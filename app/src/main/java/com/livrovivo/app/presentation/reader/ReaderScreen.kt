package com.livrovivo.app.presentation.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.livrovivo.app.core.audio.NarrationStatus
import com.livrovivo.app.core.audio.PlaybackState
import com.livrovivo.app.core.audio.VoicePersona
import com.livrovivo.app.core.bedtime.BedtimeMode
import com.livrovivo.app.core.theme.FairyEmerald
import com.livrovivo.app.core.theme.FairyGold
import com.livrovivo.app.core.theme.FairyNightSurface
import com.livrovivo.app.core.theme.FairyPurple
import com.livrovivo.app.core.ui.BouncyCardButton
import com.livrovivo.app.core.ui.CompanionAvatar
import com.livrovivo.app.core.ui.ConfettiOverlay
import com.livrovivo.app.core.ui.HighlightedStoryText
import com.livrovivo.app.core.ui.InfoPill
import com.livrovivo.app.core.ui.PageIllustration
import com.livrovivo.app.core.ui.StoryBookPageFrame
import com.livrovivo.app.core.ui.VirtueBadge
import com.livrovivo.app.domain.model.Chapter
import com.livrovivo.app.domain.model.Choice
import com.livrovivo.app.domain.model.MagicalCompanion
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onNavigateBack: () -> Unit,
    onNewStory: () -> Unit,
    onGoodnight: (childId: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var rewindTarget by remember { mutableStateOf<Int?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar esta história?") },
            text = { Text("\"${uiState.story?.title.orEmpty()}\", suas ilustrações e o progresso serão removidos deste aparelho. Não dá para desfazer.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteStory(onDeleted = onNavigateBack)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Apagar") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }

    DisposableEffect(Unit) {
        viewModel.audioPlayerController.onReaderStarted()
        onDispose { viewModel.audioPlayerController.onReaderStopped() }
    }

    rewindTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { rewindTarget = null },
            title = { Text("Escolher outro caminho?") },
            text = {
                Text("A história volta para a página $target e continua a partir da nova escolha. As páginas seguintes serão reescritas.")
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.chooseAnotherPath(target)
                    rewindTarget = null
                }) { Text("Vamos lá!") }
            },
            dismissButton = { TextButton(onClick = { rewindTarget = null }) { Text("Cancelar") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.story?.title ?: "Livro Vivo",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Fechar livro")
                    }
                },
                actions = {
                    if (uiState.story != null) {
                        InfoPill(text = "${uiState.pageIndex} de ${uiState.totalPages}")
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Mais opções")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Apagar esta história") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        confirmDelete = true
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (!uiState.isLoading && uiState.story != null) {
                NarrationBar(
                    state = uiState.narration,
                    onTogglePlay = viewModel::toggleAudio,
                    onReplay = viewModel::replayNarration,
                    onPersona = viewModel::setPersona,
                    onSpeed = viewModel::setSpeed,
                    onToggleMusic = viewModel::toggleAmbientSound
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                uiState.story == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(uiState.errorMessage ?: "História não encontrada.", style = MaterialTheme.typography.titleMedium)
                }

                else -> AnimatedContent(
                    targetState = uiState.pageIndex,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut())
                        } else {
                            (slideInHorizontally { -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
                        }
                    },
                    label = "page",
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val chapter = uiState.chapters.find { it.index == page }
                    if (chapter != null) {
                        PageContent(
                            uiState = uiState,
                            chapter = chapter,
                            onChoose = viewModel::selectChoice,
                            onRetry = viewModel::retryLastChoice,
                            onDismissError = viewModel::dismissError,
                            onPrevious = viewModel::previousPage,
                            onNext = viewModel::nextPage,
                            onChooseAnother = { rewindTarget = it },
                            onRestart = viewModel::restartStory,
                            onNewStory = onNewStory,
                            onShelf = onNavigateBack,
                            onRetryIllustration = viewModel::retryIllustration,
                            onGoodnight = { uiState.story?.childId?.let(onGoodnight) }
                        )
                    }
                }
            }

            GeneratingOverlay(
                visible = uiState.isGeneratingNextChapter,
                companion = uiState.companion,
                choice = uiState.pendingChoice
            )

            ConfettiOverlay(visible = uiState.showCelebration, modifier = Modifier.fillMaxSize())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageContent(
    uiState: ReaderUiState,
    chapter: Chapter,
    onChoose: (Choice) -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onChooseAnother: (Int) -> Unit,
    onRestart: () -> Unit,
    onNewStory: () -> Unit,
    onShelf: () -> Unit,
    onRetryIllustration: () -> Unit,
    onGoodnight: () -> Unit
) {
    val isCurrentNarration = uiState.narration.chapterKey?.startsWith("${uiState.story?.id}#${chapter.index}#") == true
    val status = uiState.illustrationStatus[chapter.index]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PageIllustration(
            scene = uiState.scene,
            imagePath = chapter.imagePath,
            isPainting = status == IllustrationStatus.PAINTING,
            companionEmoji = uiState.companion.emoji
        )

        if (uiState.illustrationNotice != null && status == IllustrationStatus.FAILED) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiState.illustrationNotice,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onRetryIllustration) { Text("Tentar de novo") }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        PageDots(total = uiState.totalPages, current = chapter.index, available = uiState.lastIndex)
        Spacer(modifier = Modifier.height(14.dp))

        StoryBookPageFrame(pageNumber = chapter.index) {
            HighlightedStoryText(
                text = chapter.content,
                highlightedSentence = if (isCurrentNarration) uiState.narration.highlightedSentence else -1,
                enabled = uiState.highlightReading,
                style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
            )
            if (chapter.newWords.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = FairyGold.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "✨ Palavras novas",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    chapter.newWords.forEach { word -> InfoPill(text = word) }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        when {
            chapter.isEnding -> EndingCard(
                uiState = uiState,
                onRestart = onRestart,
                onChooseAnother = { onChooseAnother((chapter.index - 1).coerceAtLeast(1)) },
                onNewStory = onNewStory,
                onShelf = onShelf,
                onGoodnight = onGoodnight
            )

            !uiState.isLatestPage -> PathChosenCard(
                chapter = chapter,
                onNext = onNext,
                onChooseAnother = { onChooseAnother(chapter.index) }
            )

            else -> ChoicesSection(
                childName = uiState.childName,
                choices = chapter.choices,
                enabled = !uiState.isGeneratingNextChapter,
                onChoose = onChoose
            )
        }

        if (uiState.errorMessage != null && uiState.isLatestPage && !chapter.isEnding) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Ops! A magia falhou por um instante.",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = uiState.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        if (uiState.canRetry) {
                            Button(onClick = onRetry) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Tentar de novo")
                            }
                        }
                        TextButton(onClick = onDismissError) { Text("Fechar") }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onPrevious, enabled = chapter.index > 1) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Página anterior")
            }
            if (chapter.index < uiState.lastIndex) {
                TextButton(onClick = onNext) {
                    Text("Próxima página")
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PageDots(total: Int, current: Int, available: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        (1..total).forEach { index ->
            val color = when {
                index == current -> MaterialTheme.colorScheme.primary
                index <= available -> FairyGold
                else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f)
            }
            Box(
                modifier = Modifier
                    .size(width = if (index == current) 22.dp else 10.dp, height = 10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(color)
            )
        }
    }
}

@Composable
private fun ChoicesSection(
    childName: String,
    choices: List<Choice>,
    enabled: Boolean,
    onChoose: (Choice) -> Unit
) {
    Text(
        text = "O que $childName vai fazer agora? 🔮",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    )
    val colors = listOf(FairyPurple, Color(0xFF0E9F8A), Color(0xFFE0567A))
    choices.forEachIndexed { index, choice ->
        BouncyCardButton(
            onClick = { onChoose(choice) },
            containerColor = colors[index % colors.size],
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = choice.virtue?.emoji ?: if (index == 0) "🌟" else "✨", fontSize = 22.sp)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = choice.text,
                        style = MaterialTheme.typography.labelLarge,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    choice.virtue?.let {
                        Text(
                            text = it.title,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PathChosenCard(chapter: Chapter, onNext: () -> Unit, onChooseAnother: () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = if (chapter.selectedChoiceText != null) "Caminho escolhido" else "A história continua na próxima página",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            chapter.selectedChoiceText?.let { chosen ->
                Text(
                    text = listOfNotNull(chapter.selectedChoice?.virtue?.emoji, chosen).joinToString(" "),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onNext, modifier = Modifier.weight(1f)) { Text("Continuar") }
                OutlinedButton(onClick = onChooseAnother, modifier = Modifier.weight(1f)) { Text("Outro caminho") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EndingCard(
    uiState: ReaderUiState,
    onRestart: () -> Unit,
    onChooseAnother: () -> Unit,
    onNewStory: () -> Unit,
    onShelf: () -> Unit,
    onGoodnight: () -> Unit
) {
    val virtues = uiState.story?.chosenVirtues.orEmpty()
    val mode = uiState.bedtimeMode
    val atNight = mode != BedtimeMode.OFF
    val closing = when (mode) {
        BedtimeMode.OFF -> "Que tal descobrir o que aconteceria com outra escolha?"
        BedtimeMode.OFFER -> "Já está ficando tarde… que tal dar boa-noite?"
        BedtimeMode.REQUIRED -> {
            val tonight = if (uiState.storiesTonight == 1) "uma aventura" else "${uiState.storiesTonight} aventuras"
            "Você já viveu $tonight hoje à noite. Agora ${uiState.companion.name} está com soninho…"
        }
    }
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = if (atNight) "🌙✨" else "👑✨🎉", fontSize = 40.sp)
            Text(
                text = if (mode == BedtimeMode.REQUIRED) "Hora de dormir!" else "Fim desta aventura!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Foram as escolhas de ${uiState.childName} que criaram este final. $closing",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
            )
            if (virtues.isNotEmpty()) {
                Text(
                    text = "Conquistas desta história",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    virtues.groupingBy { it }.eachCount().forEach { (virtue, count) ->
                        VirtueBadge(virtue = virtue, count = count)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            if (atNight) {
                // À noite o boa-noite é o convite principal; com o limite atingido, o único.
                Button(
                    onClick = onGoodnight,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = FairyNightSurface, contentColor = Color.White)
                ) { Text("Boa noite 🌙", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                if (mode == BedtimeMode.REQUIRED) return@Column
                Spacer(modifier = Modifier.height(10.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onRestart, modifier = Modifier.weight(1f)) { Text("Ler do começo") }
                OutlinedButton(onClick = onChooseAnother, modifier = Modifier.weight(1f)) { Text("Outro final") }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (atNight) {
                    // "Mais uma" continua possível, só sem o destaque que teria de dia.
                    OutlinedButton(onClick = onNewStory, modifier = Modifier.weight(1f)) { Text("Nova aventura") }
                    OutlinedButton(onClick = onShelf, modifier = Modifier.weight(1f)) { Text("Minha estante") }
                } else {
                    Button(
                        onClick = onNewStory,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = FairyEmerald)
                    ) { Text("Nova aventura") }
                    Button(onClick = onShelf, modifier = Modifier.weight(1f)) { Text("Minha estante") }
                }
            }
        }
    }
}

@Composable
private fun NarrationBar(
    state: PlaybackState,
    onTogglePlay: () -> Unit,
    onReplay: () -> Unit,
    onPersona: (VoicePersona) -> Unit,
    onSpeed: (Float) -> Unit,
    onToggleMusic: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            LinearProgressIndicator(
                progress = { state.playbackProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { menuOpen = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = state.activePersona.emoji, fontSize = 24.sp)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        Text(
                            text = "Quem conta a história?",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        VoicePersona.entries.forEach { persona ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("${persona.emoji} ${persona.title}", fontWeight = if (persona == state.activePersona) FontWeight.ExtraBold else FontWeight.Normal)
                                        Text(persona.description, style = MaterialTheme.typography.labelSmall)
                                    }
                                },
                                onClick = {
                                    onPersona(persona)
                                    menuOpen = false
                                }
                            )
                        }
                        HorizontalDivider()
                        Text(
                            text = "Velocidade",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        listOf(0.85f to "🐢 Calminha", 1.0f to "🙂 Normal", 1.15f to "🐇 Animada").forEach { (speed, label) ->
                            DropdownMenuItem(
                                text = { Text(label, fontWeight = if (kotlin.math.abs(state.speed - speed) < 0.01f) FontWeight.ExtraBold else FontWeight.Normal) },
                                onClick = {
                                    onSpeed(speed)
                                    menuOpen = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when (state.status) {
                            NarrationStatus.PREPARING ->
                                if (state.partIndex > 1) "Preparando a próxima parte..."
                                else "Preparando a voz de ${state.activePersona.title}..."
                            NarrationStatus.PLAYING -> "${state.activePersona.title} está contando"
                            NarrationStatus.ENDED -> "Fim da página"
                            NarrationStatus.ERROR -> "Narração indisponível"
                            else -> "Toque para ouvir"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = state.error ?: state.notice ?: listOfNotNull(
                        state.engine?.label,
                        when {
                            state.isChunked -> "Parte ${state.partIndex} de ${state.partCount}"
                            state.durationMs > 0 -> "${formatTime(state.currentPositionMs)} / ${formatTime(state.durationMs)}"
                            else -> null
                        }
                    ).joinToString(" · ")
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(onClick = onToggleMusic) {
                    Icon(
                        imageVector = if (state.isAmbientSoundEnabled) Icons.Default.MusicNote else Icons.Default.MusicOff,
                        contentDescription = if (state.isAmbientSoundEnabled) "Desligar música de ninar" else "Ligar música de ninar",
                        tint = if (state.isAmbientSoundEnabled) FairyGold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                IconButton(onClick = onReplay, enabled = state.status != NarrationStatus.PREPARING) {
                    Icon(Icons.Default.Replay, contentDescription = "Ouvir de novo")
                }
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onTogglePlay),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.status == NarrationStatus.PREPARING) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
                    } else {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pausar narração" else "Ouvir narração",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GeneratingOverlay(
    visible: Boolean,
    companion: MagicalCompanion,
    choice: String?
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        val messages = remember(companion.id) {
            listOf(
                "${companion.name} está imaginando o que acontece...",
                "Misturando poeira de estrelas...",
                "Escolhendo as palavras mais bonitas...",
                "Preparando a próxima surpresa..."
            )
        }
        var messageIndex by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(2_600)
                messageIndex = (messageIndex + 1) % messages.size
            }
        }
        Surface(color = Color.Black.copy(alpha = 0.6f), modifier = Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.padding(28.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CompanionAvatar(companion = companion, size = 88.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        AnimatedContent(targetState = messages[messageIndex], label = "message") { text ->
                            Text(
                                text = text,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (choice != null) {
                            Text(
                                text = "Você escolheu: \"$choice\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            modifier = Modifier
                                .width(180.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = FairyGold
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
