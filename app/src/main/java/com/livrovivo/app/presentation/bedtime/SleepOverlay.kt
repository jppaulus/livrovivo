package com.livrovivo.app.presentation.bedtime

import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.livrovivo.app.core.parentalgate.ParentalGateDialog
import com.livrovivo.app.core.theme.FairyGold
import com.livrovivo.app.core.ui.CompanionAvatar
import com.livrovivo.app.core.ui.MagicalSparklesEffect
import com.livrovivo.app.core.ui.findActivity
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.MagicalCompanion

private val SleepBackground = Color(0xFF07060F)

/**
 * O app "dormindo" depois do boa-noite: cobre todas as telas, deixa a tela bem escura e
 * não reage a toques nem ao botão voltar. Às 6h ele acorda sozinho; antes disso, só um adulto
 * acorda, pelo portão parental.
 */
@Composable
fun SleepOverlay(child: ChildProfile?, onWake: () -> Unit) {
    var showGate by remember { mutableStateOf(false) }

    // O botão voltar não tira a criança do modo dormir.
    BackHandler {}

    // Brilho mínimo só nesta janela (não mexe na configuração do aparelho).
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        val window = activity?.window
        val original = window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window?.let { it.attributes = it.attributes.apply { screenBrightness = 0.02f } }
        onDispose {
            window?.let { it.attributes = it.attributes.apply { screenBrightness = original } }
        }
    }

    if (showGate) {
        ParentalGateDialog(
            onDismiss = { showGate = false },
            onSuccess = {
                showGate = false
                onWake()
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SleepBackground)
            // Engole todos os toques para nada da tela de baixo responder.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            }
    ) {
        MagicalSparklesEffect(sparkleColor = FairyGold.copy(alpha = 0.15f))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CompanionAvatar(
                companion = MagicalCompanion.findById(child?.companionId),
                size = 96.dp,
                animated = false,
                modifier = Modifier.alpha(0.7f)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Zzz…",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "O Livro Vivo está dormindo.\nAté amanhã${child?.name?.let { ", $it" }.orEmpty()}!",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.45f),
                textAlign = TextAlign.Center
            )
        }

        TextButton(
            onClick = { showGate = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Adulto", color = Color.White.copy(alpha = 0.35f))
        }
    }
}
