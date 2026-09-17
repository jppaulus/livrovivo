package com.livrovivo.app.presentation.parent

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
import androidx.compose.material.icons.filled.Delete
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
import com.livrovivo.app.core.settings.SettingsManager
import com.livrovivo.app.core.theme.FairyEmerald
import com.livrovivo.app.core.ui.CompanionAvatar
import com.livrovivo.app.core.ui.SectionHeader
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.MagicalCompanion
import com.livrovivo.app.domain.model.ParentInsights
import com.livrovivo.app.domain.model.Story
import com.livrovivo.app.domain.model.Virtue
import com.livrovivo.app.domain.usecase.CheckStoryQuotaUseCase
import com.livrovivo.app.domain.usecase.DeleteAllStoriesUseCase
import com.livrovivo.app.domain.usecase.DeleteStoryUseCase
import com.livrovivo.app.domain.usecase.GetActiveChildUseCase
import com.livrovivo.app.domain.usecase.GetParentInsightsUseCase
import com.livrovivo.app.domain.usecase.GetStoriesUseCase
import com.livrovivo.app.domain.usecase.QuotaStatus
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
    private val checkStoryQuotaUseCase: CheckStoryQuotaUseCase,
    private val settingsManager: SettingsManager,
    getStoriesUseCase: GetStoriesUseCase,
    private val deleteStoryUseCase: DeleteStoryUseCase,
    private val deleteAllStoriesUseCase: DeleteAllStoriesUseCase,
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

    fun deleteAllStories() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            audioPlayerController.stop()
            deleteAllStoriesUseCase()
            audioPlayerController.clearNarrationCache()
            _uiState.update { it.copy(isDeleting = false) }
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    child = getActiveChildUseCase.getDirect(),
                    insights = getParentInsightsUseCase(),
                    quota = checkStoryQuotaUseCase(),
                    settings = settingsManager.current()
                )
            }
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
    onEditProfile: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    var storyToDelete by remember { mutableStateOf<Story?>(null) }
    var confirmDeleteAll by remember { mutableStateOf(false) }

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
            title = { Text("Apagar todas as histórias?") },
            text = {
                Text("As ${uiState.stories.size} histórias, com ilustrações, narrações salvas e o histórico de leitura, serão removidas deste aparelho. Não dá para desfazer.")
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

    val insights = uiState.insights
    val childName = uiState.child?.name ?: "a criança"
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
                title = "Histórias salvas (${uiState.stories.size})",
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
                    Text("Apagar todas as histórias")
                }
                if (uiState.quota is QuotaStatus.Limited) {
                    Text(
                        text = "No plano gratuito, o limite considera as histórias já criadas, mesmo que sejam apagadas.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            }

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
