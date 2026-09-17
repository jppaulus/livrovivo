package com.livrovivo.app.core.ai

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Backend opcional de produção: a Edge Function "ai-gateway" do Supabase guarda as chaves de IA
 * no servidor e repassa as chamadas. Configurado via local.properties (supabase.url / supabase.anonKey).
 */
class BackendConfig(supabaseUrl: String, val anonKey: String) {
    private val baseUrl = supabaseUrl.trim().trimEnd('/')

    val isConfigured: Boolean
        get() = baseUrl.startsWith("https://") && anonKey.isNotBlank() && !baseUrl.contains("your-project")

    val gatewayUrl: String get() = "$baseUrl/functions/v1/ai-gateway"
}

class HttpResult(val code: Int, val body: ByteArray, val contentType: String?) {
    val isSuccessful: Boolean get() = code in 200..299
    fun text(): String = body.toString(Charsets.UTF_8)
}

object AiHttp {
    val JSON = "application/json; charset=utf-8".toMediaType()

    fun createClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun jsonBody(json: String) = json.toRequestBody(JSON)

    /** Executa a chamada de forma cancelável: se a corrotina for cancelada, a requisição HTTP também é. */
    suspend fun execute(client: OkHttpClient, request: Request, callTimeoutSeconds: Long): HttpResult {
        val timedClient = client.newBuilder().callTimeout(callTimeoutSeconds, TimeUnit.SECONDS).build()
        return suspendCancellableCoroutine { continuation ->
            val call = timedClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isCancelled) return
                    val kind = if (e is InterruptedIOException) AiException.Kind.TIMEOUT else AiException.Kind.NETWORK
                    continuation.resumeWithException(AiException(kind, e.message.orEmpty(), cause = e))
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            val bytes = it.body?.bytes() ?: ByteArray(0)
                            continuation.resume(HttpResult(it.code, bytes, it.header("Content-Type")))
                        } catch (e: IOException) {
                            if (!continuation.isCancelled) {
                                continuation.resumeWithException(AiException(AiException.Kind.NETWORK, e.message.orEmpty(), cause = e))
                            }
                        }
                    }
                }
            })
        }
    }
}
