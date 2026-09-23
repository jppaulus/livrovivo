package com.livrovivo.app.core.literacy

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Sons curtinhos das atividades, sintetizados na hora (sem arquivos):
 * acerto = duas notas subindo; erro = duas notas descendo, baixinho, sem cara de "errado!".
 */
class FeedbackSounds {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val success by lazy { render(listOf(659.25, 880.0), volume = 0.30f) }  // Mi5 → Lá5
    private val tryAgain by lazy { render(listOf(440.0, 349.23), volume = 0.16f) } // Lá4 → Fá4

    fun playSuccess() = play(success)
    fun playTryAgain() = play(tryAgain)

    private fun play(samples: ShortArray) {
        scope.launch {
            runCatching {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(samples.size * 2)
                    .build()
                track.write(samples, 0, samples.size)
                track.play()
                Thread.sleep(samples.size * 1000L / SAMPLE_RATE + 100)
                track.release()
            }
        }
    }

    private fun render(notes: List<Double>, volume: Float): ShortArray {
        val noteSamples = (SAMPLE_RATE * 0.16).toInt()
        val out = ShortArray(noteSamples * notes.size + SAMPLE_RATE / 5)
        notes.forEachIndexed { n, frequency ->
            for (i in 0 until noteSamples + SAMPLE_RATE / 5) {
                val index = n * noteSamples + i
                if (index >= out.size) break
                val t = i.toDouble() / SAMPLE_RATE
                val attack = (t / 0.01).coerceAtMost(1.0)
                val envelope = attack * exp(-t * 9.0)
                val value = sin(2 * PI * frequency * t) * envelope * volume * Short.MAX_VALUE
                out[index] = (out[index] + value.toInt()).coerceIn(-32768, 32767).toShort()
            }
        }
        return out
    }

    private companion object {
        const val SAMPLE_RATE = 22_050
    }
}
