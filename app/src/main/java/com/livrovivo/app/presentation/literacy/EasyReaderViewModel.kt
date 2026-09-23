package com.livrovivo.app.presentation.literacy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livrovivo.app.core.audio.AudioPlayerController
import com.livrovivo.app.core.literacy.FeedbackSounds
import com.livrovivo.app.core.literacy.LiteracyRules
import com.livrovivo.app.core.literacy.ReaderWord
import com.livrovivo.app.core.literacy.ReaderWords
import com.livrovivo.app.domain.model.Chapter
import com.livrovivo.app.domain.model.Story
import com.livrovivo.app.domain.model.VocabularyWord
import com.livrovivo.app.domain.repository.EuLeioRepository
import com.livrovivo.app.domain.repository.LiteracyRepository
import com.livrovivo.app.domain.repository.StoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Palavra que a criança segurou: mostra as sílabas (BO · LA) enquanto o narrador lê. */
data class SyllableSplit(val wordIndex: Int, val syllables: List<String>)

data class EasyReaderState(
    val story: Story? = null,
    val pageIndex: Int = 0,
    val words: List<ReaderWord> = emptyList(),
    /** Palavra principal da página no vocabulário (a figura dela ilustra a página). */
    val mainWord: VocabularyWord? = null,
    /** Páginas (índice do capítulo) que a criança marcou como "Li sozinho!". */
    val readAlone: Set<Int> = emptySet(),
    val tappedWord: Int? = null,
    /** Mantém a palavra tocada acesa por um tempo mínimo: palavras curtas soam rápido demais para a criança ver. */
    val tapHighlight: Boolean = false,
    val split: SyllableSplit? = null,
    val finished: Boolean = false,
    val error: String? = null
) {
    val pages: List<Chapter> get() = story?.sortedChapters.orEmpty()
    val chapter: Chapter? get() = pages.getOrNull(pageIndex)
    val pageCount: Int get() = pages.size
    val isLastPage: Boolean get() = pageIndex == pageCount - 1
    val pageReadAlone: Boolean get() = chapter?.index in readAlone
}

/**
 * Leitor "Eu leio": a criança tenta ler sozinha. Não há narração automática; o narrador só entra quando
 * ela pede (tocar numa palavra, segurar para ouvir as sílabas, ou "Ouvir a página").
 */
class EasyReaderViewModel(
    private val storyId: String,
    private val storyRepository: StoryRepository,
    private val literacyRepository: LiteracyRepository,
    private val euLeioRepository: EuLeioRepository,
    private val audioPlayerController: AudioPlayerController,
    private val feedbackSounds: FeedbackSounds
) : ViewModel() {

    private val _state = MutableStateFlow(EasyReaderState())
    val state: StateFlow<EasyReaderState> = _state.asStateFlow()

    val playback = audioPlayerController.playbackState

    private val sessionStart = System.currentTimeMillis()
    private var vocabulary: Map<String, VocabularyWord> = emptyMap()
    private var syllables: Set<String> = emptySet()

    init {
        // Sem onReaderStarted(): ele liga a música ambiente das aventuras, que atrapalha quem lê sozinho.
        viewModelScope.launch {
            val story = storyRepository.getStoryById(storyId)
            if (story == null || story.chapters.isEmpty()) {
                _state.value = EasyReaderState(error = "Este livro não foi encontrado.")
                return@launch
            }
            runCatching { literacyRepository.getTrail() }.getOrNull()?.let { trail ->
                vocabulary = trail.vocabulary.associateBy { it.word }
                syllables = LiteracyRules.allSyllables(trail)
            }
            val readAlone = runCatching { euLeioRepository.pagesReadAlone(storyId) }.getOrDefault(emptySet())
            // Livro já lido começa do início; livro pela metade continua de onde parou.
            val start = if (story.isCompleted) 0 else story.sortedChapters.indexOfFirst { it.index == story.lastReadChapter }
            _state.value = EasyReaderState(story = story, readAlone = readAlone)
            showPage(start.coerceAtLeast(0))
        }
    }

    // --- Palavras ---

    /** Tocar numa palavra: o narrador lê só ela, e ela fica destacada enquanto soa. */
    fun tapWord(index: Int) {
        val word = _state.value.words.getOrNull(index) ?: return
        _state.update { it.copy(tappedWord = index, split = null) }
        holdHighlight()
        speak(wordKey(word.word), word.word, word.word.lowercase())
    }

    /** Tocar e segurar: separa em sílabas (BO · LA), lê cada uma e depois a palavra inteira. */
    fun holdWord(index: Int) {
        val word = _state.value.words.getOrNull(index) ?: return
        val parts = word.syllables ?: return tapWord(index)
        _state.update { it.copy(tappedWord = index, split = SyllableSplit(index, parts)) }
        holdHighlight()
        val spoken = parts.joinToString(", ") { it.lowercase() } + "... " + word.word.lowercase()
        speak(splitKey(word.word), word.word, spoken)
    }

    private var highlightJob: Job? = null

    private fun holdHighlight() {
        highlightJob?.cancel()
        _state.update { it.copy(tapHighlight = true) }
        highlightJob = viewModelScope.launch {
            delay(MIN_HIGHLIGHT_MS)
            _state.update { it.copy(tapHighlight = false) }
        }
    }

    /** "Ouvir a página": narra a página inteira, com o destaque de frase do leitor normal. */
    fun listenPage() {
        val chapter = _state.value.chapter ?: return
        _state.update { it.copy(tappedWord = null, split = null) }
        speak(pageKey(chapter), chapter.content, chapter.content.lowercase())
    }

    /** "Li sozinho!": registra a página (para as conquistas e o painel dos pais). Não é uma avaliação. */
    fun readAlone() {
        val state = _state.value
        val chapter = state.chapter ?: return
        val story = state.story ?: return
        if (chapter.index in state.readAlone) return
        feedbackSounds.playSuccess()
        _state.update { it.copy(readAlone = it.readAlone + chapter.index) }
        viewModelScope.launch {
            runCatching { euLeioRepository.recordPageReadAlone(story.id, chapter.index, story.childId) }
        }
    }

    // --- Páginas ---

    fun nextPage() {
        val state = _state.value
        if (state.isLastPage) finish() else showPage(state.pageIndex + 1)
    }

    fun previousPage() {
        val state = _state.value
        if (state.pageIndex > 0) showPage(state.pageIndex - 1)
    }

    /** "Quer ler de novo?" */
    fun readAgain() {
        _state.update { it.copy(finished = false) }
        showPage(0)
    }

    private fun finish() {
        val story = _state.value.story ?: return
        stopNarration()
        _state.update { it.copy(finished = true, tappedWord = null, split = null) }
        viewModelScope.launch { runCatching { euLeioRepository.markBookFinished(story.id) } }
        val name = story.childSnapshot?.name?.let { " $it" }.orEmpty()
        speak("eu-leio-end#${story.id}", "Parabéns$name! Você leu o livro todo! Quer ler de novo?", null)
    }

    private fun showPage(index: Int) {
        val story = _state.value.story ?: return
        val chapter = story.sortedChapters.getOrNull(index) ?: return
        stopNarration()
        val childName = story.childSnapshot?.name.orEmpty()
        _state.update {
            it.copy(
                pageIndex = index,
                words = ReaderWords.of(chapter.content, childName, syllables),
                mainWord = chapter.newWords.firstOrNull()?.let { word -> vocabulary[word] },
                tappedWord = null,
                split = null
            )
        }
        viewModelScope.launch { runCatching { storyRepository.markRead(story.id, chapter.index) } }
    }

    // --- Narração ---

    fun pageKey(chapter: Chapter) = "eu-leio-page#$storyId#${chapter.index}"
    fun wordKey(word: String) = "eu-leio-word#$word"
    fun splitKey(word: String) = "eu-leio-split#$word"

    private fun speak(key: String, text: String, script: String?) {
        if (audioPlayerController.playbackState.value.chapterKey == key) audioPlayerController.replay()
        else audioPlayerController.load(key, text, script, "alegre", null, true)
    }

    fun stopNarration() {
        if (audioPlayerController.playbackState.value.chapterKey?.startsWith("eu-leio-") == true) {
            audioPlayerController.stop()
        }
    }

    private companion object {
        const val MIN_HIGHLIGHT_MS = 1_200L
    }

    override fun onCleared() {
        stopNarration()
        super.onCleared()
        val story = _state.value.story ?: return
        val duration = System.currentTimeMillis() - sessionStart
        // viewModelScope já foi cancelado aqui: registra a sessão em um escopo próprio.
        CoroutineScope(Dispatchers.IO).launch {
            storyRepository.recordReadingSession(story.id, story.childId, sessionStart, duration)
        }
    }
}
