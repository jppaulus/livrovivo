package com.livrovivo.app.presentation.paywall

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livrovivo.app.BuildConfig
import com.livrovivo.app.core.billing.BillingEvent
import com.livrovivo.app.core.billing.BillingManager
import com.livrovivo.app.core.billing.BillingStatus
import com.livrovivo.app.core.billing.SubscriptionPlan
import com.livrovivo.app.core.settings.SettingsManager
import com.livrovivo.app.domain.repository.BillingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PaywallUiState(
    val plans: List<SubscriptionPlan> = emptyList(),
    val selectedProductId: String? = null,
    val status: BillingStatus = BillingStatus.Loading,
    val isPurchasing: Boolean = false,
    val isPremium: Boolean = false,
    val message: String? = null
) {
    val selectedPlan: SubscriptionPlan?
        get() = plans.firstOrNull { it.productId == selectedProductId } ?: plans.firstOrNull()

    /** Sem plano carregado não há o que comprar, então o botão fica desligado. */
    val canPurchase: Boolean
        get() = !isPurchasing && !isPremium && selectedPlan != null && status is BillingStatus.Ready
}

class PaywallViewModel(
    private val billingManager: BillingManager,
    private val settingsManager: SettingsManager,
    billingRepository: BillingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaywallUiState())
    val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                billingManager.plans,
                billingManager.status,
                billingRepository.isPremiumFlow
            ) { plans, status, isPremium ->
                Triple(plans, status, isPremium)
            }.collect { (plans, status, isPremium) ->
                _uiState.update { state ->
                    state.copy(
                        plans = plans,
                        status = status,
                        isPremium = isPremium,
                        selectedProductId = state.selectedProductId
                            ?: plans.firstOrNull { it.isAnnual }?.productId
                            ?: plans.firstOrNull()?.productId,
                        isPurchasing = if (isPremium) false else state.isPurchasing
                    )
                }
            }
        }

        viewModelScope.launch {
            billingManager.events.collect { event ->
                _uiState.update { state ->
                    when (event) {
                        BillingEvent.PurchaseCompleted ->
                            state.copy(isPurchasing = false, message = null)

                        BillingEvent.PurchasePending -> state.copy(
                            isPurchasing = false,
                            message = "Pagamento em análise. O Premium libera assim que o Google confirmar."
                        )

                        BillingEvent.PurchaseCanceled -> state.copy(isPurchasing = false, message = null)

                        is BillingEvent.PurchaseFailed ->
                            state.copy(isPurchasing = false, message = event.message)
                    }
                }
            }
        }

        refresh()
    }

    fun refresh() {
        viewModelScope.launch { billingManager.refresh() }
    }

    fun selectPlan(productId: String) {
        _uiState.update { it.copy(selectedProductId = productId, message = null) }
    }

    fun purchase(activity: Activity) {
        val plan = _uiState.value.selectedPlan ?: return
        _uiState.update { it.copy(isPurchasing = true, message = null) }
        billingManager.launchPurchase(activity, plan)
    }

    /**
     * Atalho só do build de debug, para testar os recursos Premium sem precisar de uma
     * assinatura de verdade no Play Console.
     */
    fun unlockForDebug() {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch { settingsManager.setPremium(true) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    viewModel: PaywallViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val activity = LocalContext.current.findActivity()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Área dos Pais - Premium", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Desbloqueie Todo o Potencial do Livro Vivo",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Histórias personalizadas infinitas, narradas com vozes neurais acolhedoras para acompanhar o sono e o aprendizado.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )

            // Benefícios
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BenefitItem("Histórias ilimitadas personalizadas por IA")
                    BenefitItem("Narração neural expressiva em português (PT-BR)")
                    BenefitItem("Acesso 100% offline para viagens e hora de dormir")
                    BenefitItem("Nós de escolhas adaptativos para desenvolvimento emocional")
                    BenefitItem("Sem nenhum anúncio ou link desprotegido (100% seguro)")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Planos: título, preço e teste grátis vêm do Google Play, nunca escritos no app.
            when (val status = uiState.status) {
                BillingStatus.Loading -> LoadingPlansCard()

                is BillingStatus.Unavailable -> PlansUnavailableCard(
                    reason = status.reason,
                    onRetry = viewModel::refresh
                )

                BillingStatus.Ready -> uiState.plans.forEach { plan ->
                    SubscriptionPlanCard(
                        title = plan.title,
                        price = plan.priceWithPeriod,
                        details = planDetails(plan),
                        badge = if (plan.isAnnual) "Mais Popular ⭐" else null,
                        isSelected = plan.productId == uiState.selectedPlan?.productId,
                        onClick = { viewModel.selectPlan(plan.productId) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            if (uiState.isPremium) {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "🎉 Assinatura ativa! Aproveite o Livro Vivo Premium.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            uiState.message?.let { message ->
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (!uiState.isPremium) {
                Button(
                    onClick = { activity?.let(viewModel::purchase) },
                    enabled = uiState.canPurchase && activity != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (uiState.isPurchasing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            text = purchaseButtonLabel(uiState.selectedPlan),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Cobrança gerenciada com segurança pelo Google Play Billing.\n" +
                    "Você pode gerenciar ou cancelar a qualquer momento nas assinaturas do Google Play.",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            if (BuildConfig.DEBUG && !uiState.isPremium) {
                TextButton(onClick = viewModel::unlockForDebug) {
                    Text("Destravar Premium (só no build de debug)")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/** Linha de apoio do plano: teste grátis quando houver, senão como cancelar. */
private fun planDetails(plan: SubscriptionPlan): String = when {
    plan.freeTrialDays > 0 ->
        "${plan.freeTrialDays} dias de teste grátis • depois ${plan.priceWithPeriod}"
    else ->
        "Cancele a qualquer momento na Google Play Store"
}

private fun purchaseButtonLabel(plan: SubscriptionPlan?): String = when {
    plan == null -> "Assinar"
    plan.freeTrialDays > 0 -> "Começar teste grátis de ${plan.freeTrialDays} dias 🚀"
    else -> "Assinar por ${plan.priceWithPeriod}"
}

@Composable
private fun LoadingPlansCard() {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Text("Buscando os planos na Google Play Store...", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PlansUnavailableCard(reason: String, onRetry: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Assinaturas indisponíveis",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = reason, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(onClick = onRetry, contentPadding = PaddingValues(0.dp)) {
                Text("Tentar de novo")
            }
        }
    }
}

@Composable
private fun BenefitItem(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SubscriptionPlanCard(
    title: String,
    price: String,
    details: String,
    badge: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (badge != null) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = price, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

/** A Activity que hospeda esta tela; o Google Play precisa dela para abrir a compra. */
private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
