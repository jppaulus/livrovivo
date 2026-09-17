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
        /** Chave inexistente, digitada errado, expirada ou revogada. */
        INVALID_KEY,
        /** Chave válida, mas sem acesso a este modelo/recurso (ex.: modelo fora do plano gratuito). */
        PERMISSION_DENIED,
        /** O Google bloqueou o projeto/conta (verificação de idade/telefone, chave vazada, suspensão). */
        ACCOUNT_BLOCKED,
        /** A API do Gemini está desativada no projeto da chave. */
        API_DISABLED,
        /** A chave tem restrição (apps Android, sites ou APIs) que bloqueia o app. */
        KEY_RESTRICTED,
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
            Kind.INVALID_KEY -> "A chave de API é inválida ou foi revogada. Confira se copiou a chave inteira."
            Kind.PERMISSION_DENIED -> "Esta chave não tem acesso a este modelo de IA (alguns modelos não estão no plano gratuito)."
            Kind.ACCOUNT_BLOCKED -> if (detail.contains("leaked", ignoreCase = true)) {
                "O Google bloqueou esta chave porque ela foi exposta. Crie uma nova chave no AI Studio."
            } else {
                "O Google bloqueou o acesso desta conta à API. Verifique a conta Google (idade, telefone e verificação em duas etapas) e crie uma nova chave."
            }
            Kind.API_DISABLED -> "A API do Gemini está desativada no projeto desta chave. Crie a chave pelo Google AI Studio."
            Kind.KEY_RESTRICTED -> "A chave tem restrições que bloqueiam o app. No Google Cloud, deixe a chave sem restrição de aplicativo ou libere o pacote com.livrovivo.app."
            Kind.QUOTA -> "Limite de uso da API atingido. Tente mais tarde ou verifique o plano da conta."
            Kind.BILLING -> "Este recurso exige faturamento ativo na conta do Google AI Studio (não faz parte do plano gratuito)."
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
            val technical = detail.take(400)
            val code = httpCode?.let { " (HTTP $it)" }.orEmpty()
            return if (technical.isBlank()) "$friendlyMessage$code" else "$friendlyMessage$code\n$technical"
        }

    /** Erros que valem a tentativa com outro modelo. */
    val shouldTryNextModel: Boolean
        get() = kind in setOf(
            Kind.MODEL_UNAVAILABLE,
            Kind.PERMISSION_DENIED,
            Kind.QUOTA,
            Kind.BILLING,
            Kind.SERVER,
            Kind.BAD_REQUEST,
            Kind.TIMEOUT
        )

    /** Prioridade para escolher qual erro reportar quando todos os modelos falham. */
    internal val reportPriority: Int
        get() = when (kind) {
            Kind.INVALID_KEY, Kind.ACCOUNT_BLOCKED, Kind.API_DISABLED, Kind.KEY_RESTRICTED,
            Kind.REGION, Kind.NOT_CONFIGURED -> 10
            Kind.BILLING -> 9
            Kind.PERMISSION_DENIED -> 8
            Kind.QUOTA -> 7
            Kind.BLOCKED -> 6
            Kind.BAD_REQUEST, Kind.PARSE -> 5
            Kind.NETWORK, Kind.TIMEOUT -> 4
            Kind.SERVER -> 3
            Kind.MODEL_UNAVAILABLE -> 1
        }

    /** Copia o erro acrescentando contexto (ex.: qual modelo falhou). */
    fun withDetail(extra: String): AiException = AiException(kind, extra, httpCode, cause)

    companion object {
        fun classifyHttp(code: Int, message: String, status: String? = null): AiException {
            val lower = message.lowercase()
            val kind = when {
                "api key not valid" in lower || "api_key_invalid" in lower || "invalid api key" in lower ||
                    "invalid_api_key" in lower || "api key expired" in lower ||
                    code == 401 && "quota" !in lower -> Kind.INVALID_KEY
                "reported as leaked" in lower || "denied access" in lower || "consumer_suspended" in lower ||
                    "has been suspended" in lower -> Kind.ACCOUNT_BLOCKED
                "service_disabled" in lower || "has not been used in project" in lower ||
                    "it is disabled" in lower -> Kind.API_DISABLED
                "android client application" in lower || "api_key_android_app_blocked" in lower ||
                    "referer" in lower && "blocked" in lower || "api_key_service_blocked" in lower ||
                    "are blocked" in lower && code == 403 -> Kind.KEY_RESTRICTED
                "location is not supported" in lower || "region" in lower && code == 400 -> Kind.REGION
                code == 429 || status == "RESOURCE_EXHAUSTED" || "quota" in lower -> Kind.QUOTA
                "billing" in lower || "billed users" in lower || "paid plan" in lower || code == 402 -> Kind.BILLING
                code == 403 || status == "PERMISSION_DENIED" -> Kind.PERMISSION_DENIED
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
