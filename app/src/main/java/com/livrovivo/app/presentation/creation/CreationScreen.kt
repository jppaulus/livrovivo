package com.livrovivo.app.presentation.creation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livrovivo.app.core.ai.AiException
import com.livrovivo.app.core.audio.AudioPlayerController
import com.livrovivo.app.core.audio.VoicePersona
import com.livrovivo.app.core.bedtime.BedtimeMode
import com.livrovivo.app.core.parentalgate.ParentalGateDialog
import com.livrovivo.app.core.settings.SettingsManager
import com.livrovivo.app.core.theme.FairyGold
import com.livrovivo.app.core.ui.BouncyCardButton
import com.livrovivo.app.core.ui.CompanionAvatar
import com.livrovivo.app.core.ui.MagicalSparklesEffect
import com.livrovivo.app.core.ui.ProceduralScene
import com.livrovivo.app.core.ui.SectionHeader
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.MagicalCompanion
import com.livrovivo.app.domain.model.ObjectiveType
import com.livrovivo.app.domain.model.SceneKind
import com.livrovivo.app.domain.model.ThemeOption
import com.livrovivo.app.domain.usecase.GenerateStoryUseCase
import com.livrovivo.app.domain.usecase.GetActiveChildUseCase
import com.livrovivo.app.domain.usecase.GetStoriesUseCase
import com.livrovivo.app.domain.usecase.QuotaExceededException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreationUiState(
    val selectedThemeId: String = ThemeOption.PRESETS.first().id,
    val customTheme: String = "",
    val selectedObjective: ObjectiveType = ThemeOption.PRESETS.first().defaultObjective,
    val selectedPersona: VoicePersona = VoicePersona.DEFAULT,
    val child: ChildProfile? = null,
    val isGenerating: Boolean = false,
    val generatedStoryId: String? = null,
    val errorMessage: String? = null,
    val canCreateOffline: Boolean = false,
    val quotaExceeded: Boolean = false,
    /** Se a criança tem histórias para reler; com irmãos, a estante dela pode estar vazia. */
    val shelfHasStories: Boolean = true,
    /** Limite de histórias da noite atingido: em vez de criar, é hora do boa-noite. */
    val bedtimeReached: Boolean = false,
    val storiesTonight: Int = 0
) {
    val isCustom: Boolean get() = selectedThemeId == ThemeOption.CUSTOM_ID
    val canGenerate: Boolean get() = !isGenerating && (!isCustom || customTheme.trim().length >= 3)
}

class CreationViewModel(
    private val generateStoryUseCase: GenerateStoryUseCase,
    private val getActiveChildUseCase: GetActiveChildUseCase,
    private val getStoriesUseCase: GetStoriesUseCase,
    private val settingsManager: SettingsManager,
    private val audioPlayerController: AudioPlayerController
) : ViewModel() {

    val themes = ThemeOption.PRESETS

    val customSuggestions = listOf(
        "Um dragão com medo de altura",
        "O dia em que meu cachorro falou",
        "Uma viagem dentro de um arco-íris",
        "O mistério da meia desaparecida"
    )

    private val _uiState = MutableStateFlow(CreationUiState())
    val uiState: StateFlow<CreationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsManager.current()
            val child = getActiveChildUseCase.getDirect()
            _uiState.update {
                it.copy(
                    child = child,
                    selectedPersona = VoicePersona.fromId(settings.defaultPersonaId),
                    bedtimeReached = settings.bedtimeModeFor(child?.id) == BedtimeMode.REQUIRED,
                    storiesTonight = settings.storiesTonightFor(child?.id)
                )
            }
        }
    }

    fun dismissBedtime() {
        _uiState.update { it.copy(bedtimeReached = false) }
    }

    fun selectTheme(themeId: String) {
        val preset = ThemeOption.findById(themeId)
        _uiState.update {
            it.copy(
                selectedThemeId = themeId,
                selectedObjective = preset?.defaultObjective ?: it.selectedObjective,
                errorMessage = null,
                canCreateOffline = false
            )
        }
    }

    fun updateCustomTheme(text: String) {
        _uiState.update { it.copy(customTheme = text.take(120), errorMessage = null) }
    }

    fun selectObjective(objective: ObjectiveType) {
        _uiState.update { it.copy(selectedObjective = objective) }
    }

    fun selectPersona(persona: VoicePersona) {
        _uiState.update { it.copy(selectedPersona = persona) }
        audioPlayerController.setPersona(persona)
    }

    fun generateStory(forceOffline: Boolean = false) {
        val state = _uiState.value
        if (!state.canGenerate) return
        val themeText = if (state.isCustom) state.customTheme.trim() else ThemeOption.findById(state.selectedThemeId)?.title.orEmpty()
        _uiState.update { it.copy(isGenerating = true, errorMessage = null, canCreateOffline = false) }

        viewModelScope.launch {
            generateStoryUseCase(
                theme = themeText,
                objectiveType = state.selectedObjective.code,
                themeId = state.selectedThemeId,
                forceOffline = forceOffline
            ).onSuccess { story ->
                // Mantém isGenerating = true até sair da tela: evita criar uma segunda história com um toque extra.
                _uiState.update { it.copy(generatedStoryId = story.id) }
            }.onFailure { error ->
                val shelfHasStories = error !is QuotaExceededException ||
                    getStoriesUseCase().first().isNotEmpty()
                _uiState.update {
                    when (error) {
                        is QuotaExceededException -> it.copy(
                            isGenerating = false,
                            quotaExceeded = true,
                            shelfHasStories = shelfHasStories
                        )
                        is AiException -> it.copy(
                            isGenerating = false,
                            errorMessage = error.friendlyMessage,
                            canCreateOffline = true
                        )
                        else -> it.copy(isGenerating = false, errorMessage = error.message ?: "Erro ao criar a história.")
                    }
                }
            }
        }
    }

    fun onNavigated() {
        _uiState.update { it.copy(generatedStoryId = null) }
    }

    fun dismissQuota() {
        _uiState.update { it.copy(quotaExceeded = false) }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreationScreen(
    viewModel: CreationViewModel,
    onNavigateBack: () -> Unit,
    onStoryGenerated: (String) -> Unit,
    onNavigateToPaywall: () -> Unit,
    onGoodnight: (childId: String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showGate by remember { mutableStateOf(false) }
    val companion = MagicalCompanion.findById(uiState.child?.companionId)

    LaunchedEffect(uiState.generatedStoryId) {
        uiState.generatedStoryId?.let { id ->
            viewModel.onNavigated()
            onStoryGenerated(id)
        }
    }

    // Os pais limitaram as histórias da noite e elas já foram: o convite é para o boa-noite.
    val bedtimeChild = uiState.child
    if (uiState.bedtimeReached && bedtimeChild != null) {
        val tonight = if (uiState.storiesTonight == 1) "uma aventura" else "${uiState.storiesTonight} aventuras"
        val companionName = MagicalCompanion.findById(bedtimeChild.companionId).name
        AlertDialog(
            onDismissRequest = {
                viewModel.dismissBedtime()
                onNavigateBack()
            },
            title = { Text("Já é hora de dormir 🌙") },
            text = { Text("Você já viveu $tonight hoje à noite. $companionName está com soninho… vamos dar boa-noite?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.dismissBedtime()
                    onGoodnight(bedtimeChild.id)
                }) { Text("Boa noite 🌙") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.dismissBedtime()
                    onNavigateBack()
                }) { Text("Voltar") }
            }
        )
    }

    // Este diálogo fala com a criança, então não anuncia o Premium nem pede que ela convença
    // um adulto a comprar: publicidade dirigida à criança é abusiva (CDC, art. 37, §2º).
    // Ela é levada ao que ainda pode fazer — descobrir outros finais, que não contam no
    // limite — e a oferta fica só atrás do portão, na Área dos Pais.
    if (uiState.quotaExceeded) {
        if (uiState.shelfHasStories) {
            AlertDialog(
                onDismissRequest = viewModel::dismissQuota,
                title = { Text("Que tal descobrir outro final? ✨") },
                text = {
                    Text(
                        "Você já criou todas as histórias novas por enquanto. Mas cada aventura da " +
                            "sua estante esconde outros finais: abra uma e descubra o que acontece " +
                            "com outra escolha!"
                    )
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.dismissQuota()
                        onNavigateBack()
                    }) { Text("Ir para a estante") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        viewModel.dismissQuota()
                        showGate = true
                    }) { Text("Área dos pais 🔒") }
                }
            )
        } else {
            // Estante vazia (um irmão usou as histórias grátis): não há o que reler,
            // então só resta um adulto — dito sem anunciar nada.
            AlertDialog(
                onDismissRequest = viewModel::dismissQuota,
                title = { Text("Hora de chamar um adulto 🔒") },
                text = { Text("Para criar uma história nova agora, peça ajuda a um adulto.") },
                confirmButton = {
                    Button(onClick = {
                        viewModel.dismissQuota()
                        showGate = true
                    }) { Text("Área dos pais") }
                },
                dismissButton = { TextButton(onClick = viewModel::dismissQuota) { Text("Voltar") } }
            )
        }
    }

    if (showGate) {
        ParentalGateDialog(
            onDismiss = { showGate = false },
            onSuccess = {
                showGate = false
                onNavigateToPaywall()
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nova aventura 🪄", fontWeight = FontWeight.Bold) },
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            MagicalSparklesEffect()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CompanionAvatar(companion = companion, size = 60.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Sobre o que vai ser a aventura de hoje, ${uiState.child?.name ?: "pequeno leitor"}?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                SectionHeader("Escolha o tema")
                Spacer(modifier = Modifier.height(10.dp))

                viewModel.themes.chunked(2).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(vertical = 6.dp)
                    ) {
                        row.forEach { theme ->
                            ThemeCard(
                                emoji = theme.emoji,
                                title = theme.title,
                                description = theme.description,
                                scene = theme.scene,
                                selected = uiState.selectedThemeId == theme.id,
                                onClick = { viewModel.selectTheme(theme.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }

                ThemeCard(
                    emoji = "✏️",
                    title = "Inventar um tema",
                    description = "A criança conta a ideia e a IA cria a história.",
                    scene = SceneKind.FOREST,
                    selected = uiState.isCustom,
                    onClick = { viewModel.selectTheme(ThemeOption.CUSTOM_ID) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                )

                AnimatedVisibility(visible = uiState.isCustom, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    Column {
                        OutlinedTextField(
                            value = uiState.customTheme,
                            onValueChange = viewModel::updateCustomTheme,
                            label = { Text("Sobre o que é a história?") },
                            placeholder = { Text("Ex.: uma girafa que queria voar") },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            viewModel.customSuggestions.forEach { suggestion ->
                                SuggestionChip(onClick = { viewModel.updateCustomTheme(suggestion) }, label = { Text(suggestion) })
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                SectionHeader("O que a história vai estimular?")
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    ObjectiveType.entries.forEach { objective ->
                        val selected = uiState.selectedObjective == objective
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
                            ),
                            border = if (selected) BorderStroke(2.dp, FairyGold) else null,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.selectObjective(objective) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 96.dp)
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(text = objective.iconRes, fontSize = 26.sp)
                                Text(
                                    text = objective.shortTitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                SectionHeader("Quem vai contar a história?")
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    VoicePersona.entries.forEach { persona ->
                        FilterChip(
                            selected = uiState.selectedPersona == persona,
                            onClick = { viewModel.selectPersona(persona) },
                            label = { Text("${persona.emoji} ${persona.title}") },
                            shape = RoundedCornerShape(14.dp)
                        )
                    }
                }
                Text(
                    text = uiState.selectedPersona.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )

                if (uiState.errorMessage != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Não consegui falar com a IA agora",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = uiState.errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                                Button(onClick = { viewModel.generateStory() }) { Text("Tentar de novo") }
                                if (uiState.canCreateOffline) {
                                    OutlinedButton(onClick = { viewModel.generateStory(forceOffline = true) }) { Text("Criar sem IA") }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                BouncyCardButton(
                    onClick = { viewModel.generateStory() },
                    enabled = uiState.canGenerate,
                    containerColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = FairyGold)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Começar a história",
                            style = MaterialTheme.typography.labelLarge,
                            fontSize = 19.sp,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))
            }

            CreatingOverlay(visible = uiState.isGenerating, companion = companion, childName = uiState.child?.name)
        }
    }
}

@Composable
private fun ThemeCard(
    emoji: String,
    title: String,
    description: String,
    scene: SceneKind,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        border = if (selected) BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 6.dp else 1.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
        ) {
            ProceduralScene(scene = scene, animate = false, modifier = Modifier.fillMaxSize())
            Text(
                text = emoji,
                fontSize = 30.sp,
                modifier = Modifier
                    .align(Alignment.Center)
            )
        }
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                maxLines = 3
            )
        }
    }
}

@Composable
private fun CreatingOverlay(visible: Boolean, companion: MagicalCompanion, childName: String?) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        val messages = remember(companion.id) {
            listOf(
                "Abrindo o livro mágico...",
                "${companion.name} está chamando os personagens...",
                "Pintando o cenário com palavras...",
                "Preparando a primeira escolha de ${childName ?: "você"}..."
            )
        }
        var index by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(2_400)
                index = (index + 1) % messages.size
            }
        }
        Surface(color = Color.Black.copy(alpha = 0.62f), modifier = Modifier.fillMaxSize()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.padding(30.dp)
                ) {
                    Column(modifier = Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CompanionAvatar(companion = companion, size = 92.dp)
                        Spacer(modifier = Modifier.height(16.dp))
                        AnimatedContent(targetState = messages[index], label = "creating") { text ->
                            Text(
                                text = text,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(
                            color = FairyGold,
                            modifier = Modifier
                                .width(190.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }
    }
}
