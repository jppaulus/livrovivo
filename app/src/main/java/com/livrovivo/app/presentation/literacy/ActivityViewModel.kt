package com.livrovivo.app.presentation.literacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livrovivo.app.core.audio.AudioPlayerController
import com.livrovivo.app.core.literacy.FeedbackSounds
import com.livrovivo.app.domain.repository.LiteracyRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ActivityScreenState(
    val session: ActivityState? = null,
    val error: String? = null
)

/** Uma fase sendo jogada: as 5 perguntas, a narração de cada instrução e os sons de acerto e erro. */
class ActivityViewModel(
    private val phaseId: String,
    private val literacyRepository: LiteracyRepository,
    private val audioPlayerController: AudioPlayerController,
    private val feedbackSounds: FeedbackSounds
) : ViewModel() {

    private val _state = MutableStateFlow(ActivityScreenState())
    val state: StateFlow<ActivityScreenState> = _state.asStateFlow()

    private var feedbackJob: Job? = null

    init {
        viewModelScope.launch {
            val phase = runCatching { literacyRepository.getTrail().phase(phaseId) }.getOrNull()
            if (phase == null || phase.questions.isEmpty()) {
                _state.value = ActivityScreenState(error = "Esta fase não foi encontrada.")
                return@launch
            }
            _state.value = ActivityScreenState(session = ActivitySession.start(phase))
            speakPrompt()
        }
    }

    fun chooseOption(option: String) = update { ActivitySession.chooseOption(it, option) }

    fun placePiece(pieceIndex: Int, slot: Int? = null) = update { ActivitySession.placePiece(it, pieceIndex, slot) }

    fun removeFromSlot(slot: Int) = update { ActivitySession.removeFromSlot(it, slot) }

    /** Botão de alto-falante: repete a instrução da pergunta. */
    fun speakPrompt() {
        val question = _state.value.session?.question ?: return
        speak(narrationFor(question.prompt))
    }

    fun stopNarration() {
        feedbackJob?.cancel()
        if (audioPlayerController.playbackState.value.chapterKey?.startsWith(KEY_PREFIX) == true) {
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
            if (!next.finished) speakPrompt()
        }
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
