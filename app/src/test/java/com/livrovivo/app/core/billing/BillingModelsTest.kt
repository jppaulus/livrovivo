package com.livrovivo.app.core.billing

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regras de assinatura que não dependem do Google Play estar por perto: o que libera o
 * Premium, o que precisa ser confirmado ao Play e o que o usuário lê quando algo falha.
 */
class BillingModelsTest {

    /** Monta uma compra igual à que o Play devolve (o construtor público recebe o JSON cru). */
    private fun purchase(
        productId: String = BillingProducts.ANNUAL,
        state: Int = Purchase.PurchaseState.PURCHASED,
        acknowledged: Boolean = true
    ): Purchase {
        val json = """
            {
              "orderId": "GPA.1234-5678-9012-34567",
              "packageName": "com.livrovivo.app",
              "productId": "$productId",
              "purchaseTime": 1726000000000,
              "purchaseState": ${if (state == Purchase.PurchaseState.PURCHASED) 0 else 4},
              "purchaseToken": "token-$productId-$state",
              "autoRenewing": true,
              "acknowledged": $acknowledged
            }
        """.trimIndent()
        return Purchase(json, "assinatura-falsa")
    }

    @Test
    fun `a paid subscription grants premium`() {
        assertTrue(listOf(purchase()).grantsPremium())
    }

    @Test
    fun `a pending purchase does not grant premium yet`() {
        val pending = purchase(state = Purchase.PurchaseState.PENDING, acknowledged = false)

        assertEquals(Purchase.PurchaseState.PENDING, pending.purchaseState)
        assertFalse(listOf(pending).grantsPremium())
    }

    @Test
    fun `no purchases means no premium`() {
        assertFalse(emptyList<Purchase>().grantsPremium())
    }

    @Test
    fun `one paid subscription is enough even alongside a pending one`() {
        val purchases = listOf(
            purchase(productId = BillingProducts.MONTHLY, state = Purchase.PurchaseState.PENDING, acknowledged = false),
            purchase(productId = BillingProducts.ANNUAL)
        )

        assertTrue(purchases.grantsPremium())
    }

    @Test
    fun `only paid and unacknowledged purchases need acknowledgement`() {
        val naoConfirmada = purchase(productId = BillingProducts.MONTHLY, acknowledged = false)
        val purchases = listOf(
            purchase(productId = BillingProducts.ANNUAL, acknowledged = true),
            naoConfirmada,
            purchase(state = Purchase.PurchaseState.PENDING, acknowledged = false)
        )

        val pendentes = purchases.needingAcknowledgement()

        assertEquals(listOf(naoConfirmada.purchaseToken), pendentes.map { it.purchaseToken })
    }

    @Test
    fun `billing periods are read in plain portuguese`() {
        assertEquals("por mês", billingPeriodLabel("P1M"))
        assertEquals("por ano", billingPeriodLabel("P1Y"))
        assertEquals("a cada 3 meses", billingPeriodLabel("P3M"))
        // Período fora do esperado não vaza o código ISO para a tela.
        assertEquals("por período", billingPeriodLabel("P17D"))
    }

    @Test
    fun `trial periods are converted to days`() {
        assertEquals(7, isoPeriodInDays("P7D"))
        assertEquals(7, isoPeriodInDays("P1W"))
        assertEquals(30, isoPeriodInDays("P1M"))
        assertEquals(365, isoPeriodInDays("P1Y"))
        assertEquals(0, isoPeriodInDays(null))
        assertEquals(0, isoPeriodInDays("lixo"))
    }

    @Test
    fun `error messages explain what to do instead of showing a code`() {
        val mensagens = listOf(
            BillingClient.BillingResponseCode.USER_CANCELED,
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED,
            BillingClient.BillingResponseCode.NETWORK_ERROR,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE,
            BillingClient.BillingResponseCode.DEVELOPER_ERROR,
            -999
        ).map { billingErrorMessage(it) }

        mensagens.forEach { mensagem ->
            assertTrue("mensagem vazia", mensagem.isNotBlank())
            assertFalse("vazou o código do erro: $mensagem", mensagem.contains(Regex("-?\\d{1,3}\\b")))
        }
        assertTrue(
            billingErrorMessage(BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED)
                .contains("assinatura ativa")
        )
    }

    @Test
    fun `a plan shows the price with its period`() {
        val anual = SubscriptionPlan(
            productId = BillingProducts.ANNUAL,
            offerToken = "token",
            title = "Plano Anual",
            formattedPrice = "R$ 199,90",
            billingPeriod = "P1Y",
            freeTrialDays = 7
        )

        assertTrue(anual.isAnnual)
        assertEquals("R$ 199,90 por ano", anual.priceWithPeriod)
    }
}
