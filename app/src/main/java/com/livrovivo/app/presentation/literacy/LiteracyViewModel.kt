package com.livrovivo.app.presentation.literacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livrovivo.app.core.audio.AudioPlayerController
import com.livrovivo.app.core.literacy.LiteracyRules
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.LiteracyKnowledge
import com.livrovivo.app.domain.model.LiteracyModule
import com.livrovivo.app.domain.model.LiteracyPhase
import com.livrovivo.app.domain.model.LiteracyTrail
import com.livrovivo.app.domain.model.PhaseProgress
import com.livrovivo.app.domain.repository.LiteracyRepository
import com.livrovivo.app.domain.usecase.GetActiveChildUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class PhaseUi(
    val phase: LiteracyPhase,
    val number: Int,
    val stars: Int,
    val isLocked: Boolean
) {
    val isSubscriberOnly: Boolean get() = !phase.isFree
}

data class ModuleUi(
    val module: LiteracyModule,
    val phases: List<PhaseUi>,
    val isLocked: Boolean
) {
    val stars: Int get() = phases.sumOf { it.stars }
    val maxStars: Int get() = phases.size * PhaseProgress.MAX_STARS
    val completedPhases: Int get() = phases.count { it.stars >= 1 }

    /** Símbolo do módulo no mapa, tirado do próprio conteúdo: a primeira letra, sílaba ou palavra ensinada. */
    val symbol: String get() = module.phases.firstOrNull()?.teaches?.firstOrNull() ?: "✏️"
}

data class LiteracyUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val child: ChildProfile? = null,
    val trail: LiteracyTrail? = null,
    val modules: List<ModuleUi> = emptyList(),
    val knowledge: LiteracyKnowledge = LiteracyKnowledge()
) {
    val totalPhases: Int get() = modules.sumOf { it.phases.size }
    val completedPhases: Int get() = modules.sumOf { it.completedPhases }

    fun module(id: String): ModuleUi? = modules.find { it.module.id == id }
    fun phase(id: String): PhaseUi? = modules.flatMap { it.phases }.find { it.phase.id == id }

    /** Fase seguinte na trilha (pode ser do próximo módulo), ou null no fim. */
    fun nextPhase(id: String): PhaseUi? {
        val all = modules.flatMap { it.phases }
        val index = all.indexOfFirst { it.phase.id == id }
        return all.getOrNull(index + 1)?.takeIf { index >= 0 }
    }
}

/** Mapa da trilha, lista de fases e tela de resultado. */
@OptIn(ExperimentalCoroutinesApi::class)
class LiteracyViewModel(
    private val literacyRepository: LiteracyRepository,
    getActiveChildUseCase: GetActiveChildUseCase,
    private val audioPlayerController: AudioPlayerController
) : ViewModel() {

    val uiState: StateFlow<LiteracyUiState> = getActiveChildUseCase().flatMapLatest { child ->
        if (child == null) return@flatMapLatest flowOf(LiteracyUiState(isLoading = false))
        flow {
            val trail = try {
                literacyRepository.getTrail()
            } catch (e: Exception) {
                emit(LiteracyUiState(isLoading = false, error = "Não foi possível abrir a trilha.", child = child))
                return@flow
            }
            literacyRepository.observeProgress(child.id)
                .map { progress -> buildState(child, trail, progress) }
                .collect { emit(it) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiteracyUiState())

    private fun buildState(child: ChildProfile, trail: LiteracyTrail, progress: List<PhaseProgress>): LiteracyUiState {
        val starsByPhase = progress.associate { it.phaseId to it.stars }
        val completed = LiteracyRules.completedPhaseIds(progress)
        val unlocked = LiteracyRules.unlockedPhaseIds(trail, completed)
        val modules = trail.modules.map { module ->
            ModuleUi(
                module = module,
                phases = module.phases.mapIndexed { index, phase ->
                    PhaseUi(
                        phase = phase,
                        number = index + 1,
                        stars = starsByPhase[phase.id] ?: 0,
                        isLocked = phase.id !in unlocked
                    )
                },
                isLocked = module.phases.none { it.id in unlocked }
            )
        }
        return LiteracyUiState(
            isLoading = false,
            child = child,
            trail = trail,
            modules = modules,
            knowledge = LiteracyRules.knowledge(trail, completed)
        )
    }

    fun speak(text: String) {
        val key = "literacy-screen#${text.hashCode()}"
        if (audioPlayerController.playbackState.value.chapterKey == key) audioPlayerController.replay()
        else audioPlayerController.load(key, text, null, "alegre", null, true)
    }

    fun stopNarration() {
        if (audioPlayerController.playbackState.value.chapterKey?.startsWith("literacy-screen#") == true) {
            audioPlayerController.onReaderStopped()
        }
    }
}
