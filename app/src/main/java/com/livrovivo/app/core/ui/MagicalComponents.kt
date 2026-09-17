package com.livrovivo.app.core.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livrovivo.app.core.theme.FairyGold
import com.livrovivo.app.domain.model.MagicalCompanion

/**
 * Efeito visual de estrelinhas e pó de fada cintilante em Canvas nativo Compose
 */
@Composable
fun MagicalSparklesEffect(
    modifier: Modifier = Modifier,
    sparkleColor: Color = FairyGold.copy(alpha = 0.45f)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "Sparkles")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Pulse"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val stars = listOf(
            Offset(width * 0.12f, height * 0.08f) to 6.dp.toPx(),
            Offset(width * 0.88f, height * 0.14f) to 8.dp.toPx(),
            Offset(width * 0.25f, height * 0.32f) to 5.dp.toPx(),
            Offset(width * 0.78f, height * 0.45f) to 7.dp.toPx(),
            Offset(width * 0.15f, height * 0.68f) to 6.dp.toPx(),
            Offset(width * 0.84f, height * 0.82f) to 8.dp.toPx(),
            Offset(width * 0.45f, height * 0.92f) to 5.dp.toPx()
        )

        stars.forEachIndexed { index, (pos, starSize) ->
            val factor = if (index % 2 == 0) pulse else (1.3f - pulse)
            val rad = starSize * factor
            val path = Path().apply {
                moveTo(pos.x, pos.y - rad)
                quadraticTo(pos.x, pos.y, pos.x + rad, pos.y)
                quadraticTo(pos.x, pos.y, pos.x, pos.y + rad)
                quadraticTo(pos.x, pos.y, pos.x - rad, pos.y)
                quadraticTo(pos.x, pos.y, pos.x, pos.y - rad)
                close()
            }
            drawPath(path, color = sparkleColor, style = Fill)
        }
    }
}

/**
 * Botão tátil com efeito elástico (mola), acessível para leitores de tela.
 */
@Composable
fun BouncyCardButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = Color.White,
    elevation: Int = 4,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
        label = "BouncyScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .alpha(if (enabled) 1f else 0.55f)
            .shadow(elevation.dp, shape = RoundedCornerShape(22.dp))
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(containerColor, containerColor.copy(alpha = 0.88f))
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Moldura no estilo "Página Ilustrada de Livro de Histórias"
 */
@Composable
fun StoryBookPageFrame(
    modifier: Modifier = Modifier,
    pageNumber: Int? = null,
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 2.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        FairyGold.copy(alpha = 0.45f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    )
                ),
                shape = RoundedCornerShape(26.dp)
            )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (pageNumber != null) {
                Surface(
                    shape = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                    color = FairyGold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 24.dp)
                ) {
                    Text(
                        text = "Página $pageNumber",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF422800),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 30.dp, bottom = 22.dp)) {
                content()
            }
        }
    }
}

/** Emoji do companheiro dentro de um círculo, com leve flutuação. */
@Composable
fun CompanionAvatar(
    companion: MagicalCompanion,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    animated: Boolean = true
) {
    val offset = if (animated) {
        val transition = rememberInfiniteTransition(label = "companion")
        val value by transition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "float"
        )
        value
    } else {
        0f
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primaryContainer)
                )
            )
            .border(2.dp, FairyGold, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = companion.emoji,
            fontSize = (size.value * 0.52f).sp,
            modifier = Modifier.graphicsLayer { translationY = offset * 3.dp.toPx() }
        )
    }
}

/**
 * Banner de saudação do Companheiro Mágico
 */
@Composable
fun CompanionGreetingCard(
    companion: MagicalCompanion,
    childName: String,
    speechText: String,
    onClick: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompanionAvatar(companion = companion, size = 64.dp)

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Oi, $childName!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${companion.name}: \"$speechText\"",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
