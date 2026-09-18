package com.livrovivo.app.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livrovivo.app.domain.model.MedalLevel
import com.livrovivo.app.domain.model.Sticker
import com.livrovivo.app.domain.model.StickerArt

private val StickerShape = RoundedCornerShape(16.dp)

/** O símbolo da figurinha, usado também apagado nos espaços vazios. */
private val StickerArt.emoji: String
    get() = when (this) {
        is StickerArt.World -> emoji
        is StickerArt.Medal -> virtue.emoji
        is StickerArt.Badge -> emoji
    }

private fun MedalLevel.ring(): Color = when (this) {
    MedalLevel.BRONZE -> Color(0xFFCD7F32)
    MedalLevel.PRATA -> Color(0xFFB8BCC6)
    MedalLevel.OURO -> Color(0xFFFFC53D)
}

/**
 * Uma figurinha. Colada, mostra o desenho; ainda não ganha, é um espaço tracejado com o número
 * — como num álbum de verdade, o espaço vazio já diz que tem mais coisa para descobrir.
 */
@Composable
fun StickerView(
    sticker: Sticker,
    collected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp
) {
    val emojiSize = (size.value * 0.34f).sp
    if (!collected) {
        val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(StickerShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .drawBehind {
                    drawRoundRect(
                        color = outline,
                        cornerRadius = CornerRadius(16.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // O ícone apagado diz o que vem ali, para quem ainda não lê; o número fica no canto.
            Text(
                text = sticker.art.emoji,
                fontSize = emojiSize,
                modifier = Modifier.alpha(0.25f)
            )
            Text(
                text = sticker.number.toString(),
                fontSize = (size.value * 0.14f).sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 4.dp)
            )
        }
        return
    }

    when (val art = sticker.art) {
        is StickerArt.World -> Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(StickerShape)
                .border(3.dp, Color.White, StickerShape)
        ) {
            ProceduralScene(scene = art.scene, modifier = Modifier.fillMaxSize(), animate = false)
            Text(
                text = art.emoji,
                fontSize = emojiSize,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
            )
        }

        is StickerArt.Medal -> Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(StickerShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize(0.8f)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color.White, art.level.ring().copy(alpha = 0.35f))))
                    .border(4.dp, art.level.ring(), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = art.virtue.emoji, fontSize = emojiSize)
            }
        }

        is StickerArt.Badge -> Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(StickerShape)
                .background(Brush.linearGradient(listOf(Color(0xFFFFF3D6), Color(0xFFFFD98A)))),
            contentAlignment = Alignment.Center
        ) {
            Text(text = art.emoji, fontSize = (size.value * 0.42f).sp)
        }
    }
}
