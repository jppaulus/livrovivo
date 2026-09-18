package com.livrovivo.app.presentation.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livrovivo.app.core.audio.AudioPlayerController
import com.livrovivo.app.core.settings.AppSettings
import com.livrovivo.app.core.bedtime.Bedtime
import com.livrovivo.app.core.bedtime.BedtimeSettings
import com.livrovivo.app.core.settings.SettingsManager
import com.livrovivo.app.core.theme.FairyEmerald
import com.livrovivo.app.core.ui.ChildAvatar
import com.livrovivo.app.core.ui.CompanionAvatar
import com.livrovivo.app.core.ui.SectionHeader
import com.livrovivo.app.domain.model.AgeGroup
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.MagicalCompanion
import com.livrovivo.app.domain.model.ParentInsights
import com.livrovivo.app.domain.model.Story
import com.livrovivo.app.domain.model.Virtue
import com.livrovivo.app.domain.usecase.CheckStoryQuotaUseCase
import com.livrovivo.app.domain.usecase.DeleteAllStoriesUseCase
import com.livrovivo.app.domain.usecase.DeleteChildProfileUseCase
import com.livrovivo.app.domain.usecase.DeleteStoriesOfChildUseCase
import com.livrovivo.app.domain.usecase.DeleteStoryUseCase
import com.livrovivo.app.domain.usecase.GetActiveChildUseCase
import com.livrovivo.app.domain.usecase.GetChildProfilesUseCase
import com.livrovivo.app.domain.usecase.GetParentInsightsUseCase
import com.livrovivo.app.domain.usecase.GetStoriesUseCase
import com.livrovivo.app.domain.usecase.QuotaStatus
import com.livrovivo.app.domain.usecase.SwitchChildUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ParentDashboardUiState(
    val isLoading: Boolean = true,
    val child: ChildProfile? = null,
    val profiles: List<ChildProfile> = emptyList(),
    val insights: ParentInsights = ParentInsights(),
    val quota: QuotaStatus = QuotaStatus.Limited(3, 3),
    val settings: AppSettings = AppSettings(),
    val backendConfigured: Boolean = false,
    val stories: List<Story> = emptyList(),
    val isDeleting: Boolean = false
)

class ParentDashboardViewModel(
    private val getParentInsightsUseCase: GetParentInsightsUseCase,
    private val getActiveChildUseCase: GetActiveChildUseCase,
    private val getChildProfilesUseCase: GetChildProfilesUseCase,
    private val switchChildUseCase: SwitchChildUseCase,
    private val deleteChildProfileUseCase: DeleteChildProfileUseCase,
    private val checkStoryQuotaUseCase: CheckStoryQuotaUseCase,
    private val settingsManager: SettingsManager,
    getStoriesUseCase: GetStoriesUseCase,
    private val deleteStoryUseCase: DeleteStoryUseCase,
    private val deleteAllStoriesUseCase: DeleteAllStoriesUseCase,
    private val deleteStoriesOfChildUseCase: DeleteStoriesOfChildUseCase,
    private val audioPlayerController: AudioPlayerController,
    private val backendConfigured: Boolean
) : ViewModel() {

    private val _uiState = MutableStateFlow(ParentDashboardUiState(backendConfigured = backendConfigured))
    val uiState: StateFlow<ParentDashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            getStoriesUseCase().collect { stories -> _uiState.update { it.copy(stories = stories) } }
        }
    }

    fun deleteStory(storyId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            deleteStoryUseCase(storyId)
            _uiState.update { it.copy(isDeleting = false) }
            refresh()
        }
    }

    /**
     * Limpa a estante mostrada na tela — que é a da criança em foco. Sem nenhum irmão
     * cadastrado, dá no mesmo que apagar tudo; com irmãos, os outros ficam intactos.
     */
    fun deleteAllStories() {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id
            _uiState.update { it.copy(isDeleting = true) }
            audioPlayerController.stop()
            if (childId == null) deleteAllStoriesUseCase() else deleteStoriesOfChildUseCase(childId)
            audioPlayerController.clearNarrationCache()
            _uiState.update { it.copy(isDeleting = false) }
            refreshNow()
        }
    }

    /** Troca a criança em foco: as métricas e a estante passam a ser dela. */
    fun switchChild(childId: String) {
        viewModelScope.launch {
            audioPlayerController.stop()
            switchChildUseCase(childId)
            refreshNow()
        }
    }

    /**
     * Apaga um irmão com as histórias, ilustrações e métricas dele.
     * O último perfil não é apagado — o app não abre sem nenhuma criança.
     */
    fun deleteChild(childId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            audioPlayerController.stop()
            val removed = deleteChildProfileUseCase(childId)
            _uiState.update { it.copy(isDeleting = false) }
            if (removed) refreshNow()
        }
    }

    fun refresh() {
        viewModelScope.launch { refreshNow() }
    }

    /** Horário em que começa a hora de dormir; null desliga o ritual. */
    fun setBedtimeStart(minutes: Int?) {
        viewModelScope.launch {
            settingsManager.setBedtimeStart(minutes)
            refreshNow()
        }
    }

    /** Histórias por noite antes do boa-noite obrigatório; null = sem limite. */
    fun setStoriesPerNight(count: Int?) {
        viewModelScope.launch {
            settingsManager.setStoriesPerNight(count)
            refreshNow()
        }
    }

    private suspend fun refreshNow() {
        val child = getActiveChildUseCase.getDirect()
        val profiles = getChildProfilesUseCase.getDirect()
        val insights = getParentInsightsUseCase()
        val quota = checkStoryQuotaUseCase()
        val settings = settingsManager.current()
        _uiState.update {
            it.copy(
                isLoading = false,
                child = child,
                profiles = profiles,
                insights = insights,
                quota = quota,
                settings = settings
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ParentDashboardScreen(
    viewModel: ParentDashboardViewModel,
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPaywall: () -> Unit,
    onEditProfile: () -> Unit,
    onAddChild: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    var storyToDelete by remember { mutableStateOf<Story?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    var childToDelete by remember { mutableStateOf<ChildProfile?>(null) }

    val insights = uiState.insights
    val childName = uiState.child?.name ?: "a criança"

    childToDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { childToDelete = null },
            title = { Text("Apagar o perfil de ${profile.name}?") },
            text = {
                Text(
                    "As histórias, ilustrações e métricas de ${profile.name} serão removidas deste " +
                        "aparelho. Os outros perfis não são afetados. Não dá para desfazer."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteChild(profile.id)
                        childToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Apagar perfil") }
            },
            dismissButton = { TextButton(onClick = { childToDelete = null }) { Text("Cancelar") } }
        )
    }

    storyToDelete?.let { story ->
        AlertDialog(
            onDismissRequest = { storyToDelete = null },
            title = { Text("Apagar história?") },
            text = { Text("\"${story.title}\", suas ilustrações e o progresso serão removidos deste aparelho.") },
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

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Apagar as histórias de $childName?") },
            text = {
                Text(
                    buildString {
                        append("As ${uiState.stories.size} histórias de $childName, com ilustrações, ")
                        append("narrações salvas e o histórico de leitura, serão removidas deste aparelho. ")
                        if (uiState.profiles.size > 1) append("As estantes dos irmãos não são afetadas. ")
                        append("Não dá para desfazer.")
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteAllStories()
                        confirmDeleteAll = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Apagar todas") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancelar") } }
        )
    }

    val companion = MagicalCompanion.findById(uiState.child?.companionId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Área dos Pais", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CompanionAvatar(companion = companion, size = 54.dp, animated = false)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Jornada de $childName",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Leitura, escolhas e desenvolvimento socioemocional",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        OutlinedButton(onClick = onEditProfile) { Text("Editar") }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        MetricItem("Histórias", "${insights.storiesCompleted}/${insights.storiesStarted}", Icons.Default.AutoAwesome)
                        MetricItem("Páginas", "${insights.pagesRead}", Icons.AutoMirrored.Filled.MenuBook)
                        MetricItem("Minutos", "${insights.minutesReading}", Icons.Default.Timer)
                        MetricItem("Escolhas", "${insights.choicesMade}", Icons.Default.TouchApp)
                    }
                }
            }

            SectionHeader(
                title = "Crianças",
                subtitle = "Cada criança tem a própria estante, métricas e personalização."
            )
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.profiles.forEach { profile ->
                        ChildManageRow(
                            child = profile,
                            isActive = profile.id == uiState.child?.id,
                            canDelete = uiState.profiles.size > 1,
                            onSelect = { viewModel.switchChild(profile.id) },
                            onEdit = {
                                viewModel.switchChild(profile.id)
                                onEditProfile()
                            },
                            onDelete = { childToDelete = profile }
                        )
                    }
                    OutlinedButton(
                        onClick = onAddChild,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Adicionar criança")
                    }
                }
            }

            insights.conversationTip?.let { tip ->
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(26.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Conversa pós-história 💬", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(tip, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            SectionHeader(
                title = "Virtudes nas escolhas",
                subtitle = "Cada decisão na história estimula uma competência."
            )
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (insights.virtueCounts.isEmpty()) {
                        Text(
                            text = "As escolhas de $childName aparecerão aqui assim que as primeiras histórias forem lidas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    } else {
                        val max = insights.virtueCounts.values.maxOrNull()?.coerceAtLeast(1) ?: 1
                        Virtue.entries.sortedByDescending { insights.virtueCounts[it] ?: 0 }.forEach { virtue ->
                            VirtueBar(virtue, insights.virtueCounts[virtue] ?: 0, max)
                        }
                    }
                }
            }

            if (insights.vocabulary.isNotEmpty()) {
                SectionHeader(title = "Palavras novas 💡", subtitle = "Vocabulário apresentado pelo contexto das histórias.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    insights.vocabulary.forEach { word ->
                        SuggestionChip(onClick = {}, label = { Text(word, fontWeight = FontWeight.SemiBold) })
                    }
                }
            }

            if (insights.favoriteThemes.isNotEmpty()) {
                SectionHeader(title = "Temas favoritos")
                insights.favoriteThemes.forEach { (theme, count) ->
                    Text("• $theme ($count)", style = MaterialTheme.typography.bodyMedium)
                }
            }

            SectionHeader(
                title = "Histórias de $childName (${uiState.stories.size})",
                subtitle = "Apague histórias para liberar espaço ou recomeçar a estante."
            )
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    if (uiState.stories.isEmpty()) {
                        Text(
                            text = "Nenhuma história salva.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.padding(vertical = 10.dp)
                        )
                    }
                    uiState.stories.forEachIndexed { index, story ->
                        if (index > 0) HorizontalDivider()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = story.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = listOf(
                                        if (story.isCompleted) "Concluída" else "Página ${story.lastChapter?.index ?: 1} de ${story.plannedChapters}",
                                        if (story.isOffline) "sem IA" else "com IA",
                                        dateFormat.format(Date(story.createdAt))
                                    ).joinToString(" · "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                )
                            }
                            IconButton(onClick = { storyToDelete = story }, enabled = !uiState.isDeleting) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Apagar ${story.title}",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
            if (uiState.stories.isNotEmpty()) {
                OutlinedButton(
                    onClick = { confirmDeleteAll = true },
                    enabled = !uiState.isDeleting,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Apagar as histórias de $childName")
                }
                if (uiState.quota is QuotaStatus.Limited) {
                    Text(
                        text = "No plano gratuito, o limite considera as histórias já criadas, mesmo que sejam apagadas.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }

            SectionHeader(
                title = "Hora de dormir 🌙",
                subtitle = "No fim de cada história à noite, o Livro Vivo convida para um boa-noite com " +
                    "respiração e música. Depois dele, o app dorme até as 6h — só um adulto acorda antes."
            )
            BedtimeCard(
                bedtime = uiState.settings.bedtime,
                onStartChange = viewModel::setBedtimeStart,
                onStoriesChange = viewModel::setStoriesPerNight
            )

            SectionHeader(title = "Configurações")
            NavigationCard(
                emoji = "🪄",
                title = "IA, vozes e ilustrações",
                subtitle = aiSummary(uiState),
                onClick = onOpenSettings
            )
            NavigationCard(
                emoji = "⭐",
                title = "Livro Vivo Premium",
                subtitle = when (val quota = uiState.quota) {
                    QuotaStatus.Unlimited -> "Assinatura ativa: histórias ilimitadas."
                    is QuotaStatus.Limited -> "Plano gratuito: ${quota.remaining} de ${quota.max} histórias restantes."
                },
                onClick = onOpenPaywall
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))

private fun aiSummary(state: ParentDashboardUiState): String {
    val s = state.settings
    val text = if (s.hasGeminiKey || state.backendConfigured) "IA conectada" else "IA não configurada (modo offline)"
    val voice = when {
        s.hasElevenLabsKey -> "voz ElevenLabs"
        s.hasGeminiKey || state.backendConfigured -> "voz Gemini"
        else -> "voz do aparelho"
    }
    val art = if (s.illustrationsEnabled && (s.hasGeminiKey || state.backendConfigured)) "ilustrações ${s.illustrationStyle.title.lowercase()}" else "ilustrações do app"
    return "$text · $voice · $art"
}

@Composable
private fun NavigationCard(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = emoji, fontSize = 28.sp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

/** Escolha dos pais: quando começa a hora de dormir e quantas histórias cabem na noite. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BedtimeCard(
    bedtime: BedtimeSettings,
    onStartChange: (Int?) -> Unit,
    onStoriesChange: (Int?) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Começa às", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = bedtime.startMinutes == null,
                    onClick = { onStartChange(null) },
                    label = { Text("Desligado") }
                )
                Bedtime.START_OPTIONS.forEach { minutes ->
                    FilterChip(
                        selected = bedtime.startMinutes == minutes,
                        onClick = { onStartChange(minutes) },
                        label = { Text(Bedtime.label(minutes)) }
                    )
                }
            }

            if (bedtime.isEnabled) {
                Text("Histórias por noite", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Bedtime.STORIES_OPTIONS.forEach { count ->
                        FilterChip(
                            selected = bedtime.storiesPerNight == count,
                            onClick = { onStoriesChange(count) },
                            label = { Text(count?.toString() ?: "Sem limite") }
                        )
                    }
                }
                Text(
                    text = when (val limit = bedtime.storiesPerNight) {
                        null -> "Sem limite, o boa-noite aparece em destaque, mas a criança ainda pode escolher outra história."
                        1 -> "Depois de 1 história na hora de dormir, o boa-noite é o único caminho."
                        else -> "Depois de $limit histórias na hora de dormir, o boa-noite é o único caminho."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

/** Linha de uma criança: tocar troca o foco; lápis edita; lixeira apaga tudo dela. */
@Composable
private fun ChildManageRow(
    child: ChildProfile,
    isActive: Boolean,
    canDelete: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isActive) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
            .clickable(onClick = onSelect)
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChildAvatar(child = child, size = 40.dp, highlighted = isActive)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = child.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isActive) {
                    "${AgeGroup.fromCode(child.ageGroup).label} · em foco"
                } else {
                    AgeGroup.fromCode(child.ageGroup).label
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        IconButton(onClick = onEdit) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Editar ${child.name}",
                tint = MaterialTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onDelete, enabled = canDelete) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = if (canDelete) {
                    "Apagar o perfil de ${child.name}"
                } else {
                    "É preciso ter pelo menos uma criança cadastrada"
                },
                tint = if (canDelete) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                }
            )
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, icon: ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
    }
}

@Composable
private fun VirtueBar(virtue: Virtue, count: Int, max: Int) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "${virtue.emoji} ${virtue.title}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(
                text = if (count == 1) "1 escolha" else "$count escolhas",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { count.toFloat() / max },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = FairyEmerald,
            trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
    }
}
