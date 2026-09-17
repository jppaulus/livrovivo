package com.livrovivo.app.core.ai

import com.livrovivo.app.core.ai.JsonUtils.array
import com.livrovivo.app.core.ai.JsonUtils.asObjectOrNull
import com.livrovivo.app.core.ai.JsonUtils.bool
import com.livrovivo.app.core.ai.JsonUtils.obj
import com.livrovivo.app.core.ai.JsonUtils.string
import com.livrovivo.app.core.settings.AiModelDefaults
import com.livrovivo.app.core.settings.SettingsManager
import com.livrovivo.app.data.model.appJson
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Base64

class GeneratedImage(val bytes: ByteArray, val mimeType: String)

class GeneratedSpeech(val pcm: ByteArray, val sampleRate: Int)

/**
 * Cliente da API Gemini (generateContent) para texto estruturado, ilustrações e narração.
 *
 * - Usa a chave dos pais (modo direto) ou o backend Supabase (modo produção).
 * - Se um modelo foi desativado ou está sem cota, tenta automaticamente o próximo da lista.
 */
class GeminiService(
    private val http: OkHttpClient,
    private val settings: SettingsManager,
    private val backend: BackendConfig
) {
    private companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        const val TEXT_TIMEOUT_S = 75L
        const val IMAGE_TIMEOUT_S = 150L
        const val SPEECH_TIMEOUT_S = 150L
        val BLOCK_REASONS = setOf("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "IMAGE_SAFETY", "RECITATION")
    }

    suspend fun isAvailable(): Boolean = settings.current().hasGeminiKey || backend.isConfigured

    /** Gera um objeto JSON (saída estruturada) a partir de instruções de sistema + pedido. */
    suspend fun generateJson(
        systemPrompt: String,
        userPrompt: String,
        schema: JsonObject?,
        temperature: Double = 0.9,
        modelOverride: String? = null
    ): JsonObject {
        val configured = modelOverride ?: settings.current().textModel
        val models = (listOf(configured) + AiModelDefaults.TEXT_FALLBACKS).distinct()
        return callWithFallback(models, TEXT_TIMEOUT_S) { model, lite ->
            buildJsonObject {
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") { addJsonObject { put("text", systemPrompt) } }
                }
                putJsonArray("contents") {
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("parts") { addJsonObject { put("text", userPrompt) } }
                    }
                }
                putJsonObject("generationConfig") {
                    put("temperature", temperature)
                    put("responseMimeType", "application/json")
                    if (!lite) {
                        if (schema != null) put("responseSchema", schema)
                        thinkingConfigFor(model)?.let { put("thinkingConfig", it) }
                    }
                }
                if (!lite) put("safetySettings", childSafetySettings())
            }
        }.let { response ->
            val text = extractText(response)
            JsonUtils.extractJsonObject(text)
                ?: throw AiException(AiException.Kind.PARSE, "Resposta sem JSON válido: ${text.take(120)}")
        }
    }

    /** Gera uma ilustração. [referenceJpegs] mantém personagens consistentes entre as páginas. */
    suspend fun generateImage(
        prompt: String,
        referenceJpegs: List<ByteArray> = emptyList(),
        aspectRatio: String = "4:3"
    ): GeneratedImage {
        val configured = settings.current().imageModel
        val models = (listOf(configured) + AiModelDefaults.IMAGE_FALLBACKS).distinct()
        val encoder = Base64.getEncoder()
        val references = referenceJpegs.map { encoder.encodeToString(it) }
        val response = callWithFallback(models, IMAGE_TIMEOUT_S) { _, lite ->
            buildJsonObject {
                putJsonArray("contents") {
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            addJsonObject { put("text", prompt) }
                            references.forEach { data ->
                                addJsonObject {
                                    putJsonObject("inlineData") {
                                        put("mimeType", "image/jpeg")
                                        put("data", data)
                                    }
                                }
                            }
                        }
                    }
                }
                putJsonObject("generationConfig") {
                    putJsonArray("responseModalities") {
                        add("TEXT")
                        add("IMAGE")
                    }
                    if (!lite) {
                        putJsonObject("imageConfig") { put("aspectRatio", aspectRatio) }
                    }
                }
                if (!lite) put("safetySettings", childSafetySettings())
            }
        }
        checkBlocked(response)
        val parts = firstCandidateParts(response)
        val imagePart = parts.lastOrNull { part ->
            part.bool("thought") != true && inlineData(part)?.let { mimeOf(it).startsWith("image/") } == true
        } ?: throw AiException(AiException.Kind.PARSE, "A resposta não trouxe imagem. ${extractTextSafe(response).take(120)}")
        val inline = inlineData(imagePart)!!
        val bytes = Base64.getDecoder().decode(inline.string("data").orEmpty())
        if (bytes.isEmpty()) throw AiException(AiException.Kind.PARSE, "Imagem vazia")
        return GeneratedImage(bytes, mimeOf(inline))
    }

    /** Narração com voz neural expressiva. Retorna PCM 16-bit mono. */
    suspend fun generateSpeech(prompt: String, voiceName: String): GeneratedSpeech {
        val configured = settings.current().ttsModel
        val models = (listOf(configured) + AiModelDefaults.TTS_FALLBACKS).distinct()
        val response = callWithFallback(models, SPEECH_TIMEOUT_S) { _, _ ->
            buildJsonObject {
                putJsonArray("contents") {
                    addJsonObject {
                        put("role", "user")
                        putJsonArray("parts") { addJsonObject { put("text", prompt) } }
                    }
                }
                putJsonObject("generationConfig") {
                    putJsonArray("responseModalities") { add("AUDIO") }
                    putJsonObject("speechConfig") {
                        putJsonObject("voiceConfig") {
                            putJsonObject("prebuiltVoiceConfig") { put("voiceName", voiceName) }
                        }
                    }
                }
            }
        }
        checkBlocked(response)
        val audioPart = firstCandidateParts(response).firstOrNull { part ->
            inlineData(part)?.let { mimeOf(it).startsWith("audio/") } == true
        } ?: throw AiException(AiException.Kind.PARSE, "A resposta não trouxe áudio.")
        val inline = inlineData(audioPart)!!
        val pcm = Base64.getDecoder().decode(inline.string("data").orEmpty())
        if (pcm.isEmpty()) throw AiException(AiException.Kind.PARSE, "Áudio vazio")
        val rate = Regex("rate=(\\d+)").find(mimeOf(inline))?.groupValues?.get(1)?.toIntOrNull() ?: 24_000
        return GeneratedSpeech(pcm, rate)
    }

    /** Filtros de segurança explícitos para conteúdo infantil (os modelos novos vêm com filtros desligados por padrão). */
    private fun childSafetySettings() = buildJsonArray {
        listOf(
            "HARM_CATEGORY_HARASSMENT",
            "HARM_CATEGORY_HATE_SPEECH",
            "HARM_CATEGORY_SEXUALLY_EXPLICIT",
            "HARM_CATEGORY_DANGEROUS_CONTENT"
        ).forEach { category ->
            addJsonObject {
                put("category", category)
                put("threshold", "BLOCK_MEDIUM_AND_ABOVE")
            }
        }
    }

    private fun thinkingConfigFor(model: String): JsonObject? = when {
        // Gemini 2.5: orçamento 0 desliga o raciocínio e deixa a resposta bem mais rápida.
        model.startsWith("gemini-2.5-flash") -> buildJsonObject { put("thinkingBudget", 0) }
        // Gemini 3.x: nível de raciocínio baixo para histórias (latência menor).
        Regex("^gemini-3").containsMatchIn(model) -> buildJsonObject { put("thinkingLevel", "low") }
        else -> null
    }

    private suspend fun callWithFallback(
        models: List<String>,
        timeoutSeconds: Long,
        buildBody: (model: String, lite: Boolean) -> JsonObject
    ): JsonObject {
        val s = settings.current()
        val apiKey = s.geminiApiKey
        if (apiKey.isBlank() && !backend.isConfigured) {
            throw AiException(AiException.Kind.NOT_CONFIGURED)
        }

        var bestError: AiException? = null
        for (model in models) {
            var lite = false
            var serverRetries = 0
            while (true) {
                val error = try {
                    val result = post(apiKey, model, buildBody(model, lite), timeoutSeconds)
                    if (result.isSuccessful) {
                        return appJson.parseToJsonElement(result.text()).asObjectOrNull()
                            ?: throw AiException(AiException.Kind.PARSE, "Resposta inválida")
                    }
                    parseError(result)
                } catch (e: AiException) {
                    e
                }

                if (bestError == null || error.reportPriority >= bestError.reportPriority) bestError = error

                when {
                    error.kind == AiException.Kind.BAD_REQUEST && !lite -> {
                        lite = true // tenta de novo sem campos opcionais (schema, thinking, imageConfig)
                    }
                    error.kind == AiException.Kind.SERVER && serverRetries < 1 -> {
                        serverRetries++
                        delay(1_200)
                    }
                    error.shouldTryNextModel -> break
                    else -> throw error
                }
            }
        }
        throw bestError ?: AiException(AiException.Kind.MODEL_UNAVAILABLE)
    }

    private suspend fun post(apiKey: String, model: String, body: JsonObject, timeoutSeconds: Long): HttpResult {
        val request = if (apiKey.isNotBlank()) {
            Request.Builder()
                .url("$BASE_URL/$model:generateContent")
                .addHeader("x-goog-api-key", apiKey)
                .post(AiHttp.jsonBody(body.toString()))
                .build()
        } else {
            val payload = buildJsonObject {
                put("provider", "gemini")
                put("model", model)
                put("payload", body)
            }
            Request.Builder()
                .url(backend.gatewayUrl)
                .addHeader("Authorization", "Bearer ${backend.anonKey}")
                .addHeader("apikey", backend.anonKey)
                .post(AiHttp.jsonBody(payload.toString()))
                .build()
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
        val error = json?.obj("error")
        val message = error?.string("message") ?: json?.string("message") ?: raw.take(300)
        val status = error?.string("status")
        if (status == "NOT_CONFIGURED") return AiException(AiException.Kind.NOT_CONFIGURED, message, result.code)
        val reason = error?.array("details")?.toString().orEmpty()
        return AiException.classifyHttp(result.code, "$message $reason".trim(), status)
    }

    private fun firstCandidateParts(response: JsonObject): List<JsonObject> =
        response.array("candidates")?.firstOrNull()?.asObjectOrNull()
            ?.obj("content")?.array("parts")
            ?.mapNotNull { it.asObjectOrNull() }
            .orEmpty()

    private fun inlineData(part: JsonObject): JsonObject? = part.obj("inlineData") ?: part.obj("inline_data")

    private fun mimeOf(inline: JsonObject): String =
        (inline.string("mimeType") ?: inline.string("mime_type")).orEmpty().lowercase()

    private fun checkBlocked(response: JsonObject) {
        response.obj("promptFeedback")?.string("blockReason")?.let {
            throw AiException(AiException.Kind.BLOCKED, "blockReason=$it")
        }
        val finish = response.array("candidates")?.firstOrNull()?.asObjectOrNull()?.string("finishReason")
        if (finish != null && finish in BLOCK_REASONS && firstCandidateParts(response).none { inlineData(it) != null }) {
            throw AiException(AiException.Kind.BLOCKED, "finishReason=$finish")
        }
    }

    private fun extractText(response: JsonObject): String {
        checkBlocked(response)
        val text = extractTextSafe(response)
        if (text.isBlank()) {
            val finish = response.array("candidates")?.firstOrNull()?.asObjectOrNull()?.string("finishReason")
            throw AiException(AiException.Kind.PARSE, "Resposta vazia (finishReason=$finish)")
        }
        return text
    }

    private fun extractTextSafe(response: JsonObject): String =
        firstCandidateParts(response)
            .filter { it.bool("thought") != true }
            .mapNotNull { it.string("text") }
            .joinToString("")
}
