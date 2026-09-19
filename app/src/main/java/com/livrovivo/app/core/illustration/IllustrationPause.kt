package com.livrovivo.app.core.illustration

import com.livrovivo.app.core.ai.AiException
import java.security.MessageDigest

/**
 * Ilustrações com IA pausadas depois de um erro que não se resolve de uma página para outra
 * (falta de faturamento, chave bloqueada, cota do dia). Enquanto a pausa vale, as páginas usam os
 * desenhos do próprio app sem avisar a criança; o motivo aparece só na Área dos Pais.
 *
 * A pausa acaba sozinha no prazo de [durationFor], quando a chave do Gemini muda ou quando a
 * ilustração de teste das configurações funciona.
 */
data class IllustrationPause(val reason: AiException.Kind, val since: Long) {

    companion object {
        private const val HOUR_MS = 60 * 60 * 1000L

        private val PAUSING = setOf(
            AiException.Kind.BILLING,
            AiException.Kind.QUOTA,
            AiException.Kind.PERMISSION_DENIED,
            AiException.Kind.MODEL_UNAVAILABLE,
            AiException.Kind.REGION,
            AiException.Kind.INVALID_KEY,
            AiException.Kind.ACCOUNT_BLOCKED,
            AiException.Kind.API_DISABLED,
            AiException.Kind.KEY_RESTRICTED
        )

        /** Erros que pausam as ilustrações; os demais (rede, instabilidade, filtro) valem só para a página. */
        fun pausesFor(kind: AiException.Kind): Boolean = kind in PAUSING

        /** Quanto esperar antes de tentar de novo sozinho: cota volta logo, faturamento não. */
        fun durationFor(kind: AiException.Kind): Long =
            if (kind == AiException.Kind.QUOTA) HOUR_MS else 24 * HOUR_MS

        /** Identifica a chave sem guardá-la de novo: trocar de chave libera uma nova tentativa. */
        fun fingerprint(apiKey: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(apiKey.trim().toByteArray())
                .take(8)
                .joinToString("") { "%02x".format(it) }

        fun encode(reason: AiException.Kind, keyFingerprint: String, since: Long): String =
            "${reason.name}|$keyFingerprint|$since"

        /** A pausa gravada, se ainda vale para esta chave e este momento. */
        fun decode(raw: String?, keyFingerprint: String, now: Long): IllustrationPause? {
            val parts = raw?.split("|") ?: return null
            if (parts.size != 3 || parts[1] != keyFingerprint) return null
            val reason = AiException.Kind.entries.find { it.name == parts[0] } ?: return null
            val since = parts[2].toLongOrNull() ?: return null
            // Relógio que voltou no tempo também libera: melhor tentar de novo do que pausar para sempre.
            if (now < since || now - since >= durationFor(reason)) return null
            return IllustrationPause(reason, since)
        }
    }
}
