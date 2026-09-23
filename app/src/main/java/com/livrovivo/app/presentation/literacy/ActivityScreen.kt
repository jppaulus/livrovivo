package com.livrovivo.app.presentation.literacy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livrovivo.app.core.theme.FairyCoral
import com.livrovivo.app.core.theme.FairyEmerald
import com.livrovivo.app.core.theme.FairyGold
import com.livrovivo.app.core.theme.FairyPurple
import com.livrovivo.app.core.ui.MagicalSparklesEffect
import com.livrovivo.app.domain.model.QuestionType
import com.livrovivo.app.presentation.literacy.activity.BuildWordActivity
import com.livrovivo.app.presentation.literacy.activity.DictationActivity
import com.livrovivo.app.presentation.literacy.activity.JoinActivity
import com.livrovivo.app.presentation.literacy.activity.LetterFont
import com.livrovivo.app.presentation.literacy.activity.ListenAndTapActivity
import com.livrovivo.app.presentation.literacy.activity.MinTouch
import com.livrovivo.app.presentation.literacy.activity.PictureWordActivity

/**
 * As 5 perguntas de uma fase. Sem cobrança nem links: só a atividade, o botão de ouvir de novo e o de sair.
 */
@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel,
    onClose: () -> Unit,
    onFinished: (stars: Int, booksBefore: Int) -> Unit
) {
    val screen by viewModel.state.collectAsState()
    val session = screen.session
    DisposableEffect(viewModel) { onDispose { viewModel.stopNarration() } }
    LaunchedEffect(screen.savedStars) {
        screen.savedStars?.let { stars -> onFinished(stars, screen.booksBefore) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        MagicalSparklesEffect()
        when {
            screen.error != null -> ErrorContent(message = screen.error!!, onClose = onClose)
            session == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            else -> ActivityContent(
                session = session,
                onClose = onClose,
                onListen = viewModel::speakPrompt,
                onChoose = viewModel::chooseOption,
                onPlace = viewModel::placePiece,
                onRemove = viewModel::removeFromSlot
            )
        }
    }
}

@Composable
private fun ActivityContent(
    session: ActivityState,
    onClose: () -> Unit,
    onListen: () -> Unit,
    onChoose: (String) -> Unit,
    onPlace: (Int, Int?) -> Unit,
    onRemove: (Int) -> Unit
) {
    val question = session.question
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(MinTouch)) {
                Icon(Icons.Default.Close, contentDescription = "Sair da atividade", modifier = Modifier.size(32.dp))
            }
            QuestionDots(current = session.index, total = session.questionCount, modifier = Modifier.weight(1f))
            Spacer(Modifier.size(MinTouch))
        }

        Spacer(Modifier.height(12.dp))

        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(
                    onClick = onListen,
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = FairyPurple)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Ouvir de novo",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(Modifier.size(16.dp))
                Text(
                    // No ditado a palavra não aparece escrita: a criança só escuta.
                    text = if (question.type == QuestionType.DICTATION) "Escute e escreva a palavra" else question.prompt,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (question.type) {
                    QuestionType.LISTEN_AND_TAP -> ListenAndTapActivity(session, onChoose)
                    QuestionType.PICTURE_WORD -> PictureWordActivity(session, onChoose)
                    QuestionType.JOIN -> JoinActivity(session, onPlace, onRemove)
                    QuestionType.BUILD_WORD -> BuildWordActivity(session, onPlace, onRemove)
                    QuestionType.DICTATION -> DictationActivity(session, onPlace, onRemove)
                }
            }
        }

        FeedbackBanner(feedback = session.feedback)
    }
}

@Composable
private fun QuestionDots(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == current) 20.dp else 14.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            index < current -> FairyGold
                            index == current -> FairyPurple
                            else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f)
                        }
                    )
            )
        }
    }
}

@Composable
private fun FeedbackBanner(feedback: ActivityFeedback) {
    Box(modifier = Modifier.fillMaxWidth().height(76.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible = feedback != ActivityFeedback.NONE, enter = scaleIn() + fadeIn(), exit = fadeOut()) {
            val correct = feedback == ActivityFeedback.CORRECT
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(if (correct) FairyEmerald else FairyCoral.copy(alpha = 0.85f))
                    .padding(horizontal = 28.dp, vertical = 12.dp)
            ) {
                Text(
                    text = if (correct) "Muito bem! ⭐" else "Tente de novo!",
                    fontSize = 26.sp,
                    fontFamily = LetterFont,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(message: String, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        TextButton(onClick = onClose, modifier = Modifier.height(MinTouch)) { Text("Voltar") }
    }
}
