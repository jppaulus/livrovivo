package com.livrovivo.app.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.livrovivo.app.core.ai.AiException
import com.livrovivo.app.core.settings.AppSettings
import com.livrovivo.app.core.settings.SettingsManager
import com.livrovivo.app.core.settings.VoiceEngineChoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

enum class NarrationStatus { IDLE, PREPARING, READY, PLAYING, PAUSED, ENDED, ERROR }

data class PlaybackState(
    val chapterKey: String? = null,
    val status: NarrationStatus = NarrationStatus.IDLE,
    val activePersona: VoicePersona = VoicePersona.AVENTUREIRO,
    val engine: EngineKind? = null,
    val notice: String? = null,
    val error: String? = null,
    val playbackProgress: Float = 0f,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val highlightedSentence: Int = -1,
    val speed: Float = 1f,
    val isAmbientSoundEnabled: Boolean = false,
    /** A página é narrada em partes para começar rápido: parte atual e total. */
    val partIndex: Int = 1,
    val partCount: Int = 1
) {
    val isPlaying: Boolean get() = status == NarrationStatus.PLAYING
    val isLoadingAudio: Boolean get() = status == NarrationStatus.PREPARING
    val isChunked: Boolean get() = partCount > 1
}

/**
 * Orquestra a narração: escolhe o melhor motor de voz disponível (com fallback),
 * guarda o áudio no aparelho para ouvir de novo offline, toca com ExoPlayer e
 * calcula a frase que está sendo lida para o destaque na tela.
 */
class AudioPlayerController(
    private val context: Context,
    private val settingsManager: SettingsManager,
    private val elevenLabsEngine: ElevenLabsNarrationEngine,
    private val geminiEngine: GeminiNarrationEngine,
    private val deviceEngine: DeviceNarrationEngine
) {
    companion object {
        /** Falas do ritual de dormir usam chaves com este prefixo. */
        const val BEDTIME_KEY_PREFIX = "bedtime#"

        private const val MAX_CACHE_BYTES = 300L * 1024 * 1024
        private const val DUCKED_VOLUME = 0.18f
        private const val AMBIENT_VOLUME = 0.5f
    }

    private data class LoadParams(
        val key: String,
        val text: String,
        val script: String?,
        val mood: String?,
        val listenerAge: String?
    )

    /** Uma parte da página: o áudio dela e quais frases do texto ela cobre (para o destaque). */
    private data class NarrationPart(
        val chunk: NarrationChunker.Chunk,
        val sentenceStart: Int,
        val timeline: NarrationTimeline
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val narrationDir = File(context.filesDir, "narration").apply { mkdirs() }
    private val deviceCacheDir = File(context.cacheDir, "narration_device").apply { mkdirs() }

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var player: ExoPlayer? = null
    private var loadJob: Job? = null
    private var progressJob: Job? = null
    private var currentParams: LoadParams? = null
    private var currentFile: File? = null
    private var playWhenReady = false
    private var initialized = false

    private var parts: List<NarrationPart> = emptyList()
    private var queuedParts = 0
    private var activeEngine: NarrationEngine? = null

    private var ambientTrack: AudioTrack? = null
    private var ambientJob: Job? = null
    private var lullabyPcm: ShortArray? = null

    /** Lê preferências salvas (persona, velocidade, música) uma única vez. */
    private suspend fun ensureInitialized() {
        if (initialized) return
        val settings = settingsManager.current()
        _playbackState.update {
            it.copy(
                activePersona = VoicePersona.fromId(settings.defaultPersonaId),
                speed = settings.narrationSpeed,
                isAmbientSoundEnabled = settings.ambientMusicEnabled
            )
        }
        initialized = true
    }

    private fun obtainPlayer(): ExoPlayer {
        player?.let { return it }
        val attributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()
        val exo = ExoPlayer.Builder(context)
            .setAudioAttributes(attributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    _playbackState.update { it.copy(status = NarrationStatus.PLAYING, error = null) }
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                    _playbackState.update { state ->
                        if (state.status == NarrationStatus.PLAYING) state.copy(status = NarrationStatus.PAUSED) else state
                    }
                }
                applyDucking()
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> _playbackState.update {
                        it.copy(
                            durationMs = exo.duration.coerceAtLeast(0L),
                            status = if (it.status == NarrationStatus.PREPARING) NarrationStatus.READY else it.status
                        )
                    }
                    Player.STATE_ENDED -> {
                        stopProgressTracker()
                        // Se ainda faltam partes sendo geradas, continua "preparando" em vez de terminar.
                        val waitingForNextPart = exo.mediaItemCount < parts.size
                        _playbackState.update {
                            if (waitingForNextPart) {
                                it.copy(status = NarrationStatus.PREPARING)
                            } else {
                                it.copy(status = NarrationStatus.ENDED, playbackProgress = 1f, highlightedSentence = -1)
                            }
                        }
                        applyDucking()
                    }
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                currentFile?.delete()
                currentFile = null
                _playbackState.update {
                    it.copy(status = NarrationStatus.ERROR, error = "Não foi possível tocar a narração.")
                }
            }
        })
        exo.setPlaybackParameters(PlaybackParameters(_playbackState.value.speed))
        player = exo
        return exo
    }

    /**
     * Prepara (e opcionalmente toca) a narração de uma página.
     * Chamadas repetidas para a mesma página não geram o áudio de novo.
     */
    fun load(
        key: String,
        text: String,
        script: String?,
        mood: String?,
        listenerAge: String?,
        autoPlay: Boolean
    ) {
        if (text.isBlank()) return
        val sameChapter = currentParams?.key == key
        val status = _playbackState.value.status
        if (sameChapter && status != NarrationStatus.ERROR && status != NarrationStatus.IDLE) {
            if (autoPlay && (status == NarrationStatus.READY || status == NarrationStatus.PAUSED)) player?.play()
            return
        }
        startLoad(LoadParams(key, text, script, mood, listenerAge), autoPlay)
    }

    private fun startLoad(params: LoadParams, autoPlay: Boolean) {
        loadJob?.cancel()
        stopProgressTracker()
        player?.stop()
        player?.clearMediaItems()
        currentParams = params
        currentFile = null
        activeEngine = null
        queuedParts = 0
        parts = buildParts(params)
        playWhenReady = autoPlay
        _playbackState.update {
            it.copy(
                chapterKey = params.key,
                status = NarrationStatus.PREPARING,
                engine = null,
                notice = null,
                error = null,
                playbackProgress = 0f,
                currentPositionMs = 0L,
                durationMs = 0L,
                highlightedSentence = -1,
                partIndex = 1,
                partCount = parts.size.coerceAtLeast(1)
            )
        }

        loadJob = scope.launch {
            ensureInitialized()
            val settings = settingsManager.current()
            val pageParts = parts
            if (pageParts.isEmpty()) return@launch

            // 1) Só a primeira parte é esperada: a criança ouve em poucos segundos.
            val first = synthesizeWithFallback(requestFor(params, pageParts, 0), settings, orderedEngines(settings))
            if (currentParams?.key != params.key) return@launch
            val (file, engine, notice) = first.getOrElse { error ->
                _playbackState.update {
                    it.copy(status = NarrationStatus.ERROR, error = "Não foi possível narrar: ${friendly(error)}")
                }
                return@launch
            }
            activeEngine = engine
            startPlayback(file, engine.kind, notice)

            // 2) O resto da página é gerado em segundo plano e entra na fila do player.
            for (index in 1 until pageParts.size) {
                if (currentParams?.key != params.key) return@launch
                val partFile = try {
                    cachedOrSynthesize(engine, requestFor(params, pageParts, index), settings)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    if (currentParams?.key == params.key) {
                        // Encerra a página nas partes que já tocaram, senão o player ficaria esperando para sempre.
                        parts = pageParts.take(index)
                        _playbackState.update {
                            it.copy(
                                notice = "Não consegui narrar o resto desta página (${friendly(e)}).",
                                partCount = index.coerceAtLeast(1),
                                status = if (it.status == NarrationStatus.PREPARING) NarrationStatus.ENDED else it.status
                            )
                        }
                    }
                    return@launch
                }
                if (currentParams?.key != params.key) return@launch
                enqueuePart(partFile)
            }
        }
    }

    /** Divide a página em partes e associa cada uma às frases que ela cobre. */
    private fun buildParts(params: LoadParams): List<NarrationPart> {
        val chunks = NarrationChunker.chunk(params.text, params.script)
        if (chunks.isEmpty()) return emptyList()
        val sentences = NarrationTimeline.build(params.text).sentences
        return chunks.map { chunk ->
            val displayed = params.text.substring(chunk.displayRange.first, chunk.displayRange.last + 1)
            NarrationPart(
                chunk = chunk,
                sentenceStart = sentences.count { it.first < chunk.displayRange.first },
                timeline = NarrationTimeline.build(displayed)
            )
        }
    }

    private fun requestFor(params: LoadParams, pageParts: List<NarrationPart>, index: Int): NarrationRequest {
        val part = pageParts[index]
        return NarrationRequest(
            text = params.text.substring(part.chunk.displayRange.first, part.chunk.displayRange.last + 1),
            script = part.chunk.speakText,
            persona = _playbackState.value.activePersona,
            mood = params.mood,
            listenerAge = params.listenerAge,
            partIndex = index + 1,
            partCount = pageParts.size,
            previousText = pageParts.getOrNull(index - 1)?.chunk?.speakText,
            nextText = pageParts.getOrNull(index + 1)?.chunk?.speakText
        )
    }

    /** Coloca a parte pronta na fila; se o player já terminou esperando por ela, retoma. */
    private fun enqueuePart(file: File) {
        val exo = player ?: return
        exo.addMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        queuedParts++
        val waiting = _playbackState.value.status == NarrationStatus.PREPARING && exo.mediaItemCount > 1
        if (waiting) {
            exo.seekTo(exo.mediaItemCount - 1, 0L)
            exo.play()
        }
    }

    private suspend fun synthesizeWithFallback(
        request: NarrationRequest,
        settings: AppSettings,
        engines: List<NarrationEngine>
    ): Result<Triple<File, NarrationEngine, String?>> {
        var notice: String? = null
        var lastError: Throwable? = null
        for (engine in engines) {
            if (!engine.isAvailable(settings)) continue
            try {
                val file = cachedOrSynthesize(engine, request, settings)
                if (engine.kind == EngineKind.DEVICE && notice == null && settings.voiceEngine != VoiceEngineChoice.DEVICE &&
                    !settings.hasGeminiKey && !settings.hasElevenLabsKey
                ) {
                    notice = "Dica para os pais: ative a IA na Área dos Pais para uma narração natural."
                }
                return Result.success(Triple(file, engine, notice))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastError = e
                val notConfigured = e is AiException && e.kind == AiException.Kind.NOT_CONFIGURED
                if (engine.kind != EngineKind.DEVICE && !notConfigured && notice == null) {
                    notice = "Voz ${engine.kind.label} indisponível (${friendly(e)}). Usando outra voz."
                }
            }
        }
        return Result.failure(lastError ?: IllegalStateException("Nenhuma voz disponível"))
    }

    private suspend fun cachedOrSynthesize(engine: NarrationEngine, request: NarrationRequest, settings: AppSettings): File {
        val isDevice = engine.kind == EngineKind.DEVICE
        val dir = if (isDevice) deviceCacheDir else narrationDir
        val base = File(dir, sha256(engine.cacheSignature(request, settings) + "|" + (request.script ?: request.text)))
        // A voz do aparelho é rápida e pode mudar (novas vozes instaladas): sempre gera de novo.
        if (!isDevice) {
            withContext(Dispatchers.IO) {
                listOf("m4a", "mp3", "wav").map { File("${base.path}.$it") }.firstOrNull { it.exists() && it.length() > 1_000 }
            }?.let { cached ->
                cached.setLastModified(System.currentTimeMillis())
                return cached
            }
        }
        val file = engine.synthesize(request, settings, base)
        withContext(Dispatchers.IO) { trimCache() }
        return file
    }

    private fun orderedEngines(settings: AppSettings): List<NarrationEngine> = when (settings.voiceEngine) {
        VoiceEngineChoice.AUTO -> listOf(elevenLabsEngine, geminiEngine, deviceEngine)
        VoiceEngineChoice.ELEVENLABS -> listOf(elevenLabsEngine, deviceEngine)
        VoiceEngineChoice.GEMINI -> listOf(geminiEngine, deviceEngine)
        VoiceEngineChoice.DEVICE -> listOf(deviceEngine)
    }.filter { engine ->
        // No modo automático, a ElevenLabs só entra se houver chave (ou backend) configurada.
        engine.kind != EngineKind.ELEVENLABS || settings.voiceEngine != VoiceEngineChoice.AUTO ||
            settings.hasElevenLabsKey || com.livrovivo.app.BuildConfig.SUPABASE_URL.isNotBlank()
    }

    private fun startPlayback(file: File, kind: EngineKind, notice: String?) {
        val exo = obtainPlayer()
        currentFile = file
        exo.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        exo.setPlaybackParameters(PlaybackParameters(_playbackState.value.speed))
        exo.prepare()
        exo.playWhenReady = playWhenReady
        _playbackState.update {
            it.copy(
                status = if (playWhenReady) NarrationStatus.PLAYING else NarrationStatus.READY,
                engine = kind,
                notice = notice,
                error = null
            )
        }
    }

    fun togglePlayPause() {
        val state = _playbackState.value
        val exo = player
        when (state.status) {
            NarrationStatus.PLAYING -> exo?.pause()
            NarrationStatus.READY, NarrationStatus.PAUSED -> exo?.play()
            NarrationStatus.ENDED -> replay()
            NarrationStatus.PREPARING -> playWhenReady = !playWhenReady
            NarrationStatus.ERROR, NarrationStatus.IDLE -> currentParams?.let { startLoad(it, autoPlay = true) }
        }
    }

    fun replay() {
        val exo = player ?: return
        if (currentFile == null) {
            currentParams?.let { startLoad(it, autoPlay = true) }
            return
        }
        exo.seekTo(0, 0L)
        exo.play()
    }

    /** Para a narração (ao sair do leitor ou trocar de página). */
    fun stop() {
        loadJob?.cancel()
        playWhenReady = false
        player?.pause()
        stopProgressTracker()
        _playbackState.update {
            if (it.status == NarrationStatus.PREPARING || it.status == NarrationStatus.PLAYING) {
                it.copy(status = if (currentFile != null) NarrationStatus.PAUSED else NarrationStatus.IDLE)
            } else {
                it
            }
        }
        if (currentFile == null) currentParams = null
        applyDucking()
    }

    fun setPersona(persona: VoicePersona) {
        if (_playbackState.value.activePersona == persona) return
        val wasActive = _playbackState.value.status in setOf(NarrationStatus.PLAYING, NarrationStatus.PREPARING)
        _playbackState.update { it.copy(activePersona = persona) }
        scope.launch { settingsManager.setDefaultPersona(persona.id) }
        currentParams?.let { startLoad(it, autoPlay = wasActive || playWhenReady) }
    }

    fun setSpeed(speed: Float) {
        val value = speed.coerceIn(0.7f, 1.3f)
        _playbackState.update { it.copy(speed = value) }
        player?.setPlaybackParameters(PlaybackParameters(value))
        scope.launch { settingsManager.setNarrationSpeed(value) }
    }

    fun toggleAmbientSound() = setAmbientEnabled(!_playbackState.value.isAmbientSoundEnabled)

    fun setAmbientEnabled(enabled: Boolean) {
        _playbackState.update { it.copy(isAmbientSoundEnabled = enabled) }
        scope.launch { settingsManager.setAmbientMusic(enabled) }
        if (enabled) startAmbient() else stopAmbient()
    }

    /** Chamado ao entrar no leitor: aplica preferências, aquece a voz do aparelho e retoma a música. */
    fun onReaderStarted() {
        scope.launch {
            ensureInitialized()
            if (_playbackState.value.isAmbientSoundEnabled) startAmbient()
        }
        // Inicializar o motor de voz do Android leva ~1s: faz isso antes de a criança tocar em play.
        scope.launch { deviceEngine.prewarm() }
    }

    /**
     * Chamado ao sair do leitor. A navegação desmonta o leitor depois que a tela seguinte já
     * começou, então o ritual de dormir pode estar tocando: nesse caso ele não é interrompido.
     */
    fun onReaderStopped() {
        if (currentParams?.key?.startsWith(BEDTIME_KEY_PREFIX) != true) stop()
        if (!bedtimeMusic) stopAmbient()
    }

    // --- Ritual de dormir --------------------------------------------------------------------------

    /** A caixinha toca no ritual mesmo com a música de fundo desligada, sem mudar a preferência. */
    private var bedtimeMusic = false
    private var bedtimeFadeJob: Job? = null

    fun startBedtimeMusic() {
        bedtimeMusic = true
        bedtimeFadeJob?.cancel()
        startAmbient()
    }

    /** Deixa a caixinha tocar por [holdMs], abaixa até o silêncio em [fadeMs] e desliga. */
    fun fadeOutBedtimeMusic(holdMs: Long, fadeMs: Long) {
        bedtimeFadeJob?.cancel()
        bedtimeFadeJob = scope.launch {
            delay(holdMs)
            val steps = 30
            repeat(steps) { step ->
                try {
                    ambientTrack?.setVolume(AMBIENT_VOLUME * (1f - (step + 1f) / steps))
                } catch (_: Exception) {
                }
                delay(fadeMs / steps)
            }
            stopBedtimeMusic()
        }
    }

    fun stopBedtimeMusic() {
        bedtimeFadeJob?.cancel()
        bedtimeFadeJob = null
        bedtimeMusic = false
        stopAmbient()
    }

    /** Nome técnico da voz do aparelho usada por último (diagnóstico nas configurações). */
    val lastDeviceVoiceName: String? get() = deviceEngine.lastVoiceName

    /** Apaga todos os áudios de narração salvos (usado ao apagar todas as histórias). */
    suspend fun clearNarrationCache() = withContext(Dispatchers.IO) {
        narrationDir.listFiles()?.forEach { it.delete() }
        deviceCacheDir.listFiles()?.forEach { it.delete() }
    }

    /** Toca uma amostra da persona com um motor específico (tela de configurações). */
    suspend fun previewVoice(persona: VoicePersona, engineKind: EngineKind): Result<EngineKind> {
        ensureInitialized()
        val settings = settingsManager.current()
        val engine = when (engineKind) {
            EngineKind.ELEVENLABS -> elevenLabsEngine
            EngineKind.GEMINI -> geminiEngine
            EngineKind.DEVICE -> deviceEngine
        }
        val sample = "[warmly] Oi! Eu sou ${if (persona == VoicePersona.URSINHO || persona == VoicePersona.AVENTUREIRO) "o" else "a"} ${persona.title}. " +
            "[excited] Hoje vamos viver uma história mágica juntos, cheia de estrelas e surpresas!"
        val request = NarrationRequest(
            text = com.livrovivo.app.core.ai.ChapterSanitizer.stripAudioTags(sample),
            script = sample,
            persona = persona,
            mood = "alegre"
        )
        return try {
            loadJob?.cancel()
            val file = cachedOrSynthesize(engine, request, settings)
            currentParams = null
            parts = emptyList()
            queuedParts = 0
            playWhenReady = true
            startPlayback(file, engine.kind, null)
            Result.success(engine.kind)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                val exo = player ?: break
                val duration = exo.duration.takeIf { it > 0 } ?: 0L
                val position = exo.currentPosition.coerceAtLeast(0L)
                val withinPart = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                val partIndex = exo.currentMediaItemIndex.coerceAtLeast(0)
                val part = parts.getOrNull(partIndex)
                // Progresso e destaque consideram a parte atual dentro da página inteira.
                val progress = if (parts.size > 1) {
                    ((partIndex + withinPart) / parts.size).coerceIn(0f, 1f)
                } else {
                    withinPart
                }
                val sentence = part?.let { it.sentenceStart + it.timeline.sentenceAt(withinPart) } ?: -1
                _playbackState.update {
                    it.copy(
                        currentPositionMs = position,
                        durationMs = duration,
                        playbackProgress = progress,
                        highlightedSentence = sentence,
                        partIndex = partIndex + 1,
                        partCount = parts.size.coerceAtLeast(1)
                    )
                }
                delay(150)
            }
        }
    }

    private fun stopProgressTracker() {
        progressJob?.cancel()
        progressJob = null
    }

    // --- Música de ninar ---------------------------------------------------------------------------

    private fun startAmbient() {
        if (ambientTrack != null || ambientJob?.isActive == true) {
            applyDucking()
            return
        }
        ambientJob = scope.launch {
            val pcm = lullabyPcm ?: withContext(Dispatchers.Default) { LullabySynth.render() }.also { lullabyPcm = it }
            if (!_playbackState.value.isAmbientSoundEnabled && !bedtimeMusic) return@launch
            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(LullabySynth.SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.setLoopPoints(0, pcm.size, -1)
                ambientTrack = track
                applyDucking()
                track.play()
            } catch (_: Exception) {
                ambientTrack = null
            }
        }
    }

    private fun stopAmbient() {
        ambientJob?.cancel()
        ambientJob = null
        try {
            ambientTrack?.stop()
        } catch (_: Exception) {
        }
        ambientTrack?.release()
        ambientTrack = null
    }

    /** Abaixa a música enquanto o narrador fala. */
    private fun applyDucking() {
        val volume = if (_playbackState.value.status == NarrationStatus.PLAYING) DUCKED_VOLUME else AMBIENT_VOLUME
        try {
            ambientTrack?.setVolume(volume)
        } catch (_: Exception) {
        }
    }

    // --- Utilidades --------------------------------------------------------------------------------

    private fun trimCache() {
        val files = narrationDir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        for (file in files) {
            if (total <= MAX_CACHE_BYTES) break
            total -= file.length()
            file.delete()
        }
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(40)

    private fun friendly(error: Throwable?): String = when (error) {
        is AiException -> error.friendlyMessage
        null -> "erro desconhecido"
        else -> error.message ?: "erro desconhecido"
    }

    fun release() {
        loadJob?.cancel()
        stopProgressTracker()
        stopAmbient()
        player?.release()
        player = null
        deviceEngine.shutdown()
    }
}
