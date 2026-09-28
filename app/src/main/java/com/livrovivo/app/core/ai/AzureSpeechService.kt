package com.livrovivo.app.core.ai

import android.util.Log
import com.livrovivo.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Vozes da Microsoft Azure (texto em fala). Os termos da Microsoft não proíbem apps usados por crianças; os do
 * Google proíbem IA generativa nesses apps (HANDOFF §4, item 12).
 *
 * Com o servidor do app configurado, a fala vem por ele (é o caminho da loja: a chave fica no servidor). Sem ele,
 * só a versão de teste fala direto com a Azure, com a chave azure.speechKey do local.properties.
 *
 * A Azure devolve o áudio enquanto gera: o primeiro pedaço chega em 0,5 a 0,8 s (medido em 25/09/2026).
 */
class AzureSpeechService(
    private val http: OkHttpClient,
    private val server: LivroVivoServer,
    private val key: String = BuildConfig.DEV_AZURE_SPEECH_KEY,
    private val region: String = BuildConfig.DEV_AZURE_SPEECH_REGION
) {
    companion object {
        const val SAMPLE_RATE = 24_000
        private const val OUTPUT_FORMAT = "raw-24khz-16bit-mono-pcm"
        private val SSML = "application/ssml+xml".toMediaType()

        /** Pedido SSML com a voz, a velocidade (ex.: "-8%") e o texto, que é lido exatamente como vem. */
        fun ssml(voice: String, text: String, rate: String): String =
            "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\" xml:lang=\"pt-BR\">" +
                "<voice name=\"${escape(voice)}\"><prosody rate=\"${escape(rate)}\">${escape(text)}</prosody></voice></speak>"

        private fun escape(text: String): String = buildString(text.length) {
            text.forEach { c ->
                when (c) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(c)
                }
            }
        }

        private fun errorFor(code: Int, body: String): AiException {
            val kind = when {
                code == 401 -> AiException.Kind.INVALID_KEY
                code == 403 -> AiException.Kind.PERMISSION_DENIED
                code == 429 -> AiException.Kind.QUOTA
                code == 400 -> AiException.Kind.BAD_REQUEST
                code >= 500 -> AiException.Kind.SERVER
                else -> AiException.Kind.BAD_REQUEST
            }
            return AiException(kind, body.take(300).ifBlank { "HTTP $code" }, code)
        }
    }

    private val hasDevKey: Boolean get() = key.isNotBlank() && region.isNotBlank()

    val isAvailable: Boolean get() = server.isConfigured || hasDevKey

    /** A fala em pedaços de PCM 16-bit mono a 24 kHz, conforme a Azure gera. */
    fun stream(voice: String, text: String, rate: String): Flow<SpeechChunk> = when {
        server.isConfigured -> server.speech(voice, text, rate)
        hasDevKey -> direct(ssml(voice, text, rate))
        else -> flow { throw AiException(AiException.Kind.NOT_CONFIGURED) }
    }

    /** A fala inteira de uma vez (o caminho sem pedaços). */
    suspend fun synthesize(voice: String, text: String, rate: String): GeneratedSpeech {
        val all = ByteArrayOutputStream()
        stream(voice, text, rate).collect { all.write(it.pcm) }
        if (all.size() == 0) throw AiException(AiException.Kind.PARSE, "A Azure não devolveu áudio")
        return GeneratedSpeech(all.toByteArray(), SAMPLE_RATE)
    }

    private fun direct(ssml: String): Flow<SpeechChunk> {
        val request = Request.Builder()
            .url("https://$region.tts.speech.microsoft.com/cognitiveservices/v1")
            .addHeader("Ocp-Apim-Subscription-Key", key)
            .addHeader("X-Microsoft-OutputFormat", OUTPUT_FORMAT)
            .addHeader("User-Agent", "LivroVivo")
            .post(ssml.toRequestBody(SSML))
            .build()
        return PcmStream.open(http, request, SAMPLE_RATE) { code, body ->
            errorFor(code, body).also {
                if (BuildConfig.DEBUG) Log.w("LivroVivoIA", "Voz da Azure falhou: HTTP $code ${it.kind}")
            }
        }
    }
}

/** Lê uma resposta HTTP de PCM 16-bit mono em pedaços, conforme chega (voz direta da Azure ou pelo servidor). */
internal object PcmStream {
    /** Tempo máximo sem receber nada no meio da fala. */
    private const val READ_TIMEOUT_S = 20L

    fun open(
        http: OkHttpClient,
        request: Request,
        sampleRate: Int,
        errorFor: (code: Int, body: String) -> AiException
    ): Flow<SpeechChunk> = flow {
        // Pedaços de 100 ms: o som começa cedo sem gastar processamento com pedaços minúsculos.
        val chunkBytes = sampleRate * 2 / 10
        val call = http.newBuilder().readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS).build().newCall(request)
        // Sair da página cancela a corrotina: a conexão precisa fechar junto (a leitura abaixo é bloqueante).
        val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw errorFor(response.code, response.body?.string().orEmpty())
                val source = response.body?.source() ?: throw AiException(AiException.Kind.PARSE, "Resposta vazia")
                val buffer = ByteArray(chunkBytes)
                var filled = 0
                while (true) {
                    val read = source.read(buffer, filled, buffer.size - filled)
                    if (read == -1) break
                    filled += read
                    if (filled == buffer.size) {
                        emit(SpeechChunk(buffer.copyOf(), sampleRate))
                        filled = 0
                    }
                }
                val rest = filled - filled % 2 // PCM de 16 bits: nunca meia amostra
                if (rest > 0) emit(SpeechChunk(buffer.copyOf(rest), sampleRate))
            }
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            throw AiException(AiException.Kind.NETWORK, e.message.orEmpty(), cause = e)
        } finally {
            cancelHandle?.dispose()
        }
    }.flowOn(Dispatchers.IO)
}
