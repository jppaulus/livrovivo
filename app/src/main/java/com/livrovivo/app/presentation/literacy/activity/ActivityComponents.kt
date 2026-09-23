package com.livrovivo.app.presentation.literacy.activity

import android.annotation.SuppressLint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.livrovivo.app.core.theme.FairyCoral
import com.livrovivo.app.core.theme.FairyEmerald
import com.livrovivo.app.core.theme.FairyGold
import com.livrovivo.app.core.theme.FairyPurple
import com.livrovivo.app.presentation.literacy.ActivityFeedback
import com.livrovivo.app.presentation.literacy.ActivityState

/** Tamanho mínimo de qualquer coisa que a criança toca (a especificação pede 64dp). */
val MinTouch = 64.dp

/**
 * Letra de forma sem serifa, a mesma que a criança aprende a traçar. O resto do app usa serifa nas
 * histórias, então as atividades definem a fonte explicitamente.
 */
val LetterFont = FontFamily.SansSerif

/**
 * Botões grandes de resposta. A opção errada balança; depois de 2 erros a certa brilha (dica).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OptionButtons(state: ActivityState, onChoose: (String) -> Unit, modifier: Modifier = Modifier) {
    val question = state.question
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        question.options.forEach { option ->
            val isCorrectShown = state.feedback == ActivityFeedback.CORRECT && option == question.answer
            val isHint = state.showHint && option == question.answer
            val isWrong = state.wrongOption == option
            BigTile(
                text = option,
                color = when {
                    isCorrectShown -> FairyEmerald
                    isWrong -> FairyCoral
                    else -> FairyPurple
                },
                shakeKey = if (isWrong) state.totalMistakes else null,
                glowing = isHint,
                onClick = { onChoose(option) },
                modifier = Modifier.widthIn(min = if (option.length > 2) 132.dp else 96.dp)
            )
        }
    }
}

/**
 * Espaços da resposta em cima e peças embaixo. A peça pode ser arrastada até um espaço ou só tocada
 * (vai para o próximo espaço vazio). Tocar numa peça já colocada devolve ela.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PieceBoard(
    state: ActivityState,
    onPlace: (pieceIndex: Int, slot: Int?) -> Unit,
    onRemove: (slot: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val question = state.question
    val slotBounds = remember(state.index) { mutableStateMapOf<Int, Rect>() }
    val solved = state.feedback == ActivityFeedback.CORRECT

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            state.slots.forEachIndexed { slot, pieceIndex ->
                val filled = pieceIndex != null
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { slotBounds[slot] = it.boundsInRoot() }
                        .defaultMinSize(minWidth = 88.dp, minHeight = 88.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            when {
                                solved -> FairyEmerald.copy(alpha = 0.18f)
                                filled -> FairyPurple.copy(alpha = 0.12f)
                                else -> MaterialTheme.colorScheme.surface
                            }
                        )
                        .border(
                            width = 3.dp,
                            color = if (solved) FairyEmerald else FairyPurple.copy(alpha = if (filled) 0.8f else 0.35f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .clickable(enabled = filled && !solved, role = Role.Button) { onRemove(slot) }
                        .semantics { contentDescription = if (filled) "Espaço ${slot + 1}: ${question.pieces[pieceIndex!!]}" else "Espaço ${slot + 1} vazio" },
                    contentAlignment = Alignment.Center
                ) {
                    if (pieceIndex != null) {
                        Text(
                            text = question.pieces[pieceIndex],
                            fontSize = 40.sp,
                            fontFamily = LetterFont,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (solved) FairyEmerald else FairyPurple,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.size(28.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            question.pieces.indices.forEach { pieceIndex ->
                val inTray = pieceIndex in state.trayPieces
                DraggablePiece(
                    text = question.pieces[pieceIndex],
                    visible = inTray,
                    glowing = state.showHint && state.hintPiece == pieceIndex,
                    enabled = !solved,
                    onTap = { onPlace(pieceIndex, null) },
                    onDrop = { center ->
                        val slot = slotBounds.entries.firstOrNull { it.value.inflate(24f).contains(center) }?.key
                        if (slot != null) onPlace(pieceIndex, slot)
                    }
                )
            }
        }
    }
}

@Composable
private fun DraggablePiece(
    text: String,
    visible: Boolean,
    glowing: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
    onDrop: (centerInRoot: Offset) -> Unit
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    val active = visible && enabled

    Box(
        modifier = Modifier
            .zIndex(if (dragging) 1f else 0f)
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .graphicsLayer {
                translationX = drag.x
                translationY = drag.y
                alpha = if (visible) 1f else 0.18f
                scaleX = if (dragging) 1.1f else 1f
                scaleY = if (dragging) 1.1f else 1f
            }
            .pointerInput(active) {
                if (!active) return@pointerInput
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        onDrop(bounds.center + drag)
                        drag = Offset.Zero
                        dragging = false
                    },
                    onDragCancel = {
                        drag = Offset.Zero
                        dragging = false
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        drag += amount
                    }
                )
            }
    ) {
        BigTile(
            text = text,
            color = FairyGold,
            textColor = Color(0xFF422800),
            glowing = glowing,
            enabled = active,
            onClick = onTap
        )
    }
}

/** Bloco grande e colorido com uma letra, sílaba ou palavra. */
@Composable
fun BigTile(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
    glowing: Boolean = false,
    enabled: Boolean = true,
    shakeKey: Int? = null
) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeKey) {
        // Se o "Tente de novo!" some no meio do balanço, o botão volta para o lugar.
        if (shakeKey == null) {
            shake.snapTo(0f)
            return@LaunchedEffect
        }
        listOf(14f, -12f, 9f, -6f, 3f, 0f).forEach { shake.animateTo(it, tween(55)) }
    }
    val pulse = if (glowing) {
        val transition = rememberInfiniteTransition(label = "hint")
        transition.animateFloat(1f, 1.1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "hintPulse").value
    } else {
        1f
    }
    Box(
        modifier = modifier
            .graphicsLayer { translationX = shake.value * density }
            .scale(pulse)
            .defaultMinSize(minWidth = 96.dp, minHeight = 88.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(color)
            .border(if (glowing) 4.dp else 0.dp, if (glowing) FairyGold else Color.Transparent, RoundedCornerShape(22.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = if (text.length > 2) 34.sp else 44.sp,
            fontFamily = LetterFont,
            fontWeight = FontWeight.ExtraBold,
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Figura da pergunta. Enquanto o desenho não existir no app, mostra um cartão com a palavra escrita.
 */
@SuppressLint("DiscouragedApi")
@Composable
fun LiteracyPicture(imageName: String, word: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resId = remember(imageName) { context.resources.getIdentifier(imageName, "drawable", context.packageName) }
    Box(
        modifier = modifier
            .fillMaxWidth(0.62f)
            .heightIn(max = 240.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .border(3.dp, FairyGold.copy(alpha = 0.6f), RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (resId != 0) {
            Image(
                painter = painterResource(resId),
                contentDescription = word.lowercase(),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = word,
                fontSize = 44.sp,
                fontFamily = LetterFont,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}
