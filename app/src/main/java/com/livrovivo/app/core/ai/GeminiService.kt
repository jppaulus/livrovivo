package com.livrovivo.app.core.ai

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.livrovivo.app.BuildConfig
import com.livrovivo.app.core.ai.JsonUtils.array
import com.livrovivo.app.core.ai.JsonUtils.asObjectOrNull
import com.livrovivo.app.core.ai.JsonUtils.bool
import com.livrovivo.app.core.ai.JsonUtils.obj
import com.livrovivo.app.core.ai.JsonUtils.string
import com.livrovivo.app.core.settings.AiModelDefaults
import com.livrovivo.app.core.settings.SettingsManager
import com.livrovivo.app.data.model.appJson
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
import java.security.MessageDigest
import java.util.Base64

class GeneratedImage(val bytes: ByteArray, val mimeType: String)

class GeneratedSpeech(val pcm: ByteArray, val sampleRate: Int)

/** Resultado do teste de chave: se ela foi aceita e quantos modelos a conta enxerga. */
data class KeyValidation(val valid: Boolean, val visibleModels: List<String>, val error: AiException?)

/**
 * Cliente da API Gemini (generateContent) para texto estruturado, ilustrações e narração.
 *
 * - Usa a chave dos pais (modo direto) ou o backend Supabase (modo produção).
 * - Se um modelo foi desativado, não está liberado para a conta ou está sem cota, tenta o próximo
 *   da lista; quando o configurado não tem acesso e outro funciona, o que funcionou vira o padrão.
 */
class GeminiService(
    private val context: Context,
    private val http: OkHttpClient,
    private val settings: SettingsManager,
    private val backend: BackendConfig
) {
    private companion object {
        const val API_ROOT = "https://generativelanguage.googleapis.com/v1beta"
        const val BASE_URL = "$API_ROOT/models"
        const val TEXT_TIMEOUT_S = 30L
        const val IMAGE_TIMEOUT_S = 150L
        const val SPEECH_TIMEOUT_S = 150L
        val BLOCK_REASONS = setOf("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "IMAGE_SAFETY", "RECITATION")
    }

    /** Último modelo que respondeu com sucesso para texto (exibido no teste de conexão). */
    @Volatile
    var lastTextModel: String? = null
        private set

    /** Identificação do app para chaves restritas a aplicativos Android no Google Cloud. */
    private val androidCertSha1: String? by lazy { signingCertificateSha1() }

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
        val (model, response) = callWithFallback(
            models = models,
            timeoutSeconds = TEXT_TIMEOUT_S,
            persistDefault = if (modelOverride == null) settings::setTextModel else null
        ) { model, lite ->
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
        }
        lastTextModel = model
        val text = extractText(response)
        return JsonUtils.extractJsonObject(text)
            ?: throw AiException(AiException.Kind.PARSE, "Resposta sem JSON válido: ${text.take(120)}")
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
        val response = try {
            callWithFallback(models, IMAGE_TIMEOUT_S, persistDefault = settings::setImageModel) { _, lite ->
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
            }.second
        } catch (e: AiException) {
            // Os modelos de imagem do Gemini não fazem parte do plano gratuito: sem faturamento o Google
            // responde "sem permissão" (403) ou cota zero (429). Explica isso em vez de culpar a chave.
            val freeTierLimit = e.kind == AiException.Kind.QUOTA && "limit: 0" in e.detail.lowercase()
            if (e.kind == AiException.Kind.PERMISSION_DENIED || freeTierLimit) {
                throw AiException(AiException.Kind.BILLING, e.detail, e.httpCode, e)
            }
            throw e
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
        val response = callWithFallback(models, SPEECH_TIMEOUT_S, persistDefault = settings::setTtsModel) { _, _ ->
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
        }.second
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

    /**
     * Confere se a chave é aceita pelo Google listando os modelos (não gasta cota de geração).
     * Distingue "chave inválida/bloqueada" de "chave válida sem acesso a um modelo".
     */
    suspend fun validateKey(): KeyValidation {
        val apiKey = settings.current().geminiApiKey
        if (apiKey.isBlank()) {
            return if (backend.isConfigured) {
                KeyValidation(valid = true, visibleModels = emptyList(), error = null)
            } else {
                KeyValidation(valid = false, visibleModels = emptyList(), error = AiException(AiException.Kind.NOT_CONFIGURED))
            }
        }
        return try {
            val request = Request.Builder()
                .url("$BASE_URL?pageSize=1000")
                .addHeader("x-goog-api-key", apiKey)
                .addAndroidIdentity()
                .get()
                .build()
            val result = AiHttp.execute(http, request, 30)
            if (!result.isSuccessful) {
                val error = parseError(result)
                if (BuildConfig.DEBUG) {
                    Log.w("LivroVivoIA", "Validação da chave falhou: HTTP ${result.code} ${error.kind} — ${error.detail.take(300)}")
                }
                KeyValidation(valid = false, visibleModels = emptyList(), error = error)
            } else {
                val models = appJson.parseToJsonElement(result.text()).asObjectOrNull()
                    ?.array("models")
                    ?.mapNotNull { it.asObjectOrNull()?.string("name")?.removePrefix("models/") }
                    .orEmpty()
                KeyValidation(valid = true, visibleModels = models, error = null)
            }
        } catch (e: AiException) {
            KeyValidation(valid = false, visibleModels = emptyList(), error = e)
        }
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
        // Gemini 2.5 Flash: orçamento 0 desliga o raciocínio e deixa a resposta bem mais rápida.
        model.startsWith("gemini-2.5-flash") -> buildJsonObject { put("thinkingBudget", 0) }
        // Gemini 3.x: nível de raciocínio baixo para histórias (latência menor).
        Regex("^gemini-3").containsMatchIn(model) -> buildJsonObject { put("thinkingLevel", "low") }
        else -> null
    }

    private suspend fun callWithFallback(
        models: List<String>,
        timeoutSeconds: Long,
        persistDefault: (suspend (String) -> Unit)? = null,
        buildBody: (model: String, lite: Boolean) -> JsonObject
    ): Pair<String, JsonObject> {
        val apiKey = settings.current().geminiApiKey
        if (apiKey.isBlank() && !backend.isConfigured) {
            throw AiException(AiException.Kind.NOT_CONFIGURED)
        }
        val started = System.nanoTime()
        val outcome = kotlinx.coroutines.withTimeoutOrNull(timeoutSeconds * 1000) {
            ModelFallback.run(models) { model, lite ->
                val result = post(apiKey, model, buildBody(model, lite), timeoutSeconds)
                if (!result.isSuccessful) {
                    val error = parseError(result)
                    if (BuildConfig.DEBUG) {
                        // Nunca registra a chave: apenas modelo, código e mensagem do Google.
                        Log.w("LivroVivoIA", "Falha em $model (lite=$lite): HTTP ${result.code} ${error.kind} — ${error.detail.take(300)}")
                    }
                    throw error
                }
                try {
                    appJson.parseToJsonElement(result.text()).asObjectOrNull()
                } catch (_: Exception) {
                    null
            } ?: throw AiException(AiException.Kind.PARSE, "Resposta inválida do modelo $model")
        }
        } ?: throw AiException(AiException.Kind.TIMEOUT, "Tempo total de geração excedido")
        if (BuildConfig.DEBUG) Log.d("LivroVivoPerf", "generation model=${outcome.model} elapsedMs=${(System.nanoTime() - started) / 1_000_000}")
        if (outcome.switchDefault) persistDefault?.invoke(outcome.model)
        return outcome.model to outcome.value
    }

    private suspend fun post(apiKey: String, model: String, body: JsonObject, timeoutSeconds: Long): HttpResult {
        val request = if (apiKey.isNotBlank()) {
            Request.Builder()
                .url("$BASE_URL/$model:generateContent")
                .addHeader("x-goog-api-key", apiKey)
                .addAndroidIdentity()
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

    /** Cabeçalhos que o Google usa para validar chaves restritas a apps Android (inofensivos nas demais). */
    private fun Request.Builder.addAndroidIdentity(): Request.Builder {
        context.packageName?.let { addHeader("X-Android-Package", it) }
        androidCertSha1?.let { addHeader("X-Android-Cert", it) }
        return this
    }

    @Suppress("DEPRECATION")
    private fun signingCertificateSha1(): String? = try {
        val pm = context.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        signatures?.firstOrNull()?.toByteArray()?.let { cert ->
            MessageDigest.getInstance("SHA-1").digest(cert).joinToString("") { "%02X".format(it) }
        }
    } catch (_: Exception) {
        null
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
        val reasons = error?.array("details")
            ?.mapNotNull { it.asObjectOrNull()?.string("reason") }
            ?.joinToString(" ")
            .orEmpty()
        val classified = AiException.classifyHttp(result.code, "$message $reasons".trim(), status)
        // Mantém no detalhe apenas a mensagem legível do Google.
        return AiException(classified.kind, message, result.code)
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
