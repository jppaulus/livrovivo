package com.livrovivo.app.presentation.literacy

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livrovivo.app.core.theme.FairyGold
import com.livrovivo.app.core.theme.FairyPurple
import com.livrovivo.app.core.ui.BouncyCardButton
import com.livrovivo.app.core.ui.ConfettiOverlay
import com.livrovivo.app.core.ui.MagicalSparklesEffect
import com.livrovivo.app.presentation.literacy.activity.MinTouch
import kotlinx.coroutines.delay

/** Estrelas da fase, comemoração e o caminho para a próxima fase. */
@Composable
fun PhaseResultScreen(
    viewModel: LiteracyViewModel,
    phaseId: String,
    stars: Int,
    onNextPhase: (phaseId: String) -> Unit,
    onPlayAgain: () -> Unit,
    onBackToTrail: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val next = uiState.nextPhase(phaseId)
    val message = when (stars) {
        3 -> "Incrível! Você ganhou 3 estrelas!"
        2 -> "Muito bem! Você ganhou 2 estrelas!"
        1 -> "Boa! Você ganhou 1 estrela!"
        else -> "Você treinou bastante! Vamos tentar de novo?"
    }
    LaunchedEffect(phaseId, stars) { viewModel.speak(message) }
    DisposableEffect(viewModel) { onDispose { viewModel.stopNarration() } }

    Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        MagicalSparklesEffect()
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            uiState.phase(phaseId)?.let {
                Text(
                    it.phase.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.height(16.dp))
            BigStars(stars = stars)
            Spacer(Modifier.height(20.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(36.dp))

            if (stars >= 1 && next != null) {
                BouncyCardButton(
                    onClick = { onNextPhase(next.phase.id) },
                    containerColor = FairyPurple,
                    modifier = Modifier.fillMaxWidth().height(72.dp)
                ) {
                    Text("Próxima fase ▶", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
                Spacer(Modifier.height(14.dp))
            }
            OutlinedButton(onClick = onPlayAgain, modifier = Modifier.fillMaxWidth().height(MinTouch)) {
                Text(if (stars >= 1) "Jogar de novo" else "Tentar de novo", fontSize = 18.sp)
            }
            TextButton(onClick = onBackToTrail, modifier = Modifier.height(MinTouch)) {
                Text("Voltar para a trilha", fontSize = 18.sp)
            }
        }
        ConfettiOverlay(visible = stars >= 1, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun BigStars(stars: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.semantics { contentDescription = "$stars de 3 estrelas" }
    ) {
        repeat(3) { index ->
            val scale = remember { Animatable(0f) }
            LaunchedEffect(Unit) {
                delay(250L * index)
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
            }
            Text(
                text = "★",
                fontSize = if (index == 1) 96.sp else 76.sp,
                color = if (index < stars) FairyGold else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                modifier = Modifier.scale(scale.value)
            )
        }
    }
}
