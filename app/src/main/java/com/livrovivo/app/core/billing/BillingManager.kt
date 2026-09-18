package com.livrovivo.app.core.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.livrovivo.app.core.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Assinaturas pelo Google Play Billing.
 *
 * O Play é a fonte da verdade; o `isPremium` do DataStore é só um espelho local, para o app
 * continuar Premium sem internet. A cada consulta bem-sucedida o espelho é atualizado — quando
 * a consulta falha, o valor guardado é mantido em vez de tirar o Premium de quem pagou.
 */
class BillingManager(
    private val context: Context,
    private val settingsManager: SettingsManager
) {

    companion object {
        private const val TAG = "LivroVivoBilling"
        private const val MAX_RECONNECT_DELAY_MS = 15 * 60 * 1000L
        private const val CONNECTION_TIMEOUT_MS = 10_000L
    }

    private val scope = CoroutineScope(SupervisorJob())
    private val connectionLock = Mutex()
    private var reconnectDelayMs = 1_000L

    private val _plans = MutableStateFlow<List<SubscriptionPlan>>(emptyList())
    val plans: StateFlow<List<SubscriptionPlan>> = _plans.asStateFlow()

    private val _status = MutableStateFlow<BillingStatus>(BillingStatus.Loading)
    val status: StateFlow<BillingStatus> = _status.asStateFlow()

    private val _events = MutableSharedFlow<BillingEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<BillingEvent> = _events.asSharedFlow()

    private val purchasesListener = PurchasesUpdatedListener { result, purchases ->
        scope.launch { onPurchasesUpdated(result, purchases.orEmpty()) }
    }

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(purchasesListener)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    /** Conecta e sincroniza planos e assinaturas. Pode ser chamado quantas vezes quiser. */
    fun start() {
        scope.launch { refresh() }
    }

    /** Reconsulta planos e assinaturas ativas. Seguro chamar ao abrir a tela de assinatura. */
    suspend fun refresh() {
        if (!ensureConnected()) return
        loadPlans()
        syncPurchases()
    }

    /**
     * Abre a tela de compra do Google Play. O resultado não volta aqui: chega depois em [events],
     * porque o Play pode concluir a compra até fora do app (boleto, aprovação dos pais).
     */
    fun launchPurchase(activity: Activity, plan: SubscriptionPlan) {
        val details = productDetails[plan.productId]
        if (details == null || !client.isReady) {
            scope.launch {
                _events.emit(
                    BillingEvent.PurchaseFailed(
                        "A Google Play Store não está pronta. Tente de novo em alguns instantes."
                    )
                )
                refresh()
            }
            return
        }

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(plan.offerToken)
                        .build()
                )
            )
            .build()

        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            logResult("launchBillingFlow", result)
            scope.launch { _events.emit(BillingEvent.PurchaseFailed(billingErrorMessage(result.responseCode))) }
        }
    }

    // --- conexão ---

    private var productDetails: Map<String, ProductDetails> = emptyMap()

    private suspend fun ensureConnected(): Boolean = connectionLock.withLock {
        if (client.isReady) return true

        // Com tempo limite: se a loja não responder, o lock precisa ser liberado, senão
        // nenhuma consulta futura consegue nem tentar.
        val result = withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(billingResult: BillingResult) {
                        if (continuation.isActive) continuation.resume(billingResult)
                    }

                    // Queda de conexão depois de pronto: a próxima chamada reconecta sozinha.
                    override fun onBillingServiceDisconnected() {
                        if (continuation.isActive) {
                            continuation.resume(
                                BillingResult.newBuilder()
                                    .setResponseCode(BillingClient.BillingResponseCode.SERVICE_DISCONNECTED)
                                    .build()
                            )
                        }
                    }
                })
            }
        } ?: BillingResult.newBuilder()
            .setResponseCode(BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE)
            .build()

        if (result.responseCode == BillingClient.BillingResponseCode.OK) {
            reconnectDelayMs = 1_000L
            _status.value = BillingStatus.Ready
            return true
        }

        logResult("startConnection", result)
        _status.value = BillingStatus.Unavailable(billingErrorMessage(result.responseCode))
        scheduleReconnect()
        return false
    }

    /** Tenta reconectar com espera crescente, para não ficar martelando a loja. */
    private fun scheduleReconnect() {
        val wait = reconnectDelayMs
        reconnectDelayMs = (reconnectDelayMs * 2).coerceAtMost(MAX_RECONNECT_DELAY_MS)
        scope.launch {
            delay(wait)
            if (!client.isReady) refresh()
        }
    }

    // --- planos ---

    private suspend fun loadPlans() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                BillingProducts.ALL.map { productId ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                }
            )
            .build()

        val result = client.queryProductDetails(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            logResult("queryProductDetails", result.billingResult)
            _status.value = BillingStatus.Unavailable(billingErrorMessage(result.billingResult.responseCode))
            return
        }

        val details = result.productDetailsList.orEmpty()
        if (details.isEmpty()) {
            // Acontece quando as assinaturas ainda não foram publicadas no Play Console,
            // ou o app não está instalado por uma faixa de teste da mesma conta.
            _status.value = BillingStatus.Unavailable(
                "As assinaturas ainda não estão disponíveis nesta conta do Google."
            )
            return
        }

        productDetails = details.associateBy { it.productId }
        _plans.value = details.mapNotNull { it.toPlan() }
            // Anual primeiro: é o plano recomendado e o que tem teste grátis.
            .sortedByDescending { it.isAnnual }
        _status.value = BillingStatus.Ready
    }

    /**
     * Converte um produto do Play num plano da tela. Entre as ofertas elegíveis, prefere a que
     * tem teste grátis — o Play só devolve ofertas que este usuário pode usar.
     */
    private fun ProductDetails.toPlan(): SubscriptionPlan? {
        val offers = subscriptionOfferDetails.orEmpty()
        if (offers.isEmpty()) return null

        val offer = offers.firstOrNull { details ->
            details.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L }
        } ?: offers.first()

        val phases = offer.pricingPhases.pricingPhaseList
        val paidPhase = phases.lastOrNull { it.priceAmountMicros > 0L } ?: return null
        val trialDays = phases.filter { it.priceAmountMicros == 0L }
            .sumOf { isoPeriodInDays(it.billingPeriod) }

        return SubscriptionPlan(
            productId = productId,
            offerToken = offer.offerToken,
            title = name.ifBlank { title },
            formattedPrice = paidPhase.formattedPrice,
            billingPeriod = paidPhase.billingPeriod,
            freeTrialDays = trialDays
        )
    }

    // --- assinaturas ---

    /** Lê as assinaturas ativas no Play e atualiza o espelho local. */
    private suspend fun syncPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val result = client.queryPurchasesAsync(params)
        if (result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            // Falha de leitura não tira o Premium de quem pagou: mantém o valor guardado.
            logResult("queryPurchasesAsync", result.billingResult)
            return
        }

        val purchases = result.purchasesList
        acknowledgeIfNeeded(purchases)
        applyPremium(purchases.grantsPremium())
    }

    private suspend fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                acknowledgeIfNeeded(purchases)
                if (purchases.grantsPremium()) {
                    applyPremium(true)
                    _events.emit(BillingEvent.PurchaseCompleted)
                } else if (purchases.any { it.purchaseState == Purchase.PurchaseState.PENDING }) {
                    _events.emit(BillingEvent.PurchasePending)
                }
            }

            BillingClient.BillingResponseCode.USER_CANCELED ->
                _events.emit(BillingEvent.PurchaseCanceled)

            // Já assinou antes (outro aparelho, reinstalação): restaura em vez de dar erro,
            // mas só comemora se a loja confirmar mesmo que a assinatura está ativa.
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                syncPurchases()
                if (settingsManager.current().isPremium) {
                    _events.emit(BillingEvent.PurchaseCompleted)
                } else {
                    _events.emit(
                        BillingEvent.PurchaseFailed(billingErrorMessage(result.responseCode))
                    )
                }
            }

            else -> {
                logResult("onPurchasesUpdated", result)
                _events.emit(BillingEvent.PurchaseFailed(billingErrorMessage(result.responseCode)))
            }
        }
    }

    /** Sem isto o Google estorna a compra automaticamente em 3 dias. */
    private suspend fun acknowledgeIfNeeded(purchases: List<Purchase>) {
        purchases.needingAcknowledgement().forEach { purchase ->
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()
            val result = client.acknowledgePurchase(params)
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                logResult("acknowledgePurchase", result)
            }
        }
    }

    private suspend fun applyPremium(isPremium: Boolean) {
        if (settingsManager.current().isPremium != isPremium) {
            settingsManager.setPremium(isPremium)
        }
    }

    private fun logResult(step: String, result: BillingResult) {
        Log.w(TAG, "$step falhou: código ${result.responseCode} — ${result.debugMessage}")
    }
}
