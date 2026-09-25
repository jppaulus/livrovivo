package com.livrovivo.app.core.literacy

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.livrovivo.app.core.audio.AudioPlayerController
import com.livrovivo.app.core.audio.NarrationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Voz da trilha: instruções, letras, sílabas, palavras e frases fixas.
 *
 * Toca a fala gravada antes com a voz do Gemini ([VoiceClips], em `assets/voz`) quando ela existe: começa na
 * hora e não precisa de internet. Quando ainda não foi gravada, usa a voz de narração do app (letras e
 * sílabas com a dica de pronúncia do [Pronunciation], "bá, de bala"). Assim a trilha funciona mesmo antes de
 * os áudios existirem.
 *
 * As funções só terminam quando o som acaba, para dar para encadear: "Toque na sílaba" + "bê".
 * Para parar um som, cancele a corrotina que o pediu: assim uma tela nunca corta o som de outra (não há um
 * "parar tudo" de propósito). Um som novo sempre interrompe o anterior.
 */
class SoundPlayer(
    private val context: Context,
    private val narrator: AudioPlayerController,
    private val clips: VoiceClips
) {
    private var player: MediaPlayer? = null

    /** A fala já foi gravada? */
    suspend fun hasClip(kind: VoiceClips.Kind, text: String): Boolean = clips.find(kind, text) != null

    /**
     * Toca a fala gravada e espera terminar.
     * @return false quando ela ainda não foi gravada (ou o arquivo não abriu): aí quem chamou usa a voz do app.
     */
    suspend fun playClip(kind: VoiceClips.Kind, text: String): Boolean {
        val path = clips.find(kind, text) ?: return false
        Log.d(TAG, "$text: áudio gravado ($path)")
        narrator.stop()
        try {
            return playAsset(path).also { Log.d(TAG, "$text: tocou até o fim") }
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.d(TAG, "$text: interrompido")
            throw e
        }
    }

    /**
     * Fala uma letra ou sílaba e espera terminar.
     * @param example palavra de exemplo para a voz do app ("bá, de bala"); não é usada com áudio gravado.
     */
    suspend fun play(unit: String, example: String? = null) {
        if (playClip(VoiceClips.unitKind(unit), unit)) return
        val text = Pronunciation.fallbackText(unit, example)
        Log.d(TAG, "$unit: sem áudio gravado, voz do app: \"$text\"")
        say(KEY_PREFIX + unit, unit, text)
    }

    /**
     * Fala um texto com a voz do app e espera terminar (com limite de tempo, para nunca travar a atividade).
     * [script] é o que a voz lê de fato; [text] é o que aparece para o destaque.
     */
    suspend fun say(key: String, text: String, script: String?) {
        stopClip()
        if (narrator.playbackState.value.chapterKey == key) narrator.replay()
        else narrator.load(key, text, script, "alegre", null, true)
        withTimeoutOrNull(START_TIMEOUT_MS) {
            narrator.playbackState.first { it.chapterKey == key && it.status in SPEAKING }
        } ?: return
        withTimeoutOrNull(SPEAK_TIMEOUT_MS) {
            narrator.playbackState.first { it.chapterKey != key || it.status !in SPEAKING }
        }
    }

    /** A narração com esta chave foi pedida pelo [SoundPlayer] (para as telas saberem o que parar). */
    fun isOwnKey(key: String?): Boolean = key?.startsWith(KEY_PREFIX) == true

    private suspend fun playAsset(path: String): Boolean = withContext(Dispatchers.Main) {
        stopClip()
        val media = try {
            context.assets.openFd(path).use { file ->
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    setDataSource(file.fileDescriptor, file.startOffset, file.length)
                    prepare()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Não abriu o áudio $path", e)
            return@withContext false
        }
        player = media
        suspendCancellableCoroutine { continuation ->
            fun finish() {
                if (player === media) stopClip()
                if (continuation.isActive) continuation.resume(true)
            }
            media.setOnCompletionListener { finish() }
            media.setOnErrorListener { _, _, _ -> finish(); true }
            continuation.invokeOnCancellation { if (player === media) stopClip() }
            // Cancelado enquanto abria o arquivo: o player já foi liberado acima.
            if (continuation.isActive) media.start()
        }
    }

    private fun stopClip() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }

    private companion object {
        const val TAG = "LivroVivoSom"
        const val KEY_PREFIX = "literacy-sound#"
        val SPEAKING = setOf(NarrationStatus.PREPARING, NarrationStatus.PLAYING)
        /** A voz do aparelho leva ~1 s para começar na primeira vez. */
        const val START_TIMEOUT_MS = 8_000L
        const val SPEAK_TIMEOUT_MS = 10_000L
    }
}
