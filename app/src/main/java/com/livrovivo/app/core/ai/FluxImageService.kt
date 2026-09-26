package com.livrovivo.app.core.ai

import android.util.Log
import com.livrovivo.app.BuildConfig
import com.livrovivo.app.core.ai.JsonUtils.array
import com.livrovivo.app.core.ai.JsonUtils.asObjectOrNull
import com.livrovivo.app.core.ai.JsonUtils.obj
import com.livrovivo.app.core.ai.JsonUtils.string
import com.livrovivo.app.data.model.appJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Base64

/**
 * Ilustrações com o FLUX.2 pro (Black Forest Labs), pela Microsoft Foundry. Os termos permitem app infantil, desde que o
 * app tenha termos de uso e avise que as imagens são feitas por IA (HANDOFF §4, item 12). Vai só o texto: mandar a
 * página anterior como referência ainda é prévia, e com ela o desenho copiava a cena anterior.
 *
 * Por enquanto só a versão de teste tem a chave (azure.foundryKey no local.properties); a da loja vai pelo servidor.
 * Medido em 26/09/2026: 7 a 11 s e US$ 0,03 por imagem de 1024 x 768.
 */
class FluxImageService(
    private val http: OkHttpClient,
    private val key: String = BuildConfig.DEV_AZURE_FOUNDRY_KEY,
    endpoint: String = BuildConfig.DEV_AZURE_FOUNDRY_ENDPOINT
) {
    companion object {
        /** Nome da publicação do modelo no recurso livro-vivo-ia (a API pede o nome da publicação, não o do modelo). */
        const val DEPLOYMENT = "flux-2-pro"
        private const val TIMEOUT_S = 90L
        /** Filtro de conteúdo do FLUX: 0 é o mais rígido e 6 o mais solto. App infantil: quase no máximo. */
        private const val SAFETY_TOLERANCE = 1

        fun requestBody(prompt: String, width: Int, height: Int): JsonObject = buildJsonObject {
            put("model", DEPLOYMENT)
            put("prompt", prompt)
            put("width", width)
            put("height", height)
            put("output_format", "jpeg")
            put("safety_tolerance", SAFETY_TOLERANCE)
        }

        /** A imagem vem em base64 em data[0].b64_json. */
        fun parseImage(raw: String): GeneratedImage {
            val json = try {
                appJson.parseToJsonElement(raw).asObjectOrNull()
            } catch (_: Exception) {
                null
            } ?: throw AiException(AiException.Kind.PARSE, "Resposta inválida do FLUX")
            val encoded = json.array("data")?.firstOrNull()?.asObjectOrNull()?.string("b64_json")
                ?: throw AiException(AiException.Kind.PARSE, "A resposta não trouxe imagem")
            val bytes = Base64.getDecoder().decode(encoded)
            if (bytes.isEmpty()) throw AiException(AiException.Kind.PARSE, "Imagem vazia")
            return GeneratedImage(bytes, "image/jpeg")
        }

        fun errorFor(code: Int, raw: String): AiException {
            val message = try {
                appJson.parseToJsonElement(raw).asObjectOrNull()?.let { it.obj("error")?.string("message") ?: it.string("detail") }
            } catch (_: Exception) {
                null
            } ?: raw.take(300)
            val kind = when {
                // "Request Moderated" (FLUX) ou "Content violated RAI policy" (filtro da Microsoft).
                "moderat" in message.lowercase() || "rai policy" in message.lowercase() -> AiException.Kind.BLOCKED
                code == 401 -> AiException.Kind.INVALID_KEY
                code == 403 -> AiException.Kind.PERMISSION_DENIED
                code == 404 -> AiException.Kind.MODEL_UNAVAILABLE
                code == 429 -> AiException.Kind.QUOTA
                code >= 500 -> AiException.Kind.SERVER
                else -> AiException.Kind.BAD_REQUEST
            }
            return AiException(kind, message, code)
        }
    }

    private val url = "${endpoint.trim().trimEnd('/')}/providers/blackforestlabs/v1/$DEPLOYMENT?api-version=preview"

    val isAvailable: Boolean get() = key.isNotBlank() && url.startsWith("https://")

    /** Uma ilustração 4:3 a partir do texto. */
    suspend fun generate(prompt: String, width: Int = 1024, height: Int = 768): GeneratedImage {
        if (!isAvailable) throw AiException(AiException.Kind.NOT_CONFIGURED)
        val request = Request.Builder()
            .url(url)
            .addHeader("api-key", key)
            .post(AiHttp.jsonBody(requestBody(prompt, width, height).toString()))
            .build()
        val result = AiHttp.execute(http, request, TIMEOUT_S)
        if (!result.isSuccessful) {
            val error = errorFor(result.code, result.text())
            if (BuildConfig.DEBUG) {
                Log.w("LivroVivoIA", "FLUX falhou: HTTP ${result.code} ${error.kind} — ${error.detail.take(200)}")
                if (error.kind == AiException.Kind.BLOCKED) Log.d("LivroVivoIA", "Pedido bloqueado:\n$prompt")
            }
            throw error
        }
        return parseImage(result.text())
    }
}
