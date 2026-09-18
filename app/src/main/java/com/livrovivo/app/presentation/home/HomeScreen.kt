package com.livrovivo.app.presentation.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Bedtime
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livrovivo.app.core.bedtime.BedtimeMode
import com.livrovivo.app.core.bedtime.minuteTicks
import com.livrovivo.app.core.parentalgate.ParentalGateDialog
import com.livrovivo.app.core.settings.SettingsManager
import com.livrovivo.app.core.theme.FairyGold
import com.livrovivo.app.core.theme.FairyNightSurface
import com.livrovivo.app.core.theme.FairyPurple
import com.livrovivo.app.core.ui.BouncyCardButton
import com.livrovivo.app.core.ui.ActiveChildChip
import com.livrovivo.app.core.ui.ChildSwitcherSheet
import com.livrovivo.app.core.ui.CompanionGreetingCard
import com.livrovivo.app.core.ui.InfoPill
import com.livrovivo.app.core.ui.LocalImage
import com.livrovivo.app.core.ui.MagicalSparklesEffect
import com.livrovivo.app.core.ui.ProceduralScene
import com.livrovivo.app.domain.model.AdventureMemory
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.MagicalCompanion
import com.livrovivo.app.domain.model.Story
import com.livrovivo.app.domain.model.ThemeOption
import com.livrovivo.app.domain.usecase.CheckStoryQuotaUseCase
import com.livrovivo.app.domain.usecase.DeleteStoryUseCase
import com.livrovivo.app.domain.usecase.GetActiveChildUseCase
import com.livrovivo.app.domain.usecase.GetChildProfilesUseCase
import com.livrovivo.app.domain.usecase.GetStoriesUseCase
import com.livrovivo.app.domain.usecase.QuotaStatus
import com.livrovivo.app.domain.usecase.SwitchChildUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    val inProgress: Story? get() = stories.firstOrNull { !it.isCompleted && it.chapters.isNotEmpty() }

    /** Com uma criança só não há o que trocar, então o seletor fica escondido. */
    val hasSiblings: Boolean get() = profiles.size > 1
}

class HomeViewModel(
    getStoriesUseCase: GetStoriesUseCase,
    getActiveChildUseCase: GetActiveChildUseCase,
    getChildProfilesUseCase: GetChildProfilesUseCase,
    private val switchChildUseCase: SwitchChildUseCase,
    private val checkStoryQuotaUseCase: CheckStoryQuotaUseCase,
    private val deleteStoryUseCase: DeleteStoryUseCase,
    settingsManager: SettingsManager,
    backendConfigured: Boolean
) : ViewModel() {

    private val quota = kotlinx.coroutines.flow.MutableStateFlow<QuotaStatus>(QuotaStatus.Limited(3, 3))

    val uiState: StateFlow<HomeUiState> = combine(
        getStoriesUseCase(),
        getActiveChildUseCase(),
        getChildProfilesUseCase(),
        settingsManager.settingsFlow,
        quota
    ) { stories, child, profiles, settings, quotaStatus ->
        HomeUiState(
            activeChild = child,
            profiles = profiles,
            stories = stories,
            quotaStatus = quotaStatus,
            aiConfigured = settings.hasGeminiKey || backendConfigured
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Modo da noite da criança ativa: muda com o relógio e quando uma história termina. */
    val bedtimeMode: StateFlow<BedtimeMode> = combine(
        settingsManager.settingsFlow,
        getActiveChildUseCase(),
        minuteTicks()
    ) { settings, child, _ -> settings.bedtimeModeFor(child?.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BedtimeMode.OFF)

    /** Abre a estante de outro irmão; as histórias e o limite se atualizam sozinhos. */
    fun switchChild(childId: String) {
        viewModelScope.launch { switchChildUseCase(childId) }
    }

    init {
        refreshQuota()
    }

    fun refreshQuota() {
        viewModelScope.launch { quota.value = checkStoryQuotaUseCase() }
    }

    fun deleteStory(storyId: String) {
        viewModelScope.launch {
            deleteStoryUseCase(storyId)
            quota.value = checkStoryQuotaUseCase()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToCreation: () -> Unit,
    onNavigateToReader: (String) -> Unit,
    onNavigateToParentArea: () -> Unit,
    onNavigateToEditProfile: () -> Unit,
    onGoodnight: (childId: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val bedtimeMode by viewModel.bedtimeMode.collectAsState()
    var showParentalGate by remember { mutableStateOf(false) }
    var showChildSwitcher by remember { mutableStateOf(false) }
    var storyToDelete by remember { mutableStateOf<Story?>(null) }

    if (showParentalGate) {
        ParentalGateDialog(
            onDismiss = { showParentalGate = false },
            onSuccess = {
                showParentalGate = false
                onNavigateToParentArea()
            }
        )
    }

    if (showChildSwitcher) {
        ChildSwitcherSheet(
            profiles = uiState.profiles,
            activeChildId = uiState.activeChild?.id,
            onSelect = { childId ->
                viewModel.switchChild(childId)
                showChildSwitcher = false
            },
            onDismiss = { showChildSwitcher = false },
            onManage = {
                showChildSwitcher = false
                showParentalGate = true
            }
        )
    }

    storyToDelete?.let { story ->
        AlertDialog(
            onDismissRequest = { storyToDelete = null },
            title = { Text("Apagar história?") },
            text = { Text("\"${story.title}\" e suas ilustrações serão removidas deste aparelho.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteStory(story.id)
                        storyToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Apagar") }
            },
            dismissButton = { TextButton(onClick = { storyToDelete = null }) { Text("Cancelar") } }
        )
    }

    val child = uiState.activeChild
    val companion = MagicalCompanion.findById(child?.companionId)
    val childName = child?.name ?: "Pequeno Explorador"
    val lastAdventure = remember(uiState.stories) {
        AdventureMemory.recent(uiState.stories, limit = 1).firstOrNull()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Livro Vivo ✨",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                actions = {
                    if (uiState.hasSiblings) {
                        child?.let {
                            ActiveChildChip(
                                child = it,
                                onClick = { showChildSwitcher = true },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                    IconButton(onClick = { showParentalGate = true }) {
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
            // Com o limite da noite atingido, "mais uma" dá lugar ao boa-noite.
            val windDown = bedtimeMode == BedtimeMode.REQUIRED && child != null
            BouncyCardButton(
                onClick = { if (windDown) child?.let { onGoodnight(it.id) } else onNavigateToCreation() },
                containerColor = if (windDown) FairyNightSurface else MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (windDown) Icons.Default.Bedtime else Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = FairyGold
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (windDown) "Boa noite" else "Nova aventura",
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
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CompanionGreetingCard(
                        companion = companion,
                        childName = childName,
                        speechText = if (bedtimeMode == BedtimeMode.REQUIRED) {
                            CompanionGreeting.WIND_DOWN
                        } else {
                            CompanionGreeting.forHome(
                                hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                                companion = companion,
                                memory = lastAdventure,
                                now = System.currentTimeMillis()
                            )
                        },
                        onClick = onNavigateToEditProfile
                    )
                }

                if (!uiState.aiConfigured) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ParentHintCard(onClick = { showParentalGate = true })
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
                            onClick = { onNavigateToReader(story.id) },
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
            ProceduralScene(scene = scene, animate = false, modifier = Modifier.fillMaxSize())
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
            ProceduralScene(scene = scene, companionEmoji = companion.emoji, animate = false, modifier = Modifier.fillMaxSize())
            LocalImage(path = story.coverPath, maxSide = 600, modifier = Modifier.fillMaxSize())
            InfoPill(
                text = if (story.isCompleted) "Concluída ⭐" else "Pág. ${story.lastChapter?.index ?: 1}/${story.plannedChapters}",
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
