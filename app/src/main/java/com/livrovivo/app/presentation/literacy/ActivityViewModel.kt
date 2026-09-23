package com.livrovivo.app.presentation.literacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livrovivo.app.core.audio.AudioPlayerController
import com.livrovivo.app.core.literacy.FeedbackSounds
import com.livrovivo.app.core.literacy.LiteracyRules
import com.livrovivo.app.core.literacy.Pronunciation
import com.livrovivo.app.core.literacy.SoundPlayer
import com.livrovivo.app.domain.model.VocabularyWord
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.repository.EuLeioRepository
import com.livrovivo.app.domain.repository.LiteracyRepository
import com.livrovivo.app.domain.usecase.GetActiveChildUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ActivityScreenState(
    val session: ActivityState? = null,
    val error: String? = null,
    /** Estrelas da fase, preenchido só depois que o resultado foi salvo (aí a tela pode sair). */
    val savedStars: Int? = null,
    /**
     * Quantos livros "Eu leio" a criança tinha antes desta fase, quando a fase liberou um livro novo
     * (-1 quando não liberou). O resultado mostra o aviso assim que o livro aparecer.
     */
    val booksBefore: Int = -1
)

/** Uma fase sendo jogada: as 5 perguntas, a narração de cada instrução e os sons de acerto e erro. */
class ActivityViewModel(
    private val phaseId: String,
    private val literacyRepository: LiteracyRepository,
    private val euLeioRepository: EuLeioRepository,
    private val getActiveChildUseCase: GetActiveChildUseCase,
    private val audioPlayerController: AudioPlayerController,
    private val feedbackSounds: FeedbackSounds,
    private val soundPlayer: SoundPlayer
) : ViewModel() {

    private val _state = MutableStateFlow(ActivityScreenState())
    val state: StateFlow<ActivityScreenState> = _state.asStateFlow()

    private var feedbackJob: Job? = null

    private var child: ChildProfile? = null

    /** Letras e sílabas da trilha (para achar a que fecha a instrução) e o vocabulário (exemplos). */
    private var units: Set<String> = emptySet()
    private var vocabulary: List<VocabularyWord> = emptyList()
    private var promptJob: Job? = null

    init {
        viewModelScope.launch {
            val child = getActiveChildUseCase.getDirect()
            val trail = runCatching { literacyRepository.getTrail() }.getOrNull()
            val phase = trail?.phase(phaseId)
            if (child == null || trail == null || phase == null || phase.questions.isEmpty()) {
                _state.value = ActivityScreenState(error = "Esta fase não foi encontrada.")
                return@launch
            }
            val completed = LiteracyRules.completedPhaseIds(literacyRepository.getProgress(child.id))
            if (phaseId !in LiteracyRules.unlockedPhaseIds(trail, completed)) {
                _state.value = ActivityScreenState(error = "Esta fase ainda está trancada. Termine as fases de antes!")
                return@launch
            }
            this@ActivityViewModel.child = child
            units = trail.modules.filter { it.id == LiteracyRules.MODULE_VOWELS || it.id == LiteracyRules.MODULE_CONSONANTS }
                .flatMap { module -> module.phases.flatMap { it.teaches } }.toSet() + LiteracyRules.allSyllables(trail)
            vocabulary = trail.vocabulary
            _state.value = ActivityScreenState(session = ActivitySession.start(phase))
            speakPrompt()
        }
    }

    fun chooseOption(option: String) = update { ActivitySession.chooseOption(it, option) }

    fun placePiece(pieceIndex: Int, slot: Int? = null) = update { ActivitySession.placePiece(it, pieceIndex, slot) }

    fun removeFromSlot(slot: Int) = update { ActivitySession.removeFromSlot(it, slot) }

    /**
     * Botão de alto-falante: repete a instrução da pergunta. Quando ela termina numa letra ou sílaba
     * ("Toque na sílaba BE"), a frase sai pela voz do app e o "BE" pelo [SoundPlayer]: áudio gravado,
     * ou a dica de pronúncia ("bê") enquanto os áudios não existirem.
     */
    fun speakPrompt() {
        val question = _state.value.session?.question ?: return
        val parts = Pronunciation.promptTarget(question.prompt, units)
        if (parts == null) {
            speak(narrationFor(question.prompt))
            return
        }
        stopPrompt()
        promptJob = viewModelScope.launch {
            soundPlayer.say(KEY_PREFIX + parts.carrier.hashCode(), parts.carrier, narrationFor(parts.carrier))
            soundPlayer.play(parts.target, example = Pronunciation.exampleWord(parts.target, vocabulary))
        }
    }

    private fun stopPrompt() {
        promptJob?.cancel()
        soundPlayer.stop()
    }

    fun stopNarration() {
        feedbackJob?.cancel()
        stopPrompt()
        val key = audioPlayerController.playbackState.value.chapterKey
        if (key?.startsWith(KEY_PREFIX) == true || soundPlayer.isOwnKey(key)) {
            audioPlayerController.onReaderStopped()
        }
    }

    private fun update(action: (ActivityState) -> ActivityState) {
        val before = _state.value.session ?: return
        val after = action(before)
        if (after == before) return
        _state.value = _state.value.copy(session = after)
        if (after.totalMistakes > before.totalMistakes) onTryAgain()
        else if (after.feedback == ActivityFeedback.CORRECT && before.feedback != ActivityFeedback.CORRECT) onCorrect()
    }

    private fun onCorrect() {
        feedbackSounds.playSuccess()
        speak("Muito bem!")
        feedbackJob?.cancel()
        feedbackJob = viewModelScope.launch {
            delay(CELEBRATION_MS)
            val next = ActivitySession.next(_state.value.session ?: return@launch)
            _state.value = _state.value.copy(session = next)
            if (next.finished) saveResult(next) else speakPrompt()
        }
    }

    /**
     * Guarda a melhor nota antes de a tela sair (a tela espera o [ActivityScreenState.savedStars]).
     * Se a fase liberou um livro "Eu leio", ele é escrito em segundo plano: com IA pode levar alguns
     * segundos, e a criança já vê as estrelas enquanto isso.
     */
    private suspend fun saveResult(session: ActivityState) {
        var booksBefore = -1
        child?.let { child ->
            runCatching { literacyRepository.recordAttempt(child.id, phaseId, session.stars, session.totalMistakes) }
            if (runCatching { euLeioRepository.pendingBooks(child) }.getOrDefault(0) > 0) {
                booksBefore = runCatching { euLeioRepository.observeBooks(child.id).first().size }.getOrDefault(0)
                euLeioRepository.requestEarnedBooks(child)
            }
        }
        _state.value = _state.value.copy(savedStars = session.stars, booksBefore = booksBefore)
    }

    private fun onTryAgain() {
        feedbackSounds.playTryAgain()
        speak("Tente de novo!")
        feedbackJob?.cancel()
        feedbackJob = viewModelScope.launch {
            delay(TRY_AGAIN_MS)
            _state.value.session?.let { _state.value = _state.value.copy(session = ActivitySession.clearFeedback(it)) }
        }
    }

    private fun speak(text: String) {
        stopPrompt()
        val key = KEY_PREFIX + text.hashCode()
        if (audioPlayerController.playbackState.value.chapterKey == key) audioPlayerController.replay()
        else audioPlayerController.load(key, text, null, "alegre", null, true)
    }

    override fun onCleared() {
        stopNarration()
        super.onCleared()
    }

    companion object {
        private const val KEY_PREFIX = "literacy-activity#"
        private const val CELEBRATION_MS = 1_400L
        private const val TRY_AGAIN_MS = 1_200L

        /**
         * A voz lê melhor sílabas em minúsculas ("forme ba" soa "bá"; "BA" pode sair soletrado).
         * A tela continua mostrando o texto original. A voz certa de letras e sílabas vem na etapa 7.
         */
        fun narrationFor(prompt: String): String = prompt.lowercase().replaceFirstChar { it.uppercase() }
    }
}
