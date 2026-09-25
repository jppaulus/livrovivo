package com.livrovivo.app.presentation.literacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livrovivo.app.core.audio.AudioPlayerController
import com.livrovivo.app.core.literacy.LiteracyRules
import com.livrovivo.app.core.literacy.SoundPlayer
import com.livrovivo.app.core.literacy.VoiceClips
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.LiteracyKnowledge
import com.livrovivo.app.domain.model.LiteracyModule
import com.livrovivo.app.domain.model.LiteracyPhase
import com.livrovivo.app.domain.model.LiteracyTrail
import com.livrovivo.app.domain.model.PhaseProgress
import com.livrovivo.app.domain.model.Story
import com.livrovivo.app.domain.repository.BillingRepository
import com.livrovivo.app.domain.repository.EuLeioRepository
import com.livrovivo.app.domain.repository.LiteracyRepository
import com.livrovivo.app.domain.usecase.GetActiveChildUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PhaseUi(
    val phase: LiteracyPhase,
    val number: Int,
    val stars: Int,
    val isLocked: Boolean,
    /** Fase paga e a família não assina (fase já concluída antes continua aberta). */
    val needsSubscription: Boolean = false
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
    val knowledge: LiteracyKnowledge = LiteracyKnowledge(),
    /** Livros "Eu leio" da criança, do mais antigo para o mais novo. */
    val books: List<Story> = emptyList()
) {
    /** Livros ganhos em um módulo (o módulo fica em [Story.themeId]). */
    fun booksOf(moduleId: String): List<Story> = books.filter { it.themeId == moduleId }

    val totalPhases: Int get() = modules.sumOf { it.phases.size }
    val completedPhases: Int get() = modules.sumOf { it.completedPhases }

    fun module(id: String): ModuleUi? = modules.find { it.module.id == id }
    fun phase(id: String): PhaseUi? = modules.flatMap { it.phases }.find { it.phase.id == id }

    /**
     * Próxima fase liberada depois desta (pode ser do próximo módulo), ou null. Sem assinatura, pula as
     * fases pagas: depois de "Sílabas com C" vem "Palavras 1".
     */
    fun nextPhase(id: String): PhaseUi? {
        val all = modules.flatMap { it.phases }
        val index = all.indexOfFirst { it.phase.id == id }
        if (index < 0) return null
        return all.drop(index + 1).firstOrNull { !it.isLocked }
    }
}

/** Mapa da trilha, lista de fases e tela de resultado. */
@OptIn(ExperimentalCoroutinesApi::class)
class LiteracyViewModel(
    private val literacyRepository: LiteracyRepository,
    private val euLeioRepository: EuLeioRepository,
    private val billingRepository: BillingRepository,
    getActiveChildUseCase: GetActiveChildUseCase,
    private val audioPlayerController: AudioPlayerController,
    private val soundPlayer: SoundPlayer
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
            // Quem já tinha fases concluídas antes desta versão também recebe os livros que ganhou.
            euLeioRepository.requestEarnedBooks(child)
            combine(
                literacyRepository.observeProgress(child.id),
                euLeioRepository.observeBooks(child.id),
                billingRepository.isPremiumFlow
            ) { progress, books, subscriber ->
                buildState(child, trail, progress, subscriber).copy(books = books)
            }.collect { emit(it) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiteracyUiState())

    private fun buildState(child: ChildProfile, trail: LiteracyTrail, progress: List<PhaseProgress>, subscriber: Boolean): LiteracyUiState {
        val starsByPhase = progress.associate { it.phaseId to it.stars }
        val completed = LiteracyRules.completedPhaseIds(progress)
        val unlocked = LiteracyRules.unlockedPhaseIds(trail, completed, subscriber)
        val modules = trail.modules.map { module ->
            ModuleUi(
                module = module,
                phases = module.phases.mapIndexed { index, phase ->
                    PhaseUi(
                        phase = phase,
                        number = index + 1,
                        stars = starsByPhase[phase.id] ?: 0,
                        isLocked = phase.id !in unlocked,
                        needsSubscription = !LiteracyRules.canPlay(phase, subscriber) && phase.id !in completed
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

    /** Um livro "Eu leio" está sendo escrito (para o "Escrevendo..." do resultado). */
    val isWritingBook: StateFlow<Boolean> = euLeioRepository.isWriting

    private var speakJob: Job? = null

    /**
     * Fala as frases em sequência. Com todas gravadas ([VoiceClips]), toca os áudios prontos, na hora;
     * se faltar alguma, a voz do app lê tudo junto.
     */
    fun speak(vararg texts: String) {
        stopNarration()
        speakJob = viewModelScope.launch {
            if (texts.all { soundPlayer.hasClip(VoiceClips.Kind.PHRASE, it) }) {
                texts.forEach { soundPlayer.playClip(VoiceClips.Kind.PHRASE, it) }
                return@launch
            }
            val text = texts.joinToString(" ")
            val key = "literacy-screen#${text.hashCode()}"
            if (audioPlayerController.playbackState.value.chapterKey == key) audioPlayerController.replay()
            else audioPlayerController.load(key, text, null, "alegre", null, true)
        }
    }

    /**
     * Para só o que esta tela pediu: ao entrar numa fase, a lista de fases sai depois de a fase já ter
     * começado a falar, e parar o som de todo mundo cortava a instrução no "Toque...".
     */
    fun stopNarration() {
        speakJob?.cancel()
        if (audioPlayerController.playbackState.value.chapterKey?.startsWith("literacy-screen#") == true) {
            audioPlayerController.onReaderStopped()
        }
    }
}
