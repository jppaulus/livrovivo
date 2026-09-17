package com.livrovivo.app.core.ai

/**
 * Erro padronizado dos serviços de IA (texto, imagem e voz), com mensagem amigável em PT-BR
 * para exibir aos pais na tela de configurações.
 */
class AiException(
    val kind: Kind,
    val detail: String = "",
    val httpCode: Int? = null,
    cause: Throwable? = null
) : Exception(detail.ifBlank { kind.name }, cause) {

    enum class Kind {
        NOT_CONFIGURED,
        INVALID_KEY,
        QUOTA,
        BILLING,
        MODEL_UNAVAILABLE,
        REGION,
        BLOCKED,
        NETWORK,
        TIMEOUT,
        SERVER,
        BAD_REQUEST,
        PARSE
    }

    val friendlyMessage: String
        get() = when (kind) {
            Kind.NOT_CONFIGURED -> "IA não configurada. Adicione uma chave na Área dos Pais."
            Kind.INVALID_KEY -> "A chave de API parece inválida ou sem permissão."
            Kind.QUOTA -> "Limite de uso da API atingido. Tente mais tarde ou verifique o plano da conta."
            Kind.BILLING -> "Este recurso exige faturamento ativo na conta da API."
            Kind.MODEL_UNAVAILABLE -> "O modelo de IA configurado não está disponível."
            Kind.REGION -> "A API não está disponível nesta região."
            Kind.BLOCKED -> "O conteúdo foi bloqueado pelo filtro de segurança da IA."
            Kind.NETWORK -> "Sem conexão com a internet."
            Kind.TIMEOUT -> "A IA demorou demais para responder."
            Kind.SERVER -> "O serviço de IA está instável no momento."
            Kind.BAD_REQUEST, Kind.PARSE -> "A IA retornou uma resposta inesperada."
        }

    /** Mensagem amigável + detalhe técnico curto, útil no botão "Testar". */
    val diagnosticMessage: String
        get() {
            val technical = detail.take(180)
            val code = httpCode?.let { " (HTTP $it)" }.orEmpty()
            return if (technical.isBlank()) "$friendlyMessage$code" else "$friendlyMessage$code\n$technical"
        }

    /** Erros que valem a tentativa com outro modelo. */
    val shouldTryNextModel: Boolean
        get() = kind in setOf(Kind.MODEL_UNAVAILABLE, Kind.QUOTA, Kind.BILLING, Kind.SERVER, Kind.BAD_REQUEST, Kind.TIMEOUT)

    /** Prioridade para escolher qual erro reportar quando todos os modelos falham. */
    internal val reportPriority: Int
        get() = when (kind) {
            Kind.INVALID_KEY, Kind.REGION, Kind.NOT_CONFIGURED -> 10
            Kind.BILLING -> 9
            Kind.QUOTA -> 8
            Kind.BLOCKED -> 7
            Kind.BAD_REQUEST, Kind.PARSE -> 6
            Kind.NETWORK, Kind.TIMEOUT -> 5
            Kind.SERVER -> 4
            Kind.MODEL_UNAVAILABLE -> 1
        }

    companion object {
        fun classifyHttp(code: Int, message: String, status: String? = null): AiException {
            val lower = message.lowercase()
            val kind = when {
                "api key not valid" in lower || "api_key_invalid" in lower || "invalid api key" in lower ||
                    "invalid_api_key" in lower || code == 401 && "quota" !in lower -> Kind.INVALID_KEY
                "location is not supported" in lower || "region" in lower && code == 400 -> Kind.REGION
                code == 429 || status == "RESOURCE_EXHAUSTED" || "quota" in lower -> Kind.QUOTA
                "billing" in lower || "billed users" in lower || "paid plan" in lower || code == 402 -> Kind.BILLING
                code == 403 -> Kind.INVALID_KEY
                code == 404 || status == "NOT_FOUND" ||
                    ("model" in lower && ("not found" in lower || "not supported" in lower || "no longer" in lower || "deprecated" in lower)) ->
                    Kind.MODEL_UNAVAILABLE
                code == 408 || code == 504 -> Kind.TIMEOUT
                code >= 500 -> Kind.SERVER
                else -> Kind.BAD_REQUEST
            }
            return AiException(kind, message, code)
        }
    }
}
