package com.livrovivo.app.presentation.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livrovivo.app.core.literacy.LiteracyRules
import com.livrovivo.app.core.parentalgate.ParentalGateDialog
import com.livrovivo.app.core.audio.AudioPlayerController
import com.livrovivo.app.core.settings.SettingsManager
import com.livrovivo.app.core.theme.FairyGold
import com.livrovivo.app.core.theme.FairyPurple
import com.livrovivo.app.core.ui.BouncyCardButton
import com.livrovivo.app.core.ui.CompanionGreetingCard
import com.livrovivo.app.core.ui.InfoPill
import com.livrovivo.app.core.ui.LocalImage
import com.livrovivo.app.core.ui.MagicalSparklesEffect
import com.livrovivo.app.core.ui.BookScene
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.MagicalCompanion
import com.livrovivo.app.domain.model.Story
import com.livrovivo.app.domain.model.ThemeOption
import com.livrovivo.app.domain.repository.LiteracyRepository
import com.livrovivo.app.domain.usecase.CheckStoryQuotaUseCase
import com.livrovivo.app.domain.usecase.DeleteStoryUseCase
import com.livrovivo.app.domain.usecase.GetActiveChildUseCase
import com.livrovivo.app.domain.usecase.GetStoriesUseCase
import com.livrovivo.app.domain.usecase.QuotaStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class HomeUiState(
    val activeChild: ChildProfile? = null,
    val profiles: List<ChildProfile> = emptyList(),
    val stories: List<Story> = emptyList(),
    val quotaStatus: QuotaStatus = QuotaStatus.Limited(remaining = 3, max = 3),
    val aiConfigured: Boolean = true
) {
    val inProgress: Story? get() = stories.firstOrNull { !it.isCompleted && it.chapters.isNotEmpty() && !it.isEuLeio }
}

/** Cartão "Aprender a ler" da Home: quantas fases a criança já concluiu. */
data class LiteracyCardState(val completedPhases: Int, val totalPhases: Int)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    getStoriesUseCase: GetStoriesUseCase,
    private val getActiveChildUseCase: GetActiveChildUseCase,
    private val checkStoryQuotaUseCase: CheckStoryQuotaUseCase,
    private val deleteStoryUseCase: DeleteStoryUseCase,
    settingsManager: SettingsManager,
    backendConfigured: Boolean,
    private val audioPlayerController: AudioPlayerController,
    private val literacyRepository: LiteracyRepository
) : ViewModel() {

    private val quota = kotlinx.coroutines.flow.MutableStateFlow<QuotaStatus>(QuotaStatus.Limited(3, 3))

    val uiState: StateFlow<HomeUiState> = combine(
        getStoriesUseCase(),
        getActiveChildUseCase(),
        settingsManager.settingsFlow,
        quota,
        getActiveChildUseCase.profiles()
    ) { stories, child, settings, quotaStatus, profiles ->
        HomeUiState(
            activeChild = child,
            profiles = profiles,
            stories = stories.filter { it.childId == child?.id },
            quotaStatus = quotaStatus,
            aiConfigured = settings.hasGeminiKey || backendConfigured
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** null esconde o cartão (faixa 9+, ou trilha indisponível). */
    val literacyCard: StateFlow<LiteracyCardState?> = combine(getActiveChildUseCase(), settingsManager.settingsFlow) { child, settings ->
        child to settings.showTrailForOlderKids
    }.flatMapLatest { (child, olderKids) ->
        if (child == null || !LiteracyRules.isTrailVisible(child.ageGroup, olderKids)) return@flatMapLatest flowOf(null)
        val total = runCatching { literacyRepository.getTrail().phases.size }.getOrNull()
            ?: return@flatMapLatest flowOf(null)
        literacyRepository.observeProgress(child.id).map { progress ->
            LiteracyCardState(completedPhases = progress.count { it.isCompleted }, totalPhases = total)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        refreshQuota()
    }

    fun refreshQuota() {
        viewModelScope.launch { quota.value = checkStoryQuotaUseCase() }
    }

    fun selectProfile(id: String) {
        stopHelp()
        viewModelScope.launch { getActiveChildUseCase.activate(id) }
    }

    fun speakHelp() {
        val name = uiState.value.activeChild?.name ?: "pequeno leitor"
        val text = "Olá, $name! Toque em Nova aventura para escolher um tema. Para continuar uma história, toque na capa do livro. Vamos imaginar juntos?"
        val key = "home-help#${text.hashCode()}"
        if (audioPlayerController.playbackState.value.chapterKey == key) audioPlayerController.replay()
        else audioPlayerController.load(key, text, null, "alegre", null, true)
    }

    fun stopHelp() {
        if (audioPlayerController.playbackState.value.chapterKey?.startsWith("home-help#") == true) {
            audioPlayerController.onReaderStopped()
        }
    }

    fun deleteStory(storyId: String) {
        viewModelScope.launch {
            deleteStoryUseCase(storyId)
            quota.value = checkStoryQuotaUseCase()
        }
    }
}

private fun greetingFor(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Bom dia! Que tal uma aventura para começar o dia com um sorriso?"
        in 12..17 -> "Boa tarde! Estou com a imaginação a mil. Vamos criar uma história?"
        else -> "Boa noite! Eu já estou de pijama. Que tal uma história calminha antes de dormir?"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToCreation: () -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToParentArea: () -> Unit,
    onNavigateToLiteracy: () -> Unit,
    onNavigateToEuLeio: (storyId: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val literacyCard by viewModel.literacyCard.collectAsState()
    DisposableEffect(viewModel) { onDispose { viewModel.stopHelp() } }
    var afterParentalGate by remember { mutableStateOf<(() -> Unit)?>(null) }
    var storyToDelete by remember { mutableStateOf<Story?>(null) }

    if (afterParentalGate != null) {
        ParentalGateDialog(
            onDismiss = { afterParentalGate = null },
            onSuccess = {
                val action = afterParentalGate
                afterParentalGate = null
                action?.invoke()
            }
        )
    }

    storyToDelete?.let { story ->
        AlertDialog(
            onDismissRequest = { storyToDelete = null },
            title = { Text("Guardar na lixeira?") },
            text = { Text("\"${story.title}\" poderá ser restaurada na Área dos Pais.") },
            confirmButton = {
                Button(
                    onClick = {
                        afterParentalGate = { viewModel.deleteStory(story.id) }
                        storyToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Mover para a lixeira") }
            },
            dismissButton = { TextButton(onClick = { storyToDelete = null }) { Text("Cancelar") } }
        )
    }

    val child = uiState.activeChild
    val companion = MagicalCompanion.findById(child?.companionId)
    val childName = child?.name ?: "Pequeno Explorador"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Livro Vivo ✨",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    IconButton(onClick = { afterParentalGate = onNavigateToParentArea }) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Área dos Pais",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            BouncyCardButton(
                onClick = onNavigateToCreation,
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = FairyGold)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Nova aventura",
                        style = MaterialTheme.typography.labelLarge,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            MagicalSparklesEffect()

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 158.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 110.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (uiState.profiles.size > 1) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Text("Quem vai viver a aventura?", style = MaterialTheme.typography.titleMedium)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                uiState.profiles.forEach { profile ->
                                    FilterChip(
                                        selected = profile.id == child?.id,
                                        onClick = { viewModel.selectProfile(profile.id) },
                                        label = { Text("${MagicalCompanion.findById(profile.companionId).emoji} ${profile.name}") }
                                    )
                                }
                            }
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CompanionGreetingCard(
                        companion = companion,
                        childName = childName,
                        speechText = greetingFor(),
                        onClick = viewModel::speakHelp
                    )
                }

                if (!uiState.aiConfigured) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ParentHintCard(onClick = { afterParentalGate = onNavigateToParentArea })
                    }
                }

                literacyCard?.let { card ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LearnToReadCard(state = card, onClick = onNavigateToLiteracy)
                    }
                }

                uiState.inProgress?.let { story ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ContinueReadingCard(story = story, onClick = { onNavigateToReader(story.id) })
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Minha estante 📚",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        when (val quota = uiState.quotaStatus) {
                            is QuotaStatus.Limited -> InfoPill(
                                text = "${quota.remaining} de ${quota.max} grátis",
                                color = MaterialTheme.colorScheme.secondaryContainer
                            )
                            QuotaStatus.Unlimited -> InfoPill(text = "Premium ⭐", color = MaterialTheme.colorScheme.secondaryContainer)
                        }
                    }
                }

                if (uiState.stories.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        EmptyShelfCard(childName = childName, onCreate = onNavigateToCreation)
                    }
                } else {
                    items(uiState.stories, key = { it.id }) { story ->
                        BookCover(
                            story = story,
                            onClick = { if (story.isEuLeio) onNavigateToEuLeio(story.id) else onNavigateToReader(story.id) },
                            onDelete = { storyToDelete = story }
                        )
                    }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "Dica: toque em ⋮ no livro para apagá-lo. Os pais também podem apagar todas na Área dos Pais.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ParentHintCard(onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "🪄", fontSize = 26.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Para os pais: ative a magia completa",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "Conecte a IA para histórias únicas, ilustrações e narradores com voz natural.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun LearnToReadCard(state: LiteracyCardState, onClick: () -> Unit) {
    BouncyCardButton(
        onClick = onClick,
        containerColor = FairyEmeraldDeep,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "ABC", fontSize = 24.sp, fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Aprender a ler",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Text(
                    text = "${state.completedPhases} de ${state.totalPhases} fases",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { state.completedPhases.toFloat() / state.totalPhases.coerceAtLeast(1) },
                    color = FairyGold,
                    trackColor = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = FairyGold, modifier = Modifier.size(40.dp))
        }
    }
}

/** Verde mais escuro que o FairyEmerald, para o texto branco ter contraste suficiente. */
private val FairyEmeraldDeep = Color(0xFF0B7A57)

@Composable
private fun ContinueReadingCard(story: Story, onClick: () -> Unit) {
    val scene = ThemeOption.sceneFor(story.themeId, story.theme)
    val lastRead = story.lastReadChapter.coerceAtMost(story.lastChapter?.index ?: 1)
    Card(
        shape = RoundedCornerShape(26.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            BookScene(scene = scene, modifier = Modifier.fillMaxSize())
            LocalImage(path = story.coverPath, maxSide = 900, modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Continuar aventura",
                        style = MaterialTheme.typography.labelLarge,
                        color = FairyGold
                    )
                    Text(
                        text = story.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { lastRead.toFloat() / story.plannedChapters.coerceAtLeast(1) },
                        color = FairyGold,
                        trackColor = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    Text(
                        text = "Página $lastRead de ${story.plannedChapters}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(FairyGold),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Continuar", tint = Color(0xFF422800), modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCover(story: Story, onClick: () -> Unit, onDelete: () -> Unit) {
    val scene = ThemeOption.sceneFor(story.themeId, story.theme)
    val companion = MagicalCompanion.findById(story.companionId)
    var menuOpen by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FairyGold.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
            .combinedClickable(onClick = onClick, onLongClick = onDelete)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
        ) {
            // Livro "Eu leio" sem ilustração (offline) mostra o título na capa; com ilustração da IA, a imagem.
            if (story.isEuLeio && story.coverPath == null) {
                EuLeioCoverArt(title = story.title)
            } else {
                BookScene(scene = scene, modifier = Modifier.fillMaxSize())
                LocalImage(path = story.coverPath, maxSide = 600, modifier = Modifier.fillMaxSize())
            }
            InfoPill(
                text = when {
                    story.isEuLeio && story.isCompleted -> "Eu li! ⭐"
                    story.isEuLeio -> "Eu leio 📖"
                    story.isCompleted -> "Concluída ⭐"
                    else -> "Pág. ${story.lastChapter?.index ?: 1}/${story.plannedChapters}"
                },
                color = if (story.isCompleted) FairyGold else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Opções de ${story.title}", tint = Color.White)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Apagar história") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = story.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = ThemeOption.findById(story.themeId)?.let { "${it.emoji} ${it.title}" } ?: story.theme,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Capa dos livros "Eu leio": o título em letra de forma, que a própria criança consegue ler. */
@Composable
private fun EuLeioCoverArt(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(FairyEmeraldDeep, Color(0xFF10B981)))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = Color.White,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
    }
}

@Composable
private fun EmptyShelfCard(childName: String, onCreate: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "🪄📖✨", fontSize = 44.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "A estante de $childName está esperando a primeira história!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Escolha um tema e decida os rumos da aventura. Cada escolha cria um caminho diferente.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
            )
            Button(
                onClick = onCreate,
                colors = ButtonDefaults.buttonColors(containerColor = FairyPurple)
            ) { Text("Criar minha primeira história") }
        }
    }
}
