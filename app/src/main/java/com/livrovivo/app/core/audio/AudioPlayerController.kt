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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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
    private companion object {
        const val MAX_CACHE_BYTES = 300L * 1024 * 1024
        const val DUCKED_VOLUME = 0.18f
        const val AMBIENT_VOLUME = 0.5f
        /** Sem o primeiro pedaço da voz em partes neste tempo, volta ao caminho antigo (arquivo por parte). */
        const val FIRST_AUDIO_TIMEOUT_MS = 6_000L
        /** Letras por segundo de fala do Gemini (medido em 24/09/2026), para o destaque antes de saber a duração. */
        const val CHARS_PER_SECOND = 10.5
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

    /** Narração tocando enquanto chega (voz do Gemini em partes); null quando é o ExoPlayer que toca. */
    private var stream: StreamingPcmPlayer? = null

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
        if (sameChapter && loadJob?.isActive != true && (player?.mediaItemCount ?: 0) < parts.size) {
            startLoad(LoadParams(key, text, script, mood, listenerAge), autoPlay)
            return
        }
        if (sameChapter && status != NarrationStatus.ERROR && status != NarrationStatus.IDLE) {
            if (autoPlay && status == NarrationStatus.PREPARING) playWhenReady = true
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

            // 0) Voz do Gemini em partes: a página inteira num pedido só, tocando desde o primeiro pedaço (~1 s).
            if (shouldStream(settings) && streamPage(params, settings)) return@launch
            if (currentParams?.key != params.key) return@launch

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
            kotlinx.coroutines.supervisorScope {
                // Keep at most two requests in flight, preserving playback order and one voice.
                val ahead = mutableMapOf<Int, kotlinx.coroutines.Deferred<File>>()
                fun prepare(index: Int) {
                    if (index < pageParts.size) ahead[index] = async {
                        cachedOrSynthesize(engine, requestFor(params, pageParts, index), settings)
                    }
                }
                prepare(1)
                prepare(2)
                for (index in 1 until pageParts.size) {
                    if (currentParams?.key != params.key) { ahead.values.forEach { it.cancel() }; return@supervisorScope }
                    val partFile = try {
                        ahead.remove(index)!!.await()
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
                        ahead.values.forEach { it.cancel() }
                        return@supervisorScope
                    }
                    if (currentParams?.key != params.key) { ahead.values.forEach { it.cancel() }; return@supervisorScope }
                    enqueuePart(partFile)
                    prepare(index + 2)
                }
            }
        }
    }

    /** A voz em partes vale para o Gemini com chave, quando ele é o primeiro motor da vez. */
    private suspend fun shouldStream(settings: AppSettings): Boolean =
        orderedEngines(settings).firstOrNull() === geminiEngine && geminiEngine.canStream()

    /** A página inteira como uma parte só (a voz em partes narra tudo num pedido). */
    private fun wholePage(params: LoadParams): NarrationPart = NarrationPart(
        chunk = NarrationChunker.Chunk(params.text.indices, params.script ?: params.text),
        sentenceStart = 0,
        timeline = NarrationTimeline.build(params.text)
    )

    /**
     * Narra a página com a voz do Gemini em partes: toca desde o primeiro pedaço e guarda a página inteira
     * para ouvir de novo sem gerar outra vez. Devolve false se o primeiro pedaço não chegou a tempo (aí a
     * narração segue pelo caminho antigo).
     */
    private suspend fun streamPage(params: LoadParams, settings: AppSettings): Boolean {
        val request = NarrationRequest(
            text = params.text,
            script = params.script,
            persona = _playbackState.value.activePersona,
            mood = params.mood,
            listenerAge = params.listenerAge
        )
        val base = File(narrationDir, sha256("stream|" + geminiEngine.cacheSignature(request, settings) + "|" + (request.script ?: request.text)))
        val whole = wholePage(params)

        // Página já narrada antes: toca o arquivo guardado, sem gastar outra geração.
        val cached = withContext(Dispatchers.IO) {
            listOf("m4a", "wav").map { File("${base.path}.$it") }.firstOrNull { it.exists() && it.length() > 1_000 }
        }
        if (cached != null) {
            cached.setLastModified(System.currentTimeMillis())
            parts = listOf(whole)
            activeEngine = geminiEngine
            startPlayback(cached, EngineKind.GEMINI, null)
            return true
        }

        val started = System.nanoTime()
        return kotlinx.coroutines.coroutineScope {
            val chunks = Channel<ByteArray>(Channel.UNLIMITED)
            val firstRate = CompletableDeferred<Int>()
            val producer = launch(Dispatchers.IO) {
                val all = ByteArrayOutputStream()
                var rate = 24_000
                try {
                    geminiEngine.stream(request).collect { chunk ->
                        rate = chunk.sampleRate
                        all.write(chunk.pcm)
                        chunks.send(chunk.pcm)
                        if (!firstRate.isCompleted) firstRate.complete(rate)
                    }
                    chunks.close()
                    if (all.size() == 0) throw AiException(AiException.Kind.PARSE, "A voz não devolveu áudio")
                    val file = saveWholePage(all.toByteArray(), rate, base)
                    withContext(Dispatchers.Main) { if (currentParams?.key == params.key) currentFile = file }
                } catch (e: CancellationException) {
                    chunks.close()
                    throw e
                } catch (e: Throwable) {
                    chunks.close(e)
                    firstRate.completeExceptionally(e)
                    if (com.livrovivo.app.BuildConfig.DEBUG) android.util.Log.w("LivroVivoVoz", "Voz em partes falhou: ${friendly(e)}")
                }
            }
            val rate = withTimeoutOrNull(FIRST_AUDIO_TIMEOUT_MS) { runCatching { firstRate.await() }.getOrNull() }
            if (rate == null || currentParams?.key != params.key) {
                producer.cancel()
                return@coroutineScope false
            }
            if (com.livrovivo.app.BuildConfig.DEBUG) android.util.Log.d("LivroVivoPerf",
                "voice engine=GEMINI stream firstAudioMs=${(System.nanoTime() - started) / 1_000_000}")
            playStream(params, rate, chunks, whole)
            true
        }
    }

    /** Toca os pedaços conforme chegam, atualizando progresso e destaque, até o último ser ouvido. */
    private suspend fun playStream(params: LoadParams, rate: Int, chunks: ReceiveChannel<ByteArray>, whole: NarrationPart) =
        kotlinx.coroutines.coroutineScope {
            player?.stop()
            player?.clearMediaItems()
            val out = StreamingPcmPlayer(rate, _playbackState.value.speed)
            stream = out
            parts = listOf(whole)
            activeEngine = geminiEngine
            val estimate = (params.text.length / CHARS_PER_SECOND * rate).toLong()
            _playbackState.update {
                it.copy(
                    status = if (playWhenReady) NarrationStatus.PLAYING else NarrationStatus.READY,
                    engine = EngineKind.GEMINI,
                    notice = null,
                    error = null,
                    partIndex = 1,
                    partCount = 1
                )
            }
            applyDucking()
            val writer = launch(Dispatchers.IO) {
                var first = true
                try {
                    for (pcm in chunks) {
                        out.write(pcm) // bloqueia enquanto está pausado ou com o buffer cheio
                        if (first) {
                            first = false
                            if (playWhenReady) out.play()
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    // A conexão caiu no meio: termina no que já chegou e avisa os pais.
                    _playbackState.update { it.copy(notice = "Não consegui narrar o resto desta página (${friendly(e)}).") }
                }
            }
            try {
                while (isActive) {
                    val written = out.framesWritten
                    val played = out.framesPlayed.coerceAtMost(written)
                    val total = if (writer.isCompleted) written else maxOf(estimate, written)
                    val progress = if (total > 0) (played.toFloat() / total).coerceIn(0f, 1f) else 0f
                    _playbackState.update {
                        it.copy(
                            currentPositionMs = played * 1000 / rate,
                            durationMs = total * 1000 / rate,
                            playbackProgress = progress,
                            highlightedSentence = if (out.isPlaying || played > 0) whole.timeline.sentenceAt(progress) else -1
                        )
                    }
                    // Terminou: tudo chegou e o alto-falante tocou até o fim (com folga de 50 ms).
                    if (writer.isCompleted && written > 0 && played >= written - rate / 20) break
                    delay(100)
                }
                _playbackState.update {
                    it.copy(status = NarrationStatus.ENDED, playbackProgress = 1f, highlightedSentence = -1)
                }
            } finally {
                out.release()
                if (stream === out) stream = null
                applyDucking()
            }
        }

    /** Guarda a página narrada (AAC, ou WAV se o aparelho não codificar) para tocar de novo sem gerar. */
    private suspend fun saveWholePage(pcm: ByteArray, rate: Int, base: File): File = withContext(Dispatchers.IO) {
        val m4a = File(base.path + ".m4a")
        val file = try {
            AacEncoder.encode(pcm, rate, m4a)
            if (m4a.length() < 1_000) throw IllegalStateException("AAC vazio")
            m4a
        } catch (_: Exception) {
            m4a.delete()
            File(base.path + ".wav").also { WavWriter.write(it, pcm, rate) }
        }
        trimCache()
        file
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
            exo.playWhenReady = playWhenReady
        }
    }

    private suspend fun synthesizeWithFallback(
        request: NarrationRequest,
        settings: AppSettings,
        engines: List<NarrationEngine>
    ): Result<Triple<File, NarrationEngine, String?>> {
        var notice: String? = null
        var lastError: Throwable? = null
        val remoteDeadline = System.nanoTime() + 8_000_000_000L
        for (engine in engines) {
            if (!engine.isAvailable(settings)) continue
            try {
                val file = if (engine.kind == EngineKind.DEVICE) {
                    cachedOrSynthesize(engine, request, settings)
                } else {
                    val remainingMs = ((remoteDeadline - System.nanoTime()) / 1_000_000L).coerceAtLeast(1)
                    withTimeoutOrNull(remainingMs) { cachedOrSynthesize(engine, request, settings) }
                        ?: throw AiException(AiException.Kind.TIMEOUT, "Tempo inicial de voz excedido")
                }
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
        val started = System.nanoTime()
        val file = if (isDevice) engine.synthesize(request, settings, base) else {
            withTimeoutOrNull(20_000) { engine.synthesize(request, settings, base) }
                ?: throw AiException(AiException.Kind.TIMEOUT, "Tempo de voz excedido")
        }
        if (com.livrovivo.app.BuildConfig.DEBUG) android.util.Log.d("LivroVivoPerf",
            "voice engine=${engine.kind} part=${request.partIndex} elapsedMs=${(System.nanoTime() - started) / 1_000_000}")
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
        stream?.let { live ->
            when (state.status) {
                NarrationStatus.PLAYING -> {
                    live.pause()
                    _playbackState.update { it.copy(status = NarrationStatus.PAUSED) }
                }
                NarrationStatus.READY, NarrationStatus.PAUSED -> {
                    playWhenReady = true
                    live.play()
                    _playbackState.update { it.copy(status = NarrationStatus.PLAYING) }
                }
                NarrationStatus.PREPARING -> playWhenReady = !playWhenReady
                else -> Unit
            }
            applyDucking()
            return
        }
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
        val file = currentFile
        if (file == null) {
            currentParams?.let { startLoad(it, autoPlay = true) }
            return
        }
        val exo = player
        if (exo == null || exo.mediaItemCount == 0) {
            // A página tocou pela voz em partes: agora ela está guardada inteira num arquivo.
            playWhenReady = true
            startPlayback(file, activeEngine?.kind ?: EngineKind.GEMINI, null)
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
        stream?.setSpeed(value)
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

    /** Chamado ao sair do leitor. */
    fun onReaderStopped() {
        stop()
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
            if (!_playbackState.value.isAmbientSoundEnabled) return@launch
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
        stream?.release()
        stream = null
        stopProgressTracker()
        stopAmbient()
        player?.release()
        player = null
        deviceEngine.shutdown()
    }
}
