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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.clip
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
import com.livrovivo.app.core.literacy.TrailPhrases
import com.livrovivo.app.core.theme.FairyGold
import com.livrovivo.app.core.theme.FairyPurple
import com.livrovivo.app.core.ui.BouncyCardButton
import com.livrovivo.app.core.ui.ConfettiOverlay
import com.livrovivo.app.core.ui.MagicalSparklesEffect
import com.livrovivo.app.presentation.literacy.activity.LetterFont
import com.livrovivo.app.presentation.literacy.activity.MinTouch
import kotlinx.coroutines.delay

/** Estrelas da fase, comemoração e o caminho para a próxima fase. */
@Composable
fun PhaseResultScreen(
    viewModel: LiteracyViewModel,
    phaseId: String,
    stars: Int,
    booksBefore: Int,
    onOpenBook: (storyId: String) -> Unit,
    onNextPhase: (phaseId: String) -> Unit,
    onPlayAgain: () -> Unit,
    onBackToTrail: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val next = uiState.nextPhase(phaseId)
    val message = TrailPhrases.result(stars)
    val bookNotice = TrailPhrases.BOOK_NOTICE
    val writing by viewModel.isWritingBook.collectAsState()
    // O livro aparece na lista quando fica pronto (com IA, alguns segundos depois das estrelas).
    val newBook = uiState.books.takeIf { booksBefore >= 0 && it.size > booksBefore }?.last()
    LaunchedEffect(phaseId, stars, newBook?.id) { if (newBook != null) viewModel.speak(message, bookNotice) else viewModel.speak(message) }
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
            Spacer(Modifier.height(28.dp))

            if (newBook != null) {
                NewBookCard(text = bookNotice, onOpen = { onOpenBook(newBook.id) })
                Spacer(Modifier.height(14.dp))
            } else if (booksBefore >= 0 && writing) {
                WritingBookCard()
                Spacer(Modifier.height(14.dp))
            }
            if (stars >= 1 && next != null && !next.isLocked) {
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

/** "Você ganhou um livro novo para ler sozinho!" com o botão para abrir o livro. */
@Composable
private fun NewBookCard(text: String, onOpen: () -> Unit) {
    BouncyCardButton(onClick = onOpen, containerColor = Color(0xFF0B7A57), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("📖", fontSize = 40.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text, color = Color.White, fontFamily = LetterFont, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text("Ler agora ▶", color = FairyGold, fontFamily = LetterFont, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

/** Enquanto a IA escreve o livro novo. */
@Composable
private fun WritingBookCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
        Spacer(Modifier.width(14.dp))
        Text(
            "Escrevendo um livro só para você...",
            fontFamily = LetterFont,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
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
