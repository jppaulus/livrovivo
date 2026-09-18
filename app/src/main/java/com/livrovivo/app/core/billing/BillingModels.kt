package com.livrovivo.app.core.billing

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase

/** Identificadores das assinaturas cadastradas no Google Play Console. */
object BillingProducts {
    const val MONTHLY = "livro_vivo_monthly"
    const val ANNUAL = "livro_vivo_annual"

    val ALL = listOf(ANNUAL, MONTHLY)
}

/**
 * Um plano como o Google Play devolve: preço já formatado na moeda do usuário.
 * Os preços nunca são escritos no app — o Play é quem manda, inclusive em promoções
 * e em outros países.
 */
data class SubscriptionPlan(
    val productId: String,
    /** Token da oferta escolhida; obrigatório para abrir a compra. */
    val offerToken: String,
    val title: String,
    /** Ex.: "R$ 29,90". */
    val formattedPrice: String,
    /** Período de cobrança em ISO-8601, ex.: "P1M", "P1Y". */
    val billingPeriod: String,
    /** Dias de teste grátis da oferta, quando houver. */
    val freeTrialDays: Int = 0
) {
    val isAnnual: Boolean get() = billingPeriod.equals("P1Y", ignoreCase = true)
    val periodLabel: String get() = billingPeriodLabel(billingPeriod)
    val priceWithPeriod: String get() = "$formattedPrice $periodLabel"
}

/** Em que pé está a conexão com a loja. */
sealed interface BillingStatus {
    data object Loading : BillingStatus
    data object Ready : BillingStatus

    /** Não dá para vender agora; [reason] é uma frase pronta para o usuário ler. */
    data class Unavailable(val reason: String) : BillingStatus
}

/** O que aconteceu depois que o usuário mexeu na tela de compra do Play. */
sealed interface BillingEvent {
    data object PurchaseCompleted : BillingEvent

    /** Pagamento em análise (boleto, aprovação dos pais). Ainda não libera o Premium. */
    data object PurchasePending : BillingEvent
    data object PurchaseCanceled : BillingEvent
    data class PurchaseFailed(val message: String) : BillingEvent
}

/**
 * Uma assinatura só vale quando o pagamento foi concluído. Compras pendentes
 * (aguardando aprovação) não liberam o Premium.
 */
fun List<Purchase>.grantsPremium(): Boolean =
    any { it.purchaseState == Purchase.PurchaseState.PURCHASED }

/**
 * Compras pagas que ainda não foram confirmadas ao Play. Sem a confirmação em até
 * 3 dias, o Google estorna o usuário automaticamente.
 */
fun List<Purchase>.needingAcknowledgement(): List<Purchase> =
    filter { it.purchaseState == Purchase.PurchaseState.PURCHASED && !it.isAcknowledged }

/** "P1M" → "por mês". Períodos incomuns caem num texto genérico em vez de vazar o código ISO. */
fun billingPeriodLabel(isoPeriod: String): String = when (isoPeriod.uppercase()) {
    "P1W" -> "por semana"
    "P1M" -> "por mês"
    "P2M" -> "a cada 2 meses"
    "P3M" -> "a cada 3 meses"
    "P6M" -> "a cada 6 meses"
    "P1Y" -> "por ano"
    else -> "por período"
}

/** "P7D" → 7, "P1W" → 7, "P1M" → 30. Devolve 0 quando não é um período reconhecido. */
fun isoPeriodInDays(isoPeriod: String?): Int {
    val match = Regex("^P(\\d+)([DWMY])$").find(isoPeriod?.uppercase().orEmpty()) ?: return 0
    val amount = match.groupValues[1].toIntOrNull() ?: return 0
    return when (match.groupValues[2]) {
        "D" -> amount
        "W" -> amount * 7
        "M" -> amount * 30
        "Y" -> amount * 365
        else -> 0
    }
}

/**
 * Mensagem em PT-BR para cada resposta do Play. O texto é para os pais lerem, então
 * explica o que fazer em vez de mostrar o código do erro.
 */
fun billingErrorMessage(responseCode: Int): String = when (responseCode) {
    BillingClient.BillingResponseCode.USER_CANCELED ->
        "Compra cancelada."
    BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED ->
        "Você já tem uma assinatura ativa. Ela será restaurada automaticamente."
    BillingClient.BillingResponseCode.ITEM_NOT_OWNED ->
        "Não encontramos essa assinatura nesta conta do Google."
    BillingClient.BillingResponseCode.ITEM_UNAVAILABLE ->
        "Esta assinatura não está disponível na sua conta ou no seu país."
    BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
    BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE ->
        "A Google Play Store não respondeu. Verifique a internet e tente de novo."
    BillingClient.BillingResponseCode.NETWORK_ERROR ->
        "Sem conexão com a internet para falar com a Google Play Store."
    BillingClient.BillingResponseCode.BILLING_UNAVAILABLE ->
        "Este aparelho não tem uma conta do Google compatível com compras. " +
            "Entre na Play Store e tente de novo."
    BillingClient.BillingResponseCode.FEATURE_NOT_SUPPORTED ->
        "Esta versão da Google Play Store não aceita assinaturas."
    BillingClient.BillingResponseCode.DEVELOPER_ERROR ->
        "As assinaturas ainda não estão liberadas para este app. Tente mais tarde."
    else ->
        "Não foi possível concluir a compra. Tente de novo em alguns instantes."
}
