package com.livrovivo.app.core.audio

import com.livrovivo.app.domain.model.SceneKind
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Os sons de fundo, medidos sem ouvir: tamanho do loop, volume, emenda e timbre.
 * (Para ouvir de verdade, veja [AmbienceSamplesExport].)
 */
class AmbienceSynthTest {

    companion object {
        private val loops: Map<Ambience, ShortArray> by lazy {
            Ambience.entries.associateWith { AmbienceSynth.render(it) }
        }

        private fun ShortArray.levels(): Pair<Double, Double> {
            var sum = 0.0
            var peak = 0.0
            forEach { s ->
                val v = s / 32768.0
                sum += v * v
                peak = maxOf(peak, abs(v))
            }
            return sqrt(sum / size) to peak
        }

        /** Frequência "média" ponderada pela energia: agudo dá número alto, grave dá baixo. */
        private fun ShortArray.brightnessHz(): Double {
            var energy = 0.0
            var slope = 0.0
            for (i in 1 until size) {
                val v = this[i].toDouble()
                val d = v - this[i - 1]
                energy += v * v
                slope += d * d
            }
            return sqrt(slope / energy) * AmbienceSynth.SAMPLE_RATE / (2 * PI)
        }
    }

    @Test
    fun `each soundscape is exactly one loop long`() {
        loops.values.forEach { assertEquals(AmbienceSynth.LOOP_SECONDS * AmbienceSynth.SAMPLE_RATE, it.size) }
    }

    @Test
    fun `rendering is deterministic`() {
        assertArrayEquals(loops.getValue(Ambience.BIRDS), AmbienceSynth.render(Ambience.BIRDS))
    }

    @Test
    fun `every soundscape is gentle, similar in level and never clips`() {
        loops.forEach { (kind, pcm) ->
            val (rms, peak) = pcm.levels()
            assertTrue("$kind baixo demais: rms=$rms", rms > 0.015)
            assertTrue("$kind alto demais: rms=$rms", rms <= AmbienceSynth.TARGET_RMS + 0.005)
            assertTrue("$kind passou do pico: $peak", peak <= AmbienceSynth.MAX_PEAK + 0.01)
        }
    }

    @Test
    fun `loops join without a click`() {
        loops.forEach { (kind, pcm) ->
            val jumps = (1 until pcm.size).map { abs(pcm[it] - pcm[it - 1]) }.sorted()
            val typicalBigJump = jumps[(jumps.size * 0.999).toInt()]
            val seam = abs(pcm[0] - pcm[pcm.size - 1])
            assertTrue("$kind estala na emenda: $seam > $typicalBigJump", seam <= typicalBigJump)
        }
    }

    @Test
    fun `each soundscape has the right color, bright or dark`() {
        val hz = loops.mapValues { (_, pcm) -> pcm.brightnessHz() }

        assertTrue("grilos mais agudos que ondas: $hz", hz.getValue(Ambience.CRICKETS) > hz.getValue(Ambience.WAVES))
        assertTrue("passarinhos mais agudos que vento: $hz", hz.getValue(Ambience.BIRDS) > hz.getValue(Ambience.WIND))
        assertTrue("brilhinhos mais agudos que lareira: $hz", hz.getValue(Ambience.SPARKLES) > hz.getValue(Ambience.FIREPLACE))
    }

    @Test
    fun `the page mood picks the soundscape`() {
        val forest = SceneKind.FOREST
        assertEquals(Ambience.CRICKETS, Ambience.forPage("sonolento", forest))
        assertEquals(Ambience.FIREPLACE, Ambience.forPage("aconchegante", forest))
        assertEquals(Ambience.WIND, Ambience.forPage("misterioso", forest))
        assertEquals(Ambience.BIRDS, Ambience.forPage("alegre", forest))
        assertEquals(Ambience.BROOK, Ambience.forPage("aventura", forest))
        assertEquals(Ambience.SPARKLES, Ambience.forPage("emocionante", forest))
    }

    @Test
    fun `the place fixes what would not make sense`() {
        assertEquals("sem passarinho no fundo do mar", Ambience.WAVES, Ambience.forPage("alegre", SceneKind.OCEAN))
        assertEquals("sem grilo no espaço", Ambience.SPARKLES, Ambience.forPage("sonolento", SceneKind.SPACE))
        assertEquals("sem passarinho de noite", Ambience.CRICKETS, Ambience.forPage("alegre", SceneKind.NIGHT))
        assertEquals(Ambience.WIND, Ambience.forPage("misterioso", SceneKind.NIGHT))
        // O clímax emocionante vale em qualquer lugar.
        assertEquals(Ambience.SPARKLES, Ambience.forPage("emocionante", SceneKind.OCEAN))
    }

    @Test
    fun `pages without a mood still get a fitting sound`() {
        assertEquals(Ambience.CRICKETS, Ambience.forPage(null, SceneKind.NIGHT))
        assertEquals(Ambience.FIREPLACE, Ambience.forPage(null, SceneKind.HOME))
        assertEquals(Ambience.BIRDS, Ambience.forPage(null, SceneKind.SCHOOL))
    }
}
