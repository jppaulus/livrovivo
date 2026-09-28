package com.livrovivo.app.core.ai

import android.content.Context
import android.util.Log
import com.livrovivo.app.BuildConfig
import com.livrovivo.app.core.ai.JsonUtils.asObjectOrNull
import com.livrovivo.app.core.ai.JsonUtils.obj
import com.livrovivo.app.core.ai.JsonUtils.string
import com.livrovivo.app.data.model.appJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * O servidor do app (função ai-gateway no Supabase, pasta supabase/ do projeto): texto pelo Claude, voz e
 * ilustrações pela Microsoft, com as chaves só no servidor. É o caminho da versão da loja; na versão de teste, é o
 * preferido sempre que supabase.url está no local.properties.
 *
 * Cada aparelho entra com uma conta anônima (sem e-mail e sem dados da criança). O servidor conta o uso dessa
 * conta e aplica as regras do plano grátis e do assinante (supabase/functions/ai-gateway/regras.ts).
 */
class LivroVivoServer(
    context: Context,
    private val http: OkHttpClient,
    private val config: BackendConfig
) {
    companion object {
        /** O Claude escreve uma página em 10 a 25 s; o servidor tenta até 2 vezes de 60 s. */
        private const val TEXT_TIMEOUT_S = 130L
        /** O FLUX leva 7 a 11 s; o servidor desiste em 45 s. */
        private const val IMAGE_TIMEOUT_S = 55L
        private const val STATE_TIMEOUT_S = 20L
        /** Região do projeto no Supabase, a mesma dos recursos da Microsoft (East US). */
        private const val SERVER_REGION = "us-east-1"

        /** Erro do servidor ({"erro":{"codigo","mensagem"}}) no formato que o resto do app já entende. */
        fun errorFor(code: Int, raw: String): AiException {
            val error = try {
                appJson.parseToJsonElement(raw).asObjectOrNull()?.obj("erro")
            } catch (_: Exception) {
                null
            }
            val codigo = error?.string("codigo")
            val kind = when (codigo) {
                "SESSAO" -> AiException.Kind.INVALID_KEY
                "LIMITE_GRATIS", "LIMITE_MES", "SO_ASSINANTE" -> AiException.Kind.BILLING
                "LIMITE_DIA", "LIMITE_HISTORIA", "TETO_DIARIO", "OCUPADO" -> AiException.Kind.QUOTA
                "NAO_CONFIGURADO" -> AiException.Kind.NOT_CONFIGURED
                "BLOQUEADO" -> AiException.Kind.BLOCKED
                "HISTORIA_DESCONHECIDA" -> AiException.Kind.PERMISSION_DENIED
                "PEDIDO_INVALIDO" -> AiException.Kind.BAD_REQUEST
                "RESPOSTA_INVALIDA" -> AiException.Kind.PARSE
                "FALHA_PROVEDOR" -> AiException.Kind.SERVER
                // Resposta que não veio da função (ex.: o Supabase recusou a sessão antes de chegar nela).
                else -> when {
                    code == 401 -> AiException.Kind.INVALID_KEY
                    code == 404 -> AiException.Kind.NOT_CONFIGURED
                    code == 429 -> AiException.Kind.QUOTA
                    code >= 500 -> AiException.Kind.SERVER
                    else -> AiException.Kind.BAD_REQUEST
                }
            }
            val detail = if (codigo != null) "$codigo: ${error.string("mensagem").orEmpty()}" else raw.take(300)
            return AiException(kind, detail, code)
        }
    }

    private val session = ServerSession(context, http, config)

    val isConfigured: Boolean get() = config.isConfigured

    /** Um objeto JSON no formato do [schema]. [kind]: "abertura", "continuacao" ou "livro". */
    suspend fun story(kind: String, storyId: String?, system: String, prompt: String, schema: JsonObject): JsonObject {
        val body = buildJsonObject {
            put("tipo", kind)
            storyId?.let { put("historiaId", it) }
            put("sistema", system)
            put("pedido", prompt)
            put("esquema", schema)
        }
        val started = System.nanoTime()
        val result = call("historia", body, TEXT_TIMEOUT_S)
        val json = try {
            appJson.parseToJsonElement(result.text()).asObjectOrNull()
        } catch (_: Exception) {
            null
        }
        if (BuildConfig.DEBUG) {
            Log.d("LivroVivoPerf", "servidor texto modelo=${json?.string("modelo")} elapsedMs=${(System.nanoTime() - started) / 1_000_000}")
        }
        return json?.obj("resultado") ?: throw AiException(AiException.Kind.PARSE, "Resposta do servidor sem história")
    }

    /** Uma ilustração 4:3 (JPEG). O servidor confere se a conta pode ilustrar esta história. */
    suspend fun image(prompt: String, storyId: String): GeneratedImage {
        val result = call("imagem", buildJsonObject {
            put("prompt", prompt)
            put("historiaId", storyId)
        }, IMAGE_TIMEOUT_S)
        if (result.body.isEmpty()) throw AiException(AiException.Kind.PARSE, "Imagem vazia")
        return GeneratedImage(result.body, "image/jpeg")
    }

    /** A fala em pedaços de PCM 16-bit mono a 24 kHz, conforme a Azure gera (o servidor repassa sem esperar). */
    fun speech(voice: String, text: String, rate: String): Flow<SpeechChunk> = flow {
        val body = buildJsonObject {
            put("texto", text)
            put("voz", voice)
            put("velocidade", rate)
        }
        val request = request("voz", body, session.accessToken())
        emitAll(PcmStream.open(http, request, AzureSpeechService.SAMPLE_RATE) { code, raw ->
            errorFor(code, raw).also {
                if (it.kind == AiException.Kind.INVALID_KEY) session.expire()
                if (BuildConfig.DEBUG) Log.w("LivroVivoIA", "Voz pelo servidor falhou: HTTP $code ${it.detail.take(120)}")
            }
        })
    }

    /** O que a conta já usou e os limites do plano (tela de testes). */
    suspend fun state(): JsonObject =
        appJson.parseToJsonElement(call("estado", buildJsonObject { }, STATE_TIMEOUT_S).text()).asObjectOrNull()
            ?: throw AiException(AiException.Kind.PARSE, "Resposta inválida do servidor")

    private suspend fun call(route: String, body: JsonObject, timeoutSeconds: Long): HttpResult {
        var result = AiHttp.execute(http, request(route, body, session.accessToken()), timeoutSeconds)
        if (result.code == 401) {
            // Sessão recusada (ex.: o relógio do aparelho adiantado): renova uma vez e tenta de novo.
            session.expire()
            result = AiHttp.execute(http, request(route, body, session.accessToken()), timeoutSeconds)
        }
        if (!result.isSuccessful) {
            val error = errorFor(result.code, result.text())
            if (BuildConfig.DEBUG) Log.w("LivroVivoIA", "Servidor ($route) falhou: HTTP ${result.code} ${error.kind} — ${error.detail.take(200)}")
            throw error
        }
        return result
    }

    private fun request(route: String, body: JsonObject, token: String): Request = Request.Builder()
        .url("${config.gatewayUrl}/$route")
        // Sem isso o Supabase roda a função em São Paulo, e cada pedido vai e volta dos EUA (banco e Microsoft ficam
        // na Virgínia): o primeiro som da voz caía de 1,1-1,6 s para 0,6-0,9 s com a região fixa (teste de 28/09/2026).
        .addHeader("x-region", SERVER_REGION)
        .addHeader("apikey", config.anonKey)
        .addHeader("Authorization", "Bearer $token")
        .post(AiHttp.jsonBody(body.toString()))
        .build()
}

/**
 * Conta anônima do aparelho no Supabase Auth: entra sozinha na primeira vez e renova a sessão (1 h) quando precisa.
 * Os códigos ficam nas preferências privadas do app; nada identifica a família.
 */
internal class ServerSession(
    context: Context,
    private val http: OkHttpClient,
    private val config: BackendConfig
) {
    private companion object {
        const val PREFS = "livro_vivo_servidor"
        const val ACCESS = "access_token"
        const val REFRESH = "refresh_token"
        const val EXPIRES_AT = "expires_at"
        /** Renova um minuto antes de vencer. */
        const val MARGIN_S = 60L
        const val TIMEOUT_S = 20L
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    suspend fun accessToken(): String = mutex.withLock {
        val token = prefs.getString(ACCESS, null)
        val now = System.currentTimeMillis() / 1000
        if (token != null && prefs.getLong(EXPIRES_AT, 0) - now > MARGIN_S) return token

        prefs.getString(REFRESH, null)?.let { refresh ->
            try {
                return save(post("${config.authUrl}/token?grant_type=refresh_token", buildJsonObject {
                    put("refresh_token", refresh)
                }))
            } catch (e: AiException) {
                // Sem internet: tenta depois. Código de renovação recusado: entra de novo, com outra conta anônima.
                if (e.kind in setOf(AiException.Kind.NETWORK, AiException.Kind.TIMEOUT, AiException.Kind.SERVER)) throw e
            }
        }
        save(post("${config.authUrl}/signup", buildJsonObject { putJsonObject("data") { } }))
    }

    /** Força a renovação no próximo pedido. */
    fun expire() {
        prefs.edit().putLong(EXPIRES_AT, 0).apply()
    }

    private suspend fun post(url: String, body: JsonObject): JsonObject {
        val request = Request.Builder()
            .url(url)
            .addHeader("apikey", config.anonKey)
            .post(AiHttp.jsonBody(body.toString()))
            .build()
        val result = AiHttp.execute(http, request, TIMEOUT_S)
        if (!result.isSuccessful) {
            if (BuildConfig.DEBUG) Log.w("LivroVivoIA", "Login no servidor falhou: HTTP ${result.code} ${result.text().take(200)}")
            throw AiException.classifyHttp(result.code, result.text().take(300))
        }
        return appJson.parseToJsonElement(result.text()).asObjectOrNull()
            ?: throw AiException(AiException.Kind.PARSE, "Resposta inválida do login")
    }

    private fun save(session: JsonObject): String {
        val access = session.string("access_token") ?: throw AiException(AiException.Kind.PARSE, "Login sem sessão")
        val expiresIn = session["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600L
        prefs.edit()
            .putString(ACCESS, access)
            .putString(REFRESH, session.string("refresh_token"))
            .putLong(EXPIRES_AT, System.currentTimeMillis() / 1000 + expiresIn)
            .apply()
        return access
    }
}
