package com.livrovivo.app.presentation.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livrovivo.app.core.theme.FairyGold
import com.livrovivo.app.core.ui.BouncyCardButton
import com.livrovivo.app.core.ui.CompanionAvatar
import com.livrovivo.app.core.ui.MagicalSparklesEffect
import com.livrovivo.app.domain.model.AgeGroup
import com.livrovivo.app.domain.model.AppearanceOption
import com.livrovivo.app.domain.model.ChildAppearance
import com.livrovivo.app.domain.model.ChildGender
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.MagicalCompanion
import com.livrovivo.app.domain.usecase.GetActiveChildUseCase
import com.livrovivo.app.domain.usecase.SaveChildProfileUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Para que serve esta passagem pelo formulário: primeiro uso, irmão novo ou edição. */
enum class OnboardingMode { FIRST_RUN, ADD_CHILD, EDIT }

data class OnboardingUiState(
    val step: Int = 0,
    val mode: OnboardingMode = OnboardingMode.FIRST_RUN,
    val existingProfile: ChildProfile? = null,
    val childName: String = "",
    val gender: ChildGender = ChildGender.NEUTRAL,
    val selectedAgeGroup: AgeGroup = AgeGroup.KID,
    val appearance: ChildAppearance = ChildAppearance(),
    val selectedCompanion: MagicalCompanion = MagicalCompanion.ALL.first(),
    val selectedInterests: Set<String> = setOf("dinossauros", "espaço"),
    val isSaved: Boolean = false
) {
    val canContinue: Boolean get() = step != 0 || childName.isNotBlank()
    val isLastStep: Boolean get() = step == OnboardingViewModel.STEP_COUNT - 1
    val isEditMode: Boolean get() = mode == OnboardingMode.EDIT
}

class OnboardingViewModel(
    private val saveChildProfileUseCase: SaveChildProfileUseCase,
    private val getActiveChildUseCase: GetActiveChildUseCase,
    mode: OnboardingMode
) : ViewModel() {

    companion object {
        const val STEP_COUNT = 5
    }

    private val _uiState = MutableStateFlow(OnboardingUiState(mode = mode))
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    val availableInterests = listOf(
        "dinossauros" to "🦖 Dinossauros",
        "espaço" to "🚀 Espaço",
        "animais" to "🦁 Animais",
        "fadas" to "🧚 Fadas e magia",
        "carros" to "🚗 Carros e trens",
        "natureza" to "🌿 Natureza",
        "super-heróis" to "🦸 Super-heróis",
        "música" to "🎵 Música e dança",
        "mar" to "🐠 Fundo do mar",
        "robôs" to "🤖 Robôs",
        "princesas e castelos" to "🏰 Castelos",
        "esportes" to "⚽ Esportes"
    )

    init {
        if (mode == OnboardingMode.EDIT) {
            viewModelScope.launch {
                getActiveChildUseCase.getDirect()?.let { profile ->
                    _uiState.update {
                        it.copy(
                            existingProfile = profile,
                            childName = profile.name,
                            gender = profile.gender,
                            selectedAgeGroup = AgeGroup.fromCode(profile.ageGroup),
                            appearance = profile.appearance,
                            selectedCompanion = MagicalCompanion.findById(profile.companionId),
                            selectedInterests = profile.interests.toSet()
                        )
                    }
                }
            }
        }
    }

    fun onNameChange(name: String) = _uiState.update { it.copy(childName = name.take(30)) }
    fun onGenderSelect(gender: ChildGender) = _uiState.update { it.copy(gender = gender) }
    fun onAgeGroupSelect(ageGroup: AgeGroup) = _uiState.update { it.copy(selectedAgeGroup = ageGroup) }
    fun onCompanionSelect(companion: MagicalCompanion) = _uiState.update { it.copy(selectedCompanion = companion) }

    fun onSkinTone(code: String) = _uiState.update {
        it.copy(appearance = it.appearance.copy(skinTone = code.takeIf { c -> c != it.appearance.skinTone }))
    }

    fun onHairColor(code: String) = _uiState.update {
        it.copy(appearance = it.appearance.copy(hairColor = code.takeIf { c -> c != it.appearance.hairColor }))
    }

    fun onHairStyle(code: String) = _uiState.update {
        it.copy(appearance = it.appearance.copy(hairStyle = code.takeIf { c -> c != it.appearance.hairStyle }))
    }

    fun onGlasses(value: Boolean) = _uiState.update { it.copy(appearance = it.appearance.copy(wearsGlasses = value)) }

    fun toggleInterest(interestKey: String) = _uiState.update {
        val current = it.selectedInterests.toMutableSet()
        if (!current.remove(interestKey)) current.add(interestKey)
        it.copy(selectedInterests = current)
    }

    fun next() {
        val state = _uiState.value
        if (!state.canContinue) return
        if (state.isLastStep) saveProfile() else _uiState.update { it.copy(step = it.step + 1) }
    }

    fun back() = _uiState.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }

    private fun saveProfile() {
        val state = _uiState.value
        val name = state.childName.trim().ifEmpty { "Pequeno Explorador" }
        viewModelScope.launch {
            val existing = state.existingProfile
            val profile = ChildProfile(
                id = existing?.id ?: UUID.randomUUID().toString(),
                name = name,
                ageGroup = state.selectedAgeGroup.code,
                interests = state.selectedInterests.toList(),
                companionId = state.selectedCompanion.id,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                gender = state.gender,
                appearance = state.appearance
            )
            saveChildProfileUseCase(profile)
            _uiState.update { it.copy(isSaved = true) }
        }
    }
}

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onFinished: () -> Unit,
    onCancel: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onFinished()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(modifier = Modifier.fillMaxSize()) {
            MagicalSparklesEffect()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 22.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                        repeat(OnboardingViewModel.STEP_COUNT) { index ->
                            Box(
                                modifier = Modifier
                                    .height(8.dp)
                                    .width(if (index == uiState.step) 28.dp else 12.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        if (index <= uiState.step) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
                                    )
                            )
                        }
                    }
                    if (onCancel != null) {
                        TextButton(onClick = onCancel) { Text("Cancelar") }
                    }
                }

                AnimatedContent(
                    targetState = uiState.step,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInHorizontally { it / 2 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 2 } + fadeOut())
                        } else {
                            (slideInHorizontally { -it / 2 } + fadeIn()) togetherWith (slideOutHorizontally { it / 2 } + fadeOut())
                        }
                    },
                    label = "onboardingStep",
                    modifier = Modifier.weight(1f)
                ) { step ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 18.dp)
                    ) {
                        when (step) {
                            0 -> NameStep(uiState, viewModel)
                            1 -> AgeStep(uiState, viewModel)
                            2 -> AppearanceStep(uiState, viewModel)
                            3 -> CompanionStep(uiState, viewModel)
                            else -> InterestsStep(uiState, viewModel)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.step > 0) {
                        TextButton(onClick = viewModel::back) { Text("Voltar") }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (uiState.step == 2) {
                        TextButton(onClick = viewModel::next) { Text("Pular") }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    BouncyCardButton(
                        onClick = viewModel::next,
                        enabled = uiState.canContinue,
                        containerColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) {
                        Text(
                            text = when {
                                !uiState.isLastStep -> "Continuar"
                                uiState.mode == OnboardingMode.EDIT -> "Salvar alterações ✨"
                                uiState.mode == OnboardingMode.ADD_CHILD -> "Criar a estante ✨"
                                else -> "Abrir o Livro Vivo ✨"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontSize = 18.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepTitle(emoji: String, title: String, subtitle: String) {
    Text(text = emoji, fontSize = 44.sp)
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary
    )
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NameStep(uiState: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepTitle(
        emoji = "🪄📖",
        title = when (uiState.mode) {
            OnboardingMode.EDIT -> "Editar perfil"
            OnboardingMode.ADD_CHILD -> "Quem mais vai ler?"
            OnboardingMode.FIRST_RUN -> "Bem-vindo ao Livro Vivo!"
        },
        subtitle = when (uiState.mode) {
            OnboardingMode.ADD_CHILD ->
                "Cada criança ganha a própria estante, com histórias e personalização só dela."
            else ->
                "Histórias mágicas em que a criança é a protagonista e decide o que acontece."
        }
    )
    OutlinedTextField(
        value = uiState.childName,
        onValueChange = viewModel::onNameChange,
        label = { Text("Como se chama o pequeno leitor?") },
        placeholder = { Text("Ex.: Leo, Sofia, Arthur...") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(20.dp))
    Text("Nas histórias, é:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChildGender.entries.forEach { gender ->
            FilterChip(
                selected = uiState.gender == gender,
                onClick = { viewModel.onGenderSelect(gender) },
                label = { Text(gender.label) },
                shape = RoundedCornerShape(14.dp)
            )
        }
    }
    Text(
        text = "Usamos isso só para as palavras e ilustrações combinarem com a criança.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        modifier = Modifier.padding(top = 6.dp)
    )
}

@Composable
private fun AgeStep(uiState: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepTitle(
        emoji = "🎂",
        title = "Quantos anos tem ${uiState.childName.ifBlank { "a criança" }}?",
        subtitle = "O tamanho dos capítulos e o vocabulário se adaptam à idade."
    )
    AgeGroup.entries.forEach { ageGroup ->
        val selected = uiState.selectedAgeGroup == ageGroup
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
            ),
            border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable { viewModel.onAgeGroupSelect(ageGroup) }
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = ageGroup.emoji, fontSize = 32.sp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = ageGroup.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${ageGroup.description} Histórias de ${ageGroup.plannedChapters} páginas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    )
                }
                if (selected) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceStep(uiState: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepTitle(
        emoji = "🎨",
        title = "Como desenhar ${uiState.childName.ifBlank { "a criança" }}?",
        subtitle = "Opcional: assim as ilustrações ficam parecidas com a criança."
    )
    Text("Tom de pele", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    SwatchRow(ChildAppearance.SKIN_TONES, uiState.appearance.skinTone, viewModel::onSkinTone)
    Spacer(modifier = Modifier.height(16.dp))
    Text("Cor do cabelo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    SwatchRow(ChildAppearance.HAIR_COLORS, uiState.appearance.hairColor, viewModel::onHairColor)
    Spacer(modifier = Modifier.height(16.dp))
    Text("Tipo de cabelo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChildAppearance.HAIR_STYLES.forEach { option ->
            FilterChip(
                selected = uiState.appearance.hairStyle == option.code,
                onClick = { viewModel.onHairStyle(option.code) },
                label = { Text(option.label) },
                shape = RoundedCornerShape(14.dp)
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("Usa óculos 👓", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Switch(checked = uiState.appearance.wearsGlasses, onCheckedChange = viewModel::onGlasses)
    }
}

@Composable
private fun SwatchRow(options: List<AppearanceOption>, selected: String?, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
        options.forEach { option ->
            val isSelected = option.code == selected
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(option.color))
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f),
                            shape = CircleShape
                        )
                        .clickable { onSelect(option.code) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) Text("✓", color = Color.White, fontWeight = FontWeight.ExtraBold)
                }
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(56.dp)
                )
            }
        }
    }
}

@Composable
private fun CompanionStep(uiState: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepTitle(
        emoji = "✨",
        title = "Escolha um companheiro mágico",
        subtitle = "Ele participa de todas as aventuras e conversa com a criança."
    )
    MagicalCompanion.ALL.forEach { companion ->
        val selected = uiState.selectedCompanion.id == companion.id
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
            ),
            border = if (selected) BorderStroke(2.dp, FairyGold) else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clickable { viewModel.onCompanionSelect(companion) }
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                CompanionAvatar(companion = companion, size = 60.dp, animated = selected)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = companion.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text(text = companion.title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = companion.personality,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InterestsStep(uiState: OnboardingUiState, viewModel: OnboardingViewModel) {
    StepTitle(
        emoji = "💫",
        title = "Do que ${uiState.childName.ifBlank { "a criança" }} mais gosta?",
        subtitle = "Os interesses aparecem nas histórias de um jeito surpreendente."
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        viewModel.availableInterests.forEach { (key, label) ->
            FilterChip(
                selected = uiState.selectedInterests.contains(key),
                onClick = { viewModel.toggleInterest(key) },
                label = { Text(label, fontSize = 16.sp) },
                shape = RoundedCornerShape(14.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }
    }
}
