package com.livrovivo.app.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.PlaybackParams

/**
 * Toca áudio PCM (16-bit mono) enquanto ele ainda está chegando: é o que faz a narração do Gemini começar em
 * cerca de 1 s, em vez de esperar a página inteira ser gerada.
 *
 * [write] bloqueia quando o buffer está cheio (ou pausado): chame numa thread de fundo.
 */
class StreamingPcmPlayer(val sampleRate: Int, speed: Float) {

    private val track: AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        .setAudioFormat(
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        // Meio segundo de folga: absorve a variação da rede sem atrasar o começo.
        .setBufferSizeInBytes(
            maxOf(
                AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT),
                sampleRate
            )
        )
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()

    @Volatile
    var framesWritten: Long = 0L
        private set

    @Volatile
    private var released = false

    init {
        setSpeed(speed)
    }

    /** Amostras já tocadas (a posição do alto-falante). */
    val framesPlayed: Long
        get() = if (released) framesWritten else track.playbackHeadPosition.toLong() and 0xFFFFFFFFL

    fun write(pcm: ByteArray) {
        if (released) return
        var offset = 0
        while (offset < pcm.size && !released) {
            val written = try {
                track.write(pcm, offset, pcm.size - offset)
            } catch (_: IllegalStateException) {
                -1 // liberado em outra thread (a criança saiu da página)
            }
            if (written <= 0) break
            offset += written
        }
        framesWritten += offset / 2
    }

    fun play() {
        if (!released) track.play()
    }

    fun pause() {
        if (!released) track.pause()
    }

    val isPlaying: Boolean get() = !released && track.playState == AudioTrack.PLAYSTATE_PLAYING

    fun setSpeed(speed: Float) {
        if (released) return
        try {
            track.playbackParams = PlaybackParams().setSpeed(speed)
        } catch (_: Exception) {
            // Alguns aparelhos não aceitam mudar a velocidade de um AudioTrack: toca na velocidade normal.
        }
    }

    fun setVolume(volume: Float) {
        if (!released) track.setVolume(volume)
    }

    fun release() {
        if (released) return
        released = true
        try {
            track.pause()
            track.flush()
            track.release()
        } catch (_: Exception) {
        }
    }
}
