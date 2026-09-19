package com.livrovivo.app.presentation.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.livrovivo.app.core.ai.AiException
import com.livrovivo.app.core.album.StickerBook
import com.livrovivo.app.core.audio.Ambience
import com.livrovivo.app.core.audio.AudioPlayerController
import com.livrovivo.app.core.audio.PlaybackState
import com.livrovivo.app.core.audio.VoicePersona
import com.livrovivo.app.core.bedtime.BedtimeMode
import com.livrovivo.app.core.bedtime.minuteTicks
import com.livrovivo.app.core.settings.SettingsManager
import com.livrovivo.app.domain.model.AgeGroup
import com.livrovivo.app.domain.model.Chapter
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.Choice
import com.livrovivo.app.domain.model.MagicalCompanion
import com.livrovivo.app.domain.model.SceneKind
import com.livrovivo.app.domain.model.Sticker
import com.livrovivo.app.domain.model.Story
import com.livrovivo.app.domain.model.ThemeOption
import com.livrovivo.app.domain.repository.StoryRepository
import com.livrovivo.app.domain.usecase.ContinueStoryUseCase
import com.livrovivo.app.domain.usecase.DeleteStoryUseCase
import com.livrovivo.app.domain.usecase.GetActiveChildUseCase
import com.livrovivo.app.domain.usecase.GetChildProfilesUseCase
import com.livrovivo.app.domain.usecase.GetStoryByIdUseCase
import com.livrovivo.app.domain.usecase.IllustrateChapterUseCase
import com.livrovivo.app.domain.usecase.RewindStoryUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class IllustrationStatus { IDLE, PAINTING, READY, FAILED, DISABLED }

data class ReaderUiState(
    val isLoading: Boolean = true,
    val story: Story? = null,
    val child: ChildProfile? = null,
    val pageIndex: Int = 1,
    val isGeneratingNextChapter: Boolean = false,
    val pendingChoice: String? = null,
    val illustrationStatus: Map<Int, IllustrationStatus> = emptyMap(),
    val illustrationNotice: String? = null,
    val errorMessage: String? = null,
    val canRetry: Boolean = false,
    val highlightReading: Boolean = true,
    val showCelebration: Boolean = false,
    val narration: PlaybackState = PlaybackState(),
    /** Na hora de dormir, o fim da história convida para o boa-noite (ou só permite ele). */
    val bedtimeMode: BedtimeMode = BedtimeMode.OFF,
    val storiesTonight: Int = 0,
    /** Figurinhas que esta história acabou de colar no álbum (vazio ao reler um final). */
    val newStickers: List<Sticker> = emptyList()
) {
    val chapters: List<Chapter> get() = story?.sortedChapters.orEmpty()
    val currentChapter: Chapter? get() = chapters.find { it.index == pageIndex }
    val lastIndex: Int get() = chapters.maxOfOrNull { it.index } ?: 1
    val isLatestPage: Boolean get() = pageIndex == lastIndex
    val totalPages: Int get() = maxOf(story?.plannedChapters ?: 1, lastIndex)
    val companion: MagicalCompanion get() = MagicalCompanion.findById(story?.companionId)
    val scene: SceneKind get() = ThemeOption.sceneFor(story?.themeId, story?.theme.orEmpty())
    val childName: String get() = child?.name ?: "Pequeno Leitor"
}

class ReaderViewModel(
    private val storyId: String,
    private val getStoryByIdUseCase: GetStoryByIdUseCase,
    private val continueStoryUseCase: ContinueStoryUseCase,
    private val rewindStoryUseCase: RewindStoryUseCase,
    private val illustrateChapterUseCase: IllustrateChapterUseCase,
    private val getActiveChildUseCase: GetActiveChildUseCase,
    private val getChildProfilesUseCase: GetChildProfilesUseCase,
    private val storyRepository: StoryRepository,
    private val settingsManager: SettingsManager,
    val audioPlayerController: AudioPlayerController,
    private val deleteStoryUseCase: DeleteStoryUseCase,
    private val stickerBook: StickerBook
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private val sessionStart = System.currentTimeMillis()
    private var lastChoice: Choice? = null
    private val illustrationJobs = mutableMapOf<Int, Job>()
    private var autoPlay = true
    private var pageInitialized = false
    private var deleted = false

    init {
        // O início/fim do áudio acompanha a tela (DisposableEffect em ReaderScreen).
        observeNarration()
        observeStory()
        observeBedtime()
    }

    /**
     * Modo da noite para a criança dona da história. Recalcula quando uma história termina
     * (a contagem muda) e a cada minuto (a hora de dormir pode começar com o leitor aberto).
     */
    private fun observeBedtime() {
        viewModelScope.launch {
            val childId = getStoryByIdUseCase(storyId)?.childId ?: return@launch
            combine(settingsManager.settingsFlow, minuteTicks()) { settings, _ -> settings }
                .collect { settings ->
                    _uiState.update {
                        it.copy(
                            bedtimeMode = settings.bedtimeModeFor(childId),
                            storiesTonight = settings.storiesTonightFor(childId)
                        )
                    }
                }
        }
    }

    private fun observeStory() {
        viewModelScope.launch {
            val settings = settingsManager.current()
            autoPlay = settings.autoPlayNarration
            _uiState.update { it.copy(highlightReading = settings.highlightReading) }

            getStoryByIdUseCase.observe(storyId).collect { story ->
                if (story == null) {
                    if (!deleted) _uiState.update { it.copy(isLoading = false, errorMessage = "História não encontrada.") }
                    return@collect
                }
                val firstLoad = !pageInitialized
                if (firstLoad) {
                    // A dona da história, não a criança ativa: com irmãos cadastrados o leitor
                    // mostraria o nome errado e narraria na idade errada.
                    val child = getChildProfilesUseCase.byId(story.childId)
                        ?: getActiveChildUseCase.getDirect()
                    _uiState.update { it.copy(child = child) }
                }
                _uiState.update { state ->
                    val page = if (firstLoad) {
                        story.lastReadChapter.coerceIn(1, story.lastChapter?.index ?: 1)
                    } else {
                        state.pageIndex.coerceAtMost(story.lastChapter?.index ?: 1)
                    }
                    state.copy(isLoading = false, story = story, pageIndex = page)
                }
                if (firstLoad) {
                    pageInitialized = true
                    onPageShown(autoPlay)
                }
            }
        }
    }

    private fun observeNarration() {
        viewModelScope.launch {
            audioPlayerController.playbackState.collect { playback ->
                _uiState.update { it.copy(narration = playback) }
            }
        }
    }

    /** Prepara narração e ilustração da página visível. */
    private fun onPageShown(play: Boolean) {
        val state = _uiState.value
        val story = state.story ?: return
        val chapter = state.currentChapter ?: return
        audioPlayerController.load(
            key = "${story.id}#${chapter.index}#${chapter.content.hashCode()}",
            text = chapter.content,
            script = chapter.narrationScript,
            mood = chapter.mood,
            listenerAge = AgeGroup.fromCode(state.child?.ageGroup).illustrationAge + " child",
            autoPlay = play
        )
        // O som de fundo acompanha o clima da página (grilos, vento, ondas...).
        audioPlayerController.setPageAmbience(Ambience.forPage(chapter.mood, state.scene))
        ensureIllustration(chapter.index)
        viewModelScope.launch { storyRepository.markRead(story.id, chapter.index) }
        if (chapter.isEnding) {
            _uiState.update { it.copy(showCelebration = true) }
            collectStickers(story)
        }
    }

    /**
     * Cola no álbum o que esta história trouxe. Voltar ao final depois não apaga a novidade
     * da tela (a segunda chamada não devolve nada novo, e a primeira lista é mantida).
     */
    private fun collectStickers(story: Story) {
        viewModelScope.launch {
            val fresh = stickerBook.collectAfterEnding(story.childId, story.id)
            if (fresh.isNotEmpty()) _uiState.update { it.copy(newStickers = fresh) }
        }
    }

    private fun ensureIllustration(chapterIndex: Int) {
        val chapter = _uiState.value.chapters.find { it.index == chapterIndex } ?: return
        if (chapter.imagePath != null) {
            setIllustrationStatus(chapterIndex, IllustrationStatus.READY)
            return
        }
        if (illustrationJobs[chapterIndex]?.isActive == true) return
        illustrationJobs[chapterIndex] = viewModelScope.launch {
            setIllustrationStatus(chapterIndex, IllustrationStatus.PAINTING)
            illustrateChapterUseCase(storyId, chapterIndex)
                .onSuccess { setIllustrationStatus(chapterIndex, IllustrationStatus.READY) }
                .onFailure { error ->
                    val notConfigured = error is AiException && error.kind == AiException.Kind.NOT_CONFIGURED
                    setIllustrationStatus(chapterIndex, if (notConfigured) IllustrationStatus.DISABLED else IllustrationStatus.FAILED)
                    if (!notConfigured && error is AiException) {
                        _uiState.update { it.copy(illustrationNotice = "Ilustração indisponível: ${error.friendlyMessage}") }
                    }
                }
        }
    }

    private fun setIllustrationStatus(chapterIndex: Int, status: IllustrationStatus) {
        _uiState.update { it.copy(illustrationStatus = it.illustrationStatus + (chapterIndex to status)) }
    }

    fun retryIllustration() {
        val index = _uiState.value.pageIndex
        _uiState.update { it.copy(illustrationNotice = null) }
        illustrationJobs[index]?.cancel()
        ensureIllustration(index)
    }

    fun goToPage(index: Int, play: Boolean = autoPlay) {
        val state = _uiState.value
        if (index == state.pageIndex || state.chapters.none { it.index == index }) return
        audioPlayerController.stop()
        _uiState.update { it.copy(pageIndex = index, errorMessage = null, showCelebration = false) }
        onPageShown(play)
    }

    fun nextPage() = goToPage(_uiState.value.pageIndex + 1)

    fun previousPage() = goToPage(_uiState.value.pageIndex - 1)

    fun selectChoice(choice: Choice) {
        val state = _uiState.value
        val chapter = state.currentChapter ?: return
        if (state.isGeneratingNextChapter || !state.isLatestPage || chapter.isEnding) return

        lastChoice = choice
        audioPlayerController.stop()
        _uiState.update {
            it.copy(isGeneratingNextChapter = true, pendingChoice = choice.text, errorMessage = null, canRetry = false)
        }

        viewModelScope.launch {
            val result = continueStoryUseCase(storyId, chapter.index, choice)
            result.onSuccess { next ->
                // Aguarda o banco refletir a nova página antes de mudar de página.
                val updated = getStoryByIdUseCase.observe(storyId)
                    .first { s -> s?.chapters?.any { it.index == next.index } == true }
                _uiState.update {
                    it.copy(story = updated, isGeneratingNextChapter = false, pendingChoice = null, pageIndex = next.index)
                }
                onPageShown(true)
            }.onFailure { error ->
                val message = when (error) {
                    is AiException -> "${error.friendlyMessage} Toque em tentar de novo."
                    else -> error.message ?: "Não foi possível criar o próximo capítulo."
                }
                _uiState.update {
                    it.copy(isGeneratingNextChapter = false, pendingChoice = null, errorMessage = message, canRetry = true)
                }
            }
        }
    }

    fun retryLastChoice() {
        lastChoice?.let { selectChoice(it) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null, canRetry = false) }
    }

    /** Volta para a página [chapterIndex], apagando as seguintes, para escolher outro caminho. */
    fun chooseAnotherPath(chapterIndex: Int) {
        audioPlayerController.stop()
        illustrationJobs.filterKeys { it > chapterIndex }.values.forEach { it.cancel() }
        viewModelScope.launch {
            rewindStoryUseCase(storyId, chapterIndex)
            _uiState.update {
                it.copy(
                    pageIndex = chapterIndex,
                    showCelebration = false,
                    illustrationStatus = it.illustrationStatus.filterKeys { key -> key <= chapterIndex }
                )
            }
            onPageShown(false)
        }
    }

    fun restartStory() {
        _uiState.update { it.copy(showCelebration = false) }
        if (_uiState.value.pageIndex == 1) audioPlayerController.replay() else goToPage(1, play = true)
    }

    fun toggleAudio() = audioPlayerController.togglePlayPause()

    fun replayNarration() = audioPlayerController.replay()

    fun setPersona(persona: VoicePersona) = audioPlayerController.setPersona(persona)

    fun cycleBackgroundSound() = audioPlayerController.cycleBackgroundSound()

    fun setSpeed(speed: Float) = audioPlayerController.setSpeed(speed)

    fun dismissIllustrationNotice() {
        _uiState.update { it.copy(illustrationNotice = null) }
    }

    /** Apaga a história aberta (com ilustrações) e avisa a tela para fechar o livro. */
    fun deleteStory(onDeleted: () -> Unit) {
        if (deleted) return
        deleted = true
        audioPlayerController.onReaderStopped()
        illustrationJobs.values.forEach { it.cancel() }
        viewModelScope.launch {
            deleteStoryUseCase(storyId)
            onDeleted()
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (deleted) return
        val state = _uiState.value
        val story = state.story ?: return
        val duration = System.currentTimeMillis() - sessionStart
        // viewModelScope já foi cancelado aqui: registra a sessão em um escopo próprio.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            storyRepository.recordReadingSession(story.id, story.childId, sessionStart, duration)
        }
    }
}
