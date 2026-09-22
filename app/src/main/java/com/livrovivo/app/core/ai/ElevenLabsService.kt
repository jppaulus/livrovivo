package com.livrovivo.app.core.ai

import com.livrovivo.app.core.ai.JsonUtils.array
import com.livrovivo.app.core.ai.JsonUtils.asObjectOrNull
import com.livrovivo.app.core.ai.JsonUtils.bool
import com.livrovivo.app.core.ai.JsonUtils.obj
import com.livrovivo.app.core.ai.JsonUtils.string
import com.livrovivo.app.core.settings.SettingsManager
import com.livrovivo.app.data.model.appJson
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder

data class ElevenLabsVoice(
    val id: String,
    val name: String,
    val gender: String?,
    val age: String?,
    val accent: String?,
    val useCase: String?,
    val description: String?,
    val languages: List<String>
) {
    val summary: String
        get() = listOfNotNull(
            gender?.let { if (it == "female") "feminina" else if (it == "male") "masculina" else it },
            age?.replace('_', ' '),
            accent,
            useCase?.replace('_', ' ')
        ).joinToString(" · ")

    val speaksPortuguese: Boolean
        get() = languages.any { it.startsWith("pt") } || accent?.contains("brazil", true) == true ||
            accent?.contains("portug", true) == true
}

/**
 * Cliente da ElevenLabs: a narração mais expressiva para histórias (suporta PT-BR e, no eleven_v3,
 * marcações de emoção como [whispers] e [giggles]).
 */
class ElevenLabsService(
    private val http: OkHttpClient,
    private val settings: SettingsManager,
    private val backend: BackendConfig
) {
    private companion object {
        const val BASE_URL = "https://api.elevenlabs.io"
    }

    private val voicesMutex = Mutex()
    private var cachedVoices: Pair<String, List<ElevenLabsVoice>>? = null

    suspend fun isAvailable(): Boolean = settings.current().hasElevenLabsKey || backend.isConfigured

    suspend fun listVoices(forceRefresh: Boolean = false): List<ElevenLabsVoice> = voicesMutex.withLock {
        val key = settings.current().elevenLabsApiKey
        cachedVoices?.let { (cachedKey, voices) ->
            if (!forceRefresh && cachedKey == key && voices.isNotEmpty()) return@withLock voices
        }
        val voices = mutableListOf<ElevenLabsVoice>()
        var pageToken: String? = null
        var pages = 0
        do {
            val path = buildString {
                append("/v2/voices?page_size=100")
                pageToken?.let { append("&next_page_token=").append(URLEncoder.encode(it, "UTF-8")) }
            }
            val result = send(path, method = "GET", body = null, timeoutSeconds = 30)
            if (!result.isSuccessful) throw parseError(result)
            val json = appJson.parseToJsonElement(result.text()).asObjectOrNull()
                ?: throw AiException(AiException.Kind.PARSE, "Lista de vozes inválida")
            json.array("voices")?.mapNotNull { it.asObjectOrNull()?.toVoice() }?.let(voices::addAll)
            pageToken = json.string("next_page_token")?.takeIf { json.bool("has_more") == true }
            pages++
        } while (pageToken != null && pages < 5)
        cachedVoices = key to voices
        voices
    }

    /** Sintetiza a fala e devolve MP3. [previousText]/[nextText] são só contexto de entonação. */
    suspend fun synthesize(
        text: String,
        voiceId: String,
        modelId: String,
        voiceSettings: JsonObject?,
        previousText: String? = null,
        nextText: String? = null
    ): ByteArray {
        val path = "/v1/text-to-speech/${URLEncoder.encode(voiceId, "UTF-8")}?output_format=mp3_44100_128"
        var attemptSettings = voiceSettings
        repeat(2) {
            val body = buildJsonObject {
                put("text", text)
                put("model_id", modelId)
                attemptSettings?.let { put("voice_settings", it) }
                previousText?.takeIf { it.isNotBlank() }?.let { put("previous_text", it) }
                nextText?.takeIf { it.isNotBlank() }?.let { put("next_text", it) }
            }
            val result = send(path, method = "POST", body = body, timeoutSeconds = 150)
            if (result.isSuccessful && result.body.isNotEmpty()) return result.body
            val error = parseError(result)
            // Alguns modelos (ex.: eleven_v3) aceitam só certos valores de voice_settings: tenta sem eles.
            if (result.code == 422 && attemptSettings != null) {
                attemptSettings = null
            } else {
                throw error
            }
        }
        throw AiException(AiException.Kind.BAD_REQUEST, "Não foi possível sintetizar a voz")
    }

    private suspend fun send(path: String, method: String, body: JsonObject?, timeoutSeconds: Long): HttpResult {
        val key = settings.current().elevenLabsApiKey
        val request = when {
            key.isNotBlank() -> Request.Builder()
                .url(BASE_URL + path)
                .addHeader("xi-api-key", key)
                .apply {
                    if (method == "POST") post(AiHttp.jsonBody((body ?: JsonObject(emptyMap())).toString())) else get()
                }
                .build()

            backend.isConfigured -> {
                val payload = buildJsonObject {
                    put("provider", "elevenlabs")
                    put("path", path)
                    put("method", method)
                    body?.let { put("payload", it) }
                }
                Request.Builder()
                    .url(backend.gatewayUrl)
                    .addHeader("Authorization", "Bearer ${backend.anonKey}")
                    .addHeader("apikey", backend.anonKey)
                    .post(AiHttp.jsonBody(payload.toString()))
                    .build()
            }

            else -> throw AiException(AiException.Kind.NOT_CONFIGURED)
        }
        return AiHttp.execute(http, request, timeoutSeconds)
    }

    private fun parseError(result: HttpResult): AiException {
        val raw = result.text()
        val json = try {
            appJson.parseToJsonElement(raw).asObjectOrNull()
        } catch (_: Exception) {
            null
        }
        val detailObj = json?.obj("detail")
        val status = detailObj?.string("status") ?: detailObj?.string("code")
        val message = detailObj?.string("message") ?: json?.string("detail") ?: json?.string("error") ?: raw.take(300)
        val combined = listOfNotNull(status, message).joinToString(": ")
        if (result.code == 501) return AiException(AiException.Kind.NOT_CONFIGURED, combined, result.code)
        if (status == "voice_not_found" || result.code == 404) {
            return AiException(AiException.Kind.BAD_REQUEST, "Voz não encontrada na conta ElevenLabs. $combined", result.code)
        }
        if (result.code == 403) {
            return AiException(
                AiException.Kind.PERMISSION_DENIED,
                "Na ElevenLabs, habilite na chave as permissões \"Text to Speech\" e \"Voices\" (leitura). $combined",
                result.code
            )
        }
        return AiException.classifyHttp(result.code, combined, status)
    }

    private fun JsonObject.toVoice(): ElevenLabsVoice? {
        val id = string("voice_id") ?: return null
        val labels = obj("labels")
        val languages = array("verified_languages")
            ?.mapNotNull { element ->
                val obj = element.asObjectOrNull() ?: return@mapNotNull null
                listOfNotNull(obj.string("language"), obj.string("locale")).joinToString("-").lowercase()
            }
            .orEmpty()
        return ElevenLabsVoice(
            id = id,
            name = string("name") ?: id,
            gender = labels?.string("gender")?.lowercase(),
            age = labels?.string("age")?.lowercase(),
            accent = labels?.string("accent"),
            useCase = labels?.string("use_case") ?: labels?.string("usecase"),
            description = string("description") ?: labels?.string("descriptive") ?: labels?.string("description"),
            languages = languages
        )
    }
}
