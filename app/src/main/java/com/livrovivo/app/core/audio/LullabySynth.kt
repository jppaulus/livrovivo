package com.livrovivo.app.core.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/**
 * Caixinha de música sintetizada em tempo real (sem arquivos de áudio):
 * "Brilha, Brilha, Estrelinha" (melodia tradicional, domínio público) com baixo suave e eco.
 */
object LullabySynth {
    const val SAMPLE_RATE = 22_050

    private const val BEAT_SECONDS = 0.78

    // Notas MIDI (C5 = 72) e duração em tempos. Seis frases de 7 notas (a última dura 2 tempos).
    private val MELODY: List<Pair<Int, Double>> = run {
        val phraseA = listOf(72, 72, 79, 79, 81, 81, 79)
        val phraseB = listOf(77, 77, 76, 76, 74, 74, 72)
        val phraseC = listOf(79, 79, 77, 77, 76, 76, 74)
        listOf(phraseA, phraseB, phraseC, phraseC, phraseA, phraseB).flatMap { phrase ->
            phrase.mapIndexed { i, note -> note to if (i == phrase.lastIndex) 2.0 else 1.0 }
        }
    }

    // Um baixo a cada 2 tempos (cada frase ocupa 8 tempos = 4 notas de baixo).
    private val BASS: List<Int> = listOf(
        48, 48, 53, 48, // C C F C
        53, 48, 55, 48, // F C G C
        48, 53, 48, 55, // C F C G
        48, 53, 48, 55,
        48, 48, 53, 48,
        53, 48, 55, 48
    )

    fun render(): ShortArray {
        val totalBeats = MELODY.sumOf { it.second } + 2.0 // respiro no final antes de repetir
        val totalSamples = (totalBeats * BEAT_SECONDS * SAMPLE_RATE).toInt()
        val mix = FloatArray(totalSamples)

        var beat = 0.0
        MELODY.forEach { (note, duration) ->
            addTine(mix, startSample(beat), frequency(note), amplitude = 0.42f, ringSeconds = 2.6)
            // Oitava acima bem baixinha: brilho típico de caixinha de música.
            addTine(mix, startSample(beat) + 40, frequency(note + 12), amplitude = 0.07f, ringSeconds = 1.2)
            beat += duration
        }
        BASS.forEachIndexed { i, note ->
            addTine(mix, startSample(i * 2.0), frequency(note), amplitude = 0.16f, ringSeconds = 3.2, bright = false)
        }

        applyEcho(mix, delaySeconds = 0.23, feedback = 0.28f)
        applyEcho(mix, delaySeconds = 0.37, feedback = 0.18f)
        applyLoopFade(mix)

        val peak = mix.maxOf { kotlin.math.abs(it) }.coerceAtLeast(0.0001f)
        val gain = 0.55f / peak
        return ShortArray(totalSamples) { i -> (mix[i] * gain * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort() }
    }

    private fun startSample(beat: Double): Int = (beat * BEAT_SECONDS * SAMPLE_RATE).toInt()

    private fun frequency(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    private fun addTine(
        mix: FloatArray,
        start: Int,
        freq: Double,
        amplitude: Float,
        ringSeconds: Double,
        bright: Boolean = true
    ) {
        val length = (ringSeconds * SAMPLE_RATE).toInt()
        val attack = (0.004 * SAMPLE_RATE).toInt()
        for (n in 0 until length) {
            val index = start + n
            if (index >= mix.size) break
            val t = n.toDouble() / SAMPLE_RATE
            val envelope = (if (n < attack) n.toDouble() / attack else 1.0) * exp(-t * 2.2)
            var sample = sin(2 * PI * freq * t)
            if (bright) {
                sample += 0.28 * sin(2 * PI * freq * 2.0 * t) * exp(-t * 7.0) +
                    0.10 * sin(2 * PI * freq * 3.01 * t) * exp(-t * 12.0)
            }
            mix[index] += (sample * envelope * amplitude).toFloat()
        }
    }

    private fun applyEcho(mix: FloatArray, delaySeconds: Double, feedback: Float) {
        val delay = (delaySeconds * SAMPLE_RATE).toInt()
        for (i in delay until mix.size) {
            mix[i] += mix[i - delay] * feedback
        }
    }

    /** Suaviza o início/fim para o loop não dar estalo. */
    private fun applyLoopFade(mix: FloatArray) {
        val fade = (0.35 * SAMPLE_RATE).toInt().coerceAtMost(mix.size / 4)
        for (i in 0 until fade) {
            val g = i.toFloat() / fade
            mix[i] *= g
            mix[mix.size - 1 - i] *= g
        }
    }
}
