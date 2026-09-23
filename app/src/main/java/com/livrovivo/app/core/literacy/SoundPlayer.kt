package com.livrovivo.app.core.literacy

import android.annotation.SuppressLint
import android.content.Context
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
 * Voz das letras e sílabas da trilha.
 *
 * Toca o áudio gravado (`res/raw/som_ba`, gerado por `ferramentas/gerar_audios.py`) quando ele existe.
 * Quando não existe, usa a voz do app com a dica de pronúncia do [Pronunciation] ("bá, de bala"). Assim a
 * trilha funciona desde o primeiro dia, mesmo antes de os áudios existirem.
 *
 * As funções só terminam quando o som acaba, para dar para encadear: "Toque na sílaba" + "bê".
 */
class SoundPlayer(
    private val context: Context,
    private val narrator: AudioPlayerController
) {
    private var player: MediaPlayer? = null

    /** Existe áudio gravado para esta letra ou sílaba? */
    @SuppressLint("DiscouragedApi")
    fun rawId(unit: String): Int =
        context.resources.getIdentifier(Pronunciation.resourceName(unit), "raw", context.packageName)

    /**
     * Fala uma letra ou sílaba e espera terminar.
     * @param example palavra de exemplo para a voz do app ("bá, de bala"); não é usada com áudio gravado.
     */
    suspend fun play(unit: String, example: String? = null) {
        val id = rawId(unit)
        if (id != 0) {
            Log.d(TAG, "$unit: áudio gravado")
            narrator.stop()
            playRaw(id)
        } else {
            val text = Pronunciation.fallbackText(unit, example)
            Log.d(TAG, "$unit: sem áudio gravado, voz do app: \"$text\"")
            say(KEY_PREFIX + unit, unit, text)
        }
    }

    /**
     * Fala um texto com a voz do app e espera terminar (com limite de tempo, para nunca travar a atividade).
     * [script] é o que a voz lê de fato; [text] é o que aparece para o destaque.
     */
    suspend fun say(key: String, text: String, script: String?) {
        stopRaw()
        if (narrator.playbackState.value.chapterKey == key) narrator.replay()
        else narrator.load(key, text, script, "alegre", null, true)
        withTimeoutOrNull(START_TIMEOUT_MS) {
            narrator.playbackState.first { it.chapterKey == key && it.status in SPEAKING }
        } ?: return
        withTimeoutOrNull(SPEAK_TIMEOUT_MS) {
            narrator.playbackState.first { it.chapterKey != key || it.status !in SPEAKING }
        }
    }

    /** Para o som gravado que estiver tocando (a voz do app se para pelo próprio narrador). */
    fun stop() = stopRaw()

    /** A narração com esta chave foi pedida pelo [SoundPlayer] (para as telas saberem o que parar). */
    fun isOwnKey(key: String?): Boolean = key?.startsWith(KEY_PREFIX) == true

    private suspend fun playRaw(id: Int) = withContext(Dispatchers.Main) {
        stopRaw()
        val media = MediaPlayer.create(context, id) ?: return@withContext
        player = media
        suspendCancellableCoroutine { continuation ->
            media.setOnCompletionListener {
                if (player === media) stopRaw()
                if (continuation.isActive) continuation.resume(Unit)
            }
            continuation.invokeOnCancellation { if (player === media) stopRaw() }
            media.start()
        }
    }

    private fun stopRaw() {
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
