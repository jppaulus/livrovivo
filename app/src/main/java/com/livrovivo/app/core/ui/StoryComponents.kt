package com.livrovivo.app.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.livrovivo.app.core.audio.NarrationTimeline
import com.livrovivo.app.core.theme.FairyGold
import com.livrovivo.app.domain.model.SceneKind
import com.livrovivo.app.domain.model.Virtue
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Ilustração da página: cena desenhada pelo app por baixo e, por cima, a ilustração da IA
 * (com fade-in quando fica pronta).
 */
@Composable
fun PageIllustration(
    scene: SceneKind,
    imagePath: String?,
    isPainting: Boolean,
    modifier: Modifier = Modifier,
    companionEmoji: String? = null,
    statusText: String? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .shadow(8.dp, RoundedCornerShape(26.dp))
            .clip(RoundedCornerShape(26.dp))
    ) {
        ProceduralScene(
            scene = scene,
            companionEmoji = if (imagePath == null) companionEmoji else null,
            animate = imagePath == null,
            modifier = Modifier.fillMaxSize()
        )
        LocalImage(
            path = imagePath,
            contentDescription = "Ilustração da página",
            modifier = Modifier.fillMaxSize()
        )
        AnimatedVisibility(
            visible = isPainting || statusText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.55f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isPainting) {
                        CircularProgressIndicator(
                            color = FairyGold,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = statusText ?: "Pintando a ilustração...",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/** Texto da história com a frase narrada em destaque (estilo karaokê), ajudando quem está aprendendo a ler. */
@Composable
fun HighlightedStoryText(
    text: String,
    highlightedSentence: Int,
    enabled: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier
) {
    val timeline = remember(text) { NarrationTimeline.build(text) }
    val highlightColor = MaterialTheme.colorScheme.secondaryContainer
    val highlightTextColor = MaterialTheme.colorScheme.onSecondaryContainer
    val annotated: AnnotatedString = remember(text, highlightedSentence, enabled, highlightColor) {
        val range = timeline.sentences.getOrNull(highlightedSentence)
        if (!enabled || range == null) {
            AnnotatedString(text)
        } else {
            buildAnnotatedString {
                append(text.substring(0, range.first))
                withStyle(SpanStyle(background = highlightColor, color = highlightTextColor, fontWeight = FontWeight.SemiBold)) {
                    append(text.substring(range.first, range.last + 1))
                }
                append(text.substring(range.last + 1))
            }
        }
    }
    Text(text = annotated, style = style, modifier = modifier)
}

@Composable
fun VirtueBadge(virtue: Virtue, count: Int? = null, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = virtue.emoji, style = MaterialTheme.typography.headlineMedium)
            Text(
                text = virtue.badgeTitle,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            if (count != null) {
                Text(
                    text = if (count == 1) "1 escolha" else "$count escolhas",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                )
            }
        }
    }
}

/** Chuva de confete para comemorar o fim da história. */
@Composable
fun ConfettiOverlay(visible: Boolean, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(visible) {
        if (visible) {
            show = true
            delay(4_500)
            show = false
        } else {
            show = false
        }
    }
    AnimatedVisibility(visible = show, enter = fadeIn(), exit = fadeOut(tween(800)), modifier = modifier) {
        val transition = rememberInfiniteTransition(label = "confetti")
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing), RepeatMode.Restart),
            label = "fall"
        )
        val pieces = remember {
            val random = Random(42)
            List(60) {
                ConfettiPiece(
                    x = random.nextFloat(),
                    offset = random.nextFloat(),
                    speed = 0.6f + random.nextFloat() * 0.8f,
                    color = listOf(0xFFFF6584, 0xFFFFB300, 0xFF5E49E2, 0xFF10B981, 0xFF4FC3F7)[random.nextInt(5)],
                    rotation = random.nextFloat() * 360f
                )
            }
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            pieces.forEach { piece ->
                val y = ((progress * piece.speed + piece.offset) % 1f) * size.height
                val x = piece.x * size.width + kotlin.math.sin((progress + piece.offset) * 12f) * 12.dp.toPx()
                rotate(piece.rotation + progress * 720f, Offset(x, y)) {
                    drawRect(Color(piece.color), Offset(x, y), Size(7.dp.toPx(), 12.dp.toPx()))
                }
            }
        }
    }
}

private data class ConfettiPiece(val x: Float, val offset: Float, val speed: Float, val color: Long, val rotation: Float)

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun InfoPill(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primaryContainer) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
