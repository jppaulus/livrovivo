package com.livrovivo.app.presentation.literacy

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livrovivo.app.core.audio.NarrationStatus
import com.livrovivo.app.core.audio.PlaybackState
import com.livrovivo.app.core.literacy.ReaderWord
import com.livrovivo.app.core.theme.FairyEmerald
import com.livrovivo.app.core.theme.FairyGold
import com.livrovivo.app.core.theme.FairyPurple
import com.livrovivo.app.core.ui.BouncyCardButton
import com.livrovivo.app.core.ui.ConfettiOverlay
import com.livrovivo.app.core.ui.LocalImage
import com.livrovivo.app.core.ui.MagicalSparklesEffect
import com.livrovivo.app.domain.model.ChildGender
import com.livrovivo.app.presentation.literacy.activity.LetterFont
import com.livrovivo.app.presentation.literacy.activity.LiteracyPicture
import com.livrovivo.app.presentation.literacy.activity.MinTouch

/** O narrador está falando (ou preparando a fala) do trecho com esta chave. */
private fun PlaybackState.isSpeaking(key: String): Boolean =
    chapterKey == key && (status == NarrationStatus.PREPARING || status == NarrationStatus.PLAYING)

/** Livro "Eu leio": a criança lê sozinha e pede ajuda ao narrador só quando quer. */
@Composable
fun EasyReaderScreen(viewModel: EasyReaderViewModel, onClose: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val playback by viewModel.playback.collectAsState()
    DisposableEffect(viewModel) { onDispose { viewModel.stopNarration() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        MagicalSparklesEffect()
        // Letra de forma sem serifa na tela toda, como nas atividades (o tema usa serifa nas aventuras).
        ProvideTextStyle(LocalTextStyle.current.copy(fontFamily = LetterFont)) {
        when {
            state.error != null -> Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(state.error!!, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                TextButton(onClick = onClose, modifier = Modifier.height(MinTouch)) { Text("Voltar") }
            }
            state.story == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            state.finished -> FinishedContent(
                readAlone = state.readAlone.size,
                onReadAgain = viewModel::readAgain,
                onClose = onClose
            )
            else -> PageContent(state = state, playback = playback, viewModel = viewModel, onClose = onClose)
        }
        }
        ConfettiOverlay(visible = state.finished, modifier = Modifier.fillMaxSize())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageContent(
    state: EasyReaderState,
    playback: PlaybackState,
    viewModel: EasyReaderViewModel,
    onClose: () -> Unit
) {
    val chapter = state.chapter ?: return
    val pageSentence = if (playback.isSpeaking(viewModel.pageKey(chapter))) playback.highlightedSentence else -1
    val tapped = state.tappedWord?.let { index ->
        val word = state.words.getOrNull(index)?.word.orEmpty()
        val speaking = playback.isSpeaking(viewModel.wordKey(word)) || playback.isSpeaking(viewModel.splitKey(word))
        index.takeIf { speaking || state.tapHighlight || state.split?.wordIndex == index }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(MinTouch)) {
                Icon(Icons.Default.Close, contentDescription = "Fechar o livro", modifier = Modifier.size(30.dp))
            }
            Text(
                text = state.story?.title.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontFamily = LetterFont,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${state.pageIndex + 1}/${state.pageCount}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            PagePicture(state)
            Spacer(Modifier.height(20.dp))

            state.split?.let { split ->
                SyllableCard(split = split)
                Spacer(Modifier.height(12.dp))
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                state.words.forEachIndexed { index, word ->
                    WordChip(
                        word = word,
                        highlighted = index == tapped,
                        inNarratedSentence = word.sentence == pageSentence,
                        onTap = { viewModel.tapWord(index) },
                        onHold = { viewModel.holdWord(index) }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BouncyCardButton(
                onClick = viewModel::listenPage,
                containerColor = FairyPurple,
                modifier = Modifier.weight(1f).height(MinTouch)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.size(8.dp))
                    Text("Ouvir a página", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            }
            BouncyCardButton(
                onClick = viewModel::readAlone,
                containerColor = if (state.pageReadAlone) FairyEmerald else FairyGold,
                modifier = Modifier.weight(1f).height(MinTouch)
            ) {
                val label = if (state.story?.childSnapshot?.gender == ChildGender.GIRL) "Li sozinha!" else "Li sozinho!"
                Text(
                    text = if (state.pageReadAlone) "⭐ $label" else label,
                    color = if (state.pageReadAlone) Color.White else Color(0xFF422800),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = viewModel::previousPage,
                enabled = state.pageIndex > 0,
                modifier = Modifier.size(MinTouch),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Página anterior", tint = FairyPurple)
            }
            PageDots(current = state.pageIndex, total = state.pageCount)
            NextButton(isLast = state.isLastPage, attention = state.pageReadAlone, onClick = viewModel::nextPage)
        }
    }
}

@Composable
private fun PagePicture(state: EasyReaderState) {
    val chapter = state.chapter ?: return
    when {
        // Livro com ilustração da IA (etapa 6).
        chapter.imagePath != null -> LocalImage(
            path = chapter.imagePath,
            maxSide = 900,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(24.dp))
        )
        // Sem IA: a figura da palavra principal (ou o cartão com a palavra, enquanto a figura não existe).
        state.mainWord != null -> LiteracyPicture(imageName = state.mainWord.image, word = state.mainWord.word)
    }
}

/** Palavra tocável: toque lê a palavra; toque longo mostra e lê as sílabas. O nome da criança é especial. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WordChip(
    word: ReaderWord,
    highlighted: Boolean,
    inNarratedSentence: Boolean,
    onTap: () -> Unit,
    onHold: () -> Unit
) {
    val background = when {
        highlighted -> FairyGold
        inNarratedSentence -> MaterialTheme.colorScheme.secondaryContainer
        word.isName -> FairyPurple.copy(alpha = 0.14f)
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .combinedClickable(role = Role.Button, onClick = onTap, onLongClick = onHold)
            .semantics {
                contentDescription = buildString {
                    append(word.word)
                    if (word.isName) append(", seu nome")
                    append(". Toque para ouvir")
                    if (word.syllables != null) append(", segure para ouvir as sílabas")
                }
            }
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = word.display,
            fontSize = 38.sp,
            fontFamily = LetterFont,
            fontWeight = FontWeight.ExtraBold,
            color = if (word.isName) FairyPurple else MaterialTheme.colorScheme.onBackground
        )
    }
}

/** "BO · LA": cada sílaba acende enquanto soa ([SyllableSplit.active]), e no fim a palavra inteira. */
@Composable
private fun SyllableCard(split: SyllableSplit) {
    val active = split.active
    Row(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .semantics { contentDescription = split.syllables.joinToString(", ") },
            verticalAlignment = Alignment.CenterVertically
        ) {
            split.syllables.forEachIndexed { index, syllable ->
                if (index > 0) {
                    Text(" · ", fontSize = 34.sp, color = FairyPurple.copy(alpha = 0.5f), fontFamily = LetterFont)
                }
                val lit = active == index || active == split.syllables.size
                Text(
                    text = syllable,
                    fontSize = 40.sp,
                    fontFamily = LetterFont,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (lit) FairyPurple else FairyPurple.copy(alpha = 0.45f),
                    modifier = Modifier.scale(if (active == index) 1.15f else 1f)
                )
            }
    }
}

@Composable
private fun PageDots(current: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == current) 16.dp else 11.dp)
                    .clip(CircleShape)
                    .background(if (index <= current) FairyGold else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f))
            )
        }
    }
}

/** Avança a página; na última vira "Terminei!". Pulsa depois do "Li sozinho!" para chamar a próxima página. */
@Composable
private fun NextButton(isLast: Boolean, attention: Boolean, onClick: () -> Unit) {
    val pulse = if (attention) {
        rememberInfiniteTransition(label = "next").animateFloat(
            initialValue = 1f,
            targetValue = 1.08f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
            label = "nextPulse"
        ).value
    } else {
        1f
    }
    if (isLast) {
        BouncyCardButton(onClick = onClick, containerColor = FairyEmerald, modifier = Modifier.height(MinTouch).scale(pulse)) {
            Text(
                "Terminei! ✓",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                modifier = Modifier.padding(horizontal = 18.dp)
            )
        }
    } else {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(MinTouch).scale(pulse),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = FairyPurple)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Próxima página", tint = Color.White)
        }
    }
}

@Composable
private fun FinishedContent(readAlone: Int, onReadAgain: () -> Unit, onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("📖✨", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Você leu o livro todo!",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )
        if (readAlone > 0) {
            Text(
                text = if (readAlone == 1) "E leu 1 página sem ajuda! ⭐" else "E leu $readAlone páginas sem ajuda! ⭐",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Spacer(Modifier.height(32.dp))
        Text("Quer ler de novo?", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        BouncyCardButton(onClick = onReadAgain, containerColor = FairyPurple, modifier = Modifier.fillMaxWidth().height(72.dp)) {
            Text("Ler de novo", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
        }
        TextButton(onClick = onClose, modifier = Modifier.height(MinTouch)) {
            Text("Voltar", fontSize = 18.sp)
        }
    }
}
