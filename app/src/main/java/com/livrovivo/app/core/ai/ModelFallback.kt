package com.livrovivo.app.core.ai

import kotlinx.coroutines.delay

/**
 * Tenta uma lista de modelos em ordem até um responder.
 *
 * - Erro de formato (400): repete o mesmo modelo em modo "lite" (sem campos opcionais).
 * - Instabilidade (5xx): repete uma vez o mesmo modelo.
 * - Modelo desativado, sem acesso, sem cota ou exigindo faturamento: passa para o próximo.
 * - Problema da chave/conta: para na hora (outros modelos falhariam igual).
 */
object ModelFallback {

    data class Outcome<T>(
        val model: String,
        val value: T,
        /** true quando o modelo configurado (primeiro da lista) não tem acesso e outro funcionou. */
        val switchDefault: Boolean,
        val failures: List<Pair<String, AiException>>
    )

    private val SWITCH_DEFAULT_ON = setOf(
        AiException.Kind.MODEL_UNAVAILABLE,
        AiException.Kind.PERMISSION_DENIED,
        AiException.Kind.BILLING
    )

    suspend fun <T> run(
        models: List<String>,
        retryDelay: suspend () -> Unit = { delay(1_200) },
        attempt: suspend (model: String, lite: Boolean) -> T
    ): Outcome<T> {
        require(models.isNotEmpty()) { "Nenhum modelo para tentar" }
        val failures = mutableListOf<Pair<String, AiException>>()

        for (model in models) {
            var lite = false
            var serverRetries = 0
            while (true) {
                val error = try {
                    val value = attempt(model, lite)
                    val configuredFailure = failures.firstOrNull()?.takeIf { it.first == models.first() }?.second
                    return Outcome(
                        model = model,
                        value = value,
                        switchDefault = configuredFailure != null && configuredFailure.kind in SWITCH_DEFAULT_ON,
                        failures = failures.toList()
                    )
                } catch (e: AiException) {
                    e
                }

                when {
                    error.kind == AiException.Kind.BAD_REQUEST && !lite -> lite = true
                    error.kind == AiException.Kind.SERVER && serverRetries < 1 -> {
                        serverRetries++
                        retryDelay()
                    }
                    else -> {
                        failures += model to error
                        if (!error.shouldTryNextModel) throw withModelDetails(error, failures)
                        break
                    }
                }
            }
        }
        val best = failures.maxByOrNull { it.second.reportPriority }!!.second
        throw withModelDetails(best, failures)
    }

    /** Acrescenta ao erro o resultado de cada modelo tentado, para o diagnóstico nas configurações. */
    fun withModelDetails(error: AiException, failures: List<Pair<String, AiException>>): AiException {
        if (failures.isEmpty()) return error
        val summary = failures.joinToString("\n") { (model, e) ->
            val code = e.httpCode?.let { "HTTP $it" } ?: e.kind.name
            // Mensagem inteira (numa linha): o Google coloca detalhes como "limit: 0" no fim.
            val message = e.detail.replace(Regex("\\s+"), " ").take(500)
            "• $model: $code $message".trimEnd()
        }
        return error.withDetail(summary)
    }
}
