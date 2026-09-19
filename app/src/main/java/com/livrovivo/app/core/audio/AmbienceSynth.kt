package com.livrovivo.app.core.audio

import com.livrovivo.app.domain.model.SceneKind
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** O som de fundo de uma página: o lugar e o clima dela, bem baixinho por baixo da narração. */
enum class Ambience(val label: String) {
    CRICKETS("grilos"),
    FIREPLACE("lareira"),
    WIND("vento"),
    BIRDS("passarinhos"),
    BROOK("riacho"),
    SPARKLES("brilhinhos"),
    WAVES("ondas");

    companion object {
        /**
         * O clima (mood) da página manda; o lugar da história só corrige o que não faria sentido,
         * como passarinho no fundo do mar ou no espaço, ou em plena noite.
         */
        fun forPage(mood: String?, scene: SceneKind): Ambience {
            if (mood == "emocionante") return SPARKLES
            when (scene) {
                SceneKind.SPACE -> return SPARKLES
                SceneKind.OCEAN -> return WAVES
                SceneKind.NIGHT -> return when (mood) {
                    "misterioso" -> WIND
                    "aconchegante" -> FIREPLACE
                    else -> CRICKETS
                }
                else -> Unit
            }
            return when (mood) {
                "sonolento" -> CRICKETS
                "aconchegante" -> FIREPLACE
                "misterioso" -> WIND
                "alegre" -> BIRDS
                "aventura" -> BROOK
                else -> if (scene == SceneKind.HOME) FIREPLACE else BIRDS
            }
        }
    }
}

/**
 * Sons de fundo sintetizados em tempo real, como a caixinha de música: nada de arquivo de áudio
 * nem de licença de terceiros.
 *
 * Cada som é um loop que emenda sem estalo: os ruídos são buffers do tamanho do loop, os filtros
 * rodam duas voltas (a segunda já começa no estado em que a primeira terminou), as oscilações
 * lentas dão um número inteiro de voltas e os sons curtos que passam do fim continuam no começo.
 */
object AmbienceSynth {
    const val SAMPLE_RATE = LullabySynth.SAMPLE_RATE
    const val LOOP_SECONDS = 16

    /** Volume de referência: todos os sons ficam parecidos, sem nenhum estourar. */
    const val TARGET_RMS = 0.06f
    const val MAX_PEAK = 0.5f

    fun render(kind: Ambience, seconds: Int = LOOP_SECONDS, seed: Int = 7): ShortArray {
        val n = seconds * SAMPLE_RATE
        val rng = Random(seed + kind.ordinal * 1_000)
        val mix = FloatArray(n)
        when (kind) {
            Ambience.CRICKETS -> crickets(mix, rng)
            Ambience.FIREPLACE -> fireplace(mix, rng)
            Ambience.WIND -> wind(mix, rng)
            Ambience.BIRDS -> birds(mix, rng)
            Ambience.BROOK -> brook(mix, rng)
            Ambience.SPARKLES -> sparkles(mix, rng)
            Ambience.WAVES -> waves(mix, rng)
        }
        return toPcm(mix)
    }

    // --- Paisagens ---------------------------------------------------------------------------------

    /** Noite: um sopro grave de ar e três grilos, cada um no seu ritmo. */
    private fun crickets(mix: FloatArray, rng: Random) {
        val n = mix.size
        val air = lowPass(noise(n, rng), 350.0)
        for (i in 0 until n) mix[i] += air[i] * 0.12f * (0.85f + 0.15f * lfo(i, n, 2, 0.1))

        data class Cricket(val freq: Double, val period: Double, val amp: Float)
        listOf(
            Cricket(4_300.0, 0.95, 0.10f),
            Cricket(4_750.0, 1.20, 0.07f),
            Cricket(5_150.0, 1.55, 0.05f)
        ).forEach { cricket ->
            var t = rng.nextDouble() * cricket.period
            while (t < seconds(n)) {
                // De vez em quando o grilo descansa: o ritmo não fica de máquina.
                if (rng.nextFloat() > 0.2f) {
                    chirp(mix, sampleAt(t), cricket.freq * (0.99 + rng.nextDouble() * 0.02), cricket.amp)
                }
                t += cricket.period * (0.9 + rng.nextDouble() * 0.2)
            }
        }
    }

    /** Um cri-cri: três pulsos curtinhos de um tom agudo. */
    private fun chirp(mix: FloatArray, start: Int, freq: Double, amp: Float) {
        val pulse = (0.018 * SAMPLE_RATE).toInt()
        val gap = (0.012 * SAMPLE_RATE).toInt()
        repeat(3) { p ->
            addEvent(mix, start + p * (pulse + gap), pulse) { k ->
                val envelope = sin(PI * k / pulse).pow(2)
                val t = k.toDouble() / SAMPLE_RATE
                ((sin(2 * PI * freq * t) + 0.15 * sin(4 * PI * freq * t)) * envelope * amp).toFloat()
            }
        }
    }

    /** Aconchego: o ronco baixo do fogo, um chiado leve e os estalos da madeira. */
    private fun fireplace(mix: FloatArray, rng: Random) {
        val n = mix.size
        val rumble = lowPass(lowPass(noise(n, rng), 180.0), 180.0)
        val hiss = highPass(noise(n, rng), 3_000.0)
        for (i in 0 until n) {
            val flicker = 0.8f + 0.2f * (0.5f * lfo(i, n, 3, 0.0) + 0.3f * lfo(i, n, 7, 0.3) + 0.2f * lfo(i, n, 11, 0.6))
            mix[i] += rumble[i] * 0.7f * flicker + hiss[i] * 0.012f
        }
        var t = 0.0
        while (t < seconds(n)) {
            t += nextGap(rng, perSecond = 9.0)
            val big = rng.nextFloat() < 0.06f
            val length = if (big) {
                (0.012 * SAMPLE_RATE).toInt()
            } else {
                (0.002 * SAMPLE_RATE).toInt() + rng.nextInt((0.006 * SAMPLE_RATE).toInt())
            }
            val amp = if (big) 0.28f else 0.05f + rng.nextFloat().pow(3) * 0.22f
            // Estalo pequeno é agudo; o grande ("pop") é mais encorpado.
            val brightness = if (big) 0.3f else 0.9f
            var previous = 0f
            addEvent(mix, sampleAt(t), length) { k ->
                val white = rng.nextFloat() * 2f - 1f
                val bright = white - previous * brightness
                previous = white
                bright * exp(-5.0 * k / length).toFloat() * amp
            }
        }
    }

    /** Mistério: vento que sobe e desce em rajadas, um assobio fino e dois sininhos ao longe. */
    private fun wind(mix: FloatArray, rng: Random) {
        val n = mix.size
        val body = bandPass(noise(n, rng), q = 1.1) { i -> 380.0 + 220.0 * (0.6 * lfo(i, n, 1, 0.0) + 0.4 * lfo(i, n, 3, 0.25)) }
        val whistle = bandPass(noise(n, rng), q = 7.0) { i -> 1_100.0 + 250.0 * lfo(i, n, 2, 0.5) }
        for (i in 0 until n) {
            val gust = (0.5f + 0.35f * lfo(i, n, 2, 0.15) + 0.15f * lfo(i, n, 5, 0.4)).coerceIn(0f, 1f)
            val level = 0.35f + 0.65f * gust * gust
            mix[i] += body[i] * 2.2f * level + whistle[i] * 0.6f * level * level
        }
        repeat(2) { k ->
            val start = ((k * 0.5 + 0.2 + rng.nextDouble() * 0.2) * n).toInt()
            bell(mix, start, freq = if (k == 0) 880.0 else 1_174.7, amp = 0.05f, decaySeconds = 2.8)
        }
    }

    /** Alegria: folhas mexendo e passarinhos, uns pertinho e outros longe. */
    private fun birds(mix: FloatArray, rng: Random) {
        val n = mix.size
        val leaves = bandPass(noise(n, rng), q = 0.7) { 2_600.0 }
        for (i in 0 until n) mix[i] += leaves[i] * 0.12f * (0.7f + 0.3f * lfo(i, n, 3, 0.2))
        var t = rng.nextDouble()
        while (t < seconds(n)) {
            val near = rng.nextFloat() > 0.3f
            birdCall(mix, sampleAt(t), amp = if (near) 0.14f else 0.05f, rng = rng)
            t += 1.2 + rng.nextDouble() * 1.6
        }
    }

    /** Aventura ao ar livre: um riacho borbulhando e, de vez em quando, um passarinho ao longe. */
    private fun brook(mix: FloatArray, rng: Random) {
        val n = mix.size
        val flow = bandPass(noise(n, rng), q = 0.6) { 900.0 }
        val ripple = normalized(lowPass(noise(n, rng), 6.0))
        for (i in 0 until n) mix[i] += flow[i] * 0.25f * (0.8f + 0.2f * ripple[i])
        var t = 0.0
        while (t < seconds(n)) {
            t += nextGap(rng, perSecond = 30.0)
            bubble(mix, sampleAt(t), freq = 450.0 + rng.nextDouble() * 900.0, seconds = 0.015 + rng.nextDouble() * 0.03, amp = 0.015f + rng.nextFloat() * 0.05f)
        }
        var bird = rng.nextDouble() * 3.0
        while (bird < seconds(n)) {
            birdCall(mix, sampleAt(bird), amp = 0.04f, rng = rng)
            bird += 4.0 + rng.nextDouble() * 3.0
        }
    }

    /** Emoção: brilhinhos mágicos em notas que sempre combinam (pentatônica) e um ar bem leve. */
    private fun sparkles(mix: FloatArray, rng: Random) {
        val n = mix.size
        val air = highPass(noise(n, rng), 4_000.0)
        for (i in 0 until n) mix[i] += air[i] * 0.02f * (0.6f + 0.4f * lfo(i, n, 4, 0.0))
        val notes = doubleArrayOf(2_093.0, 2_349.3, 2_637.0, 3_136.0, 3_520.0, 4_186.0)
        var t = 0.0
        while (t < seconds(n)) {
            t += nextGap(rng, perSecond = 2.5)
            twinkle(mix, sampleAt(t), notes[rng.nextInt(notes.size)], amp = 0.04f + rng.nextFloat() * 0.07f, decaySeconds = 0.6 + rng.nextDouble() * 0.6)
        }
    }

    /** Mar: duas ondas por volta, em tempos diferentes, com a espuma chiando logo depois da crista. */
    private fun waves(mix: FloatArray, rng: Random) {
        val n = mix.size
        val deep = lowPass(lowPass(noise(n, rng), 350.0), 350.0)
        val foam = highPass(noise(n, rng), 1_800.0)
        val swell = FloatArray(n)
        val rise = (2.8 * SAMPLE_RATE).toInt()
        val fall = (4.5 * SAMPLE_RATE).toInt()
        listOf(0.08 + rng.nextDouble() * 0.05, 0.55 + rng.nextDouble() * 0.08).forEach { position ->
            val crest = (position * n).toInt()
            addEvent(swell, crest - rise + n, rise) { k -> sin(PI / 2 * k / rise).pow(2).toFloat() }
            addEvent(swell, crest, fall) { k -> exp(-3.0 * k / fall).toFloat() }
        }
        val foamLag = SAMPLE_RATE
        for (i in 0 until n) {
            val s = swell[i].coerceAtMost(1f)
            val lag = swell[(i - foamLag + n) % n].coerceAtMost(1f)
            mix[i] += deep[i] * 3.0f * (0.25f + 0.75f * s) + foam[i] * 0.30f * lag * lag
        }
    }

    // --- Sons curtos -------------------------------------------------------------------------------

    private fun birdCall(mix: FloatArray, start: Int, amp: Float, rng: Random) {
        when (rng.nextInt(3)) {
            0 -> repeat(2 + rng.nextInt(3)) { r ->
                sweep(mix, start + r * (0.15 * SAMPLE_RATE).toInt(), 2_800.0, 4_300.0, 0.07, amp)
            }
            1 -> {
                val length = (0.35 * SAMPLE_RATE).toInt()
                var phase = 0.0
                addEvent(mix, start, length) { k ->
                    val t = k.toDouble() / SAMPLE_RATE
                    phase += 2 * PI * (3_200.0 + 600.0 * sin(2 * PI * 18.0 * t)) / SAMPLE_RATE
                    (sin(phase) * sin(PI * k / length) * amp * 0.8).toFloat()
                }
            }
            else -> {
                sweep(mix, start, 3_700.0, 3_600.0, 0.12, amp)
                sweep(mix, start + (0.16 * SAMPLE_RATE).toInt(), 3_000.0, 2_850.0, 0.18, amp)
            }
        }
    }

    /** Um tom que desliza de [fromHz] para [toHz], com entrada e saída suaves. */
    private fun sweep(mix: FloatArray, start: Int, fromHz: Double, toHz: Double, seconds: Double, amp: Float) {
        val length = (seconds * SAMPLE_RATE).toInt()
        var phase = 0.0
        addEvent(mix, start, length) { k ->
            val p = k.toDouble() / length
            phase += 2 * PI * (fromHz + (toHz - fromHz) * p) / SAMPLE_RATE
            (sin(phase) * sin(PI * p) * amp).toFloat()
        }
    }

    /** Uma bolhinha: senoide curta que sobe de tom, como uma gota batendo na água. */
    private fun bubble(mix: FloatArray, start: Int, freq: Double, seconds: Double, amp: Float) {
        val length = (seconds * SAMPLE_RATE).toInt()
        val attack = (0.001 * SAMPLE_RATE).toInt()
        var phase = 0.0
        addEvent(mix, start, length) { k ->
            val p = k.toDouble() / length
            phase += 2 * PI * freq * (1.0 + 0.8 * p) / SAMPLE_RATE
            val envelope = (if (k < attack) k.toDouble() / attack else 1.0) * exp(-4.0 * p)
            (sin(phase) * envelope * amp).toFloat()
        }
    }

    /** Sininho: parciais inarmônicos, como um sino de verdade, com decaimento lento. */
    private fun bell(mix: FloatArray, start: Int, freq: Double, amp: Float, decaySeconds: Double) {
        val length = (decaySeconds * SAMPLE_RATE).toInt()
        val attack = (0.003 * SAMPLE_RATE).toInt()
        addEvent(mix, start, length) { k ->
            val t = k.toDouble() / SAMPLE_RATE
            val envelope = (if (k < attack) k.toDouble() / attack else 1.0) * exp(-t * 4.0 / decaySeconds)
            val tone = sin(2 * PI * freq * t) +
                0.45 * sin(2 * PI * freq * 2.76 * t) * exp(-t * 3.0) +
                0.2 * sin(2 * PI * freq * 5.4 * t) * exp(-t * 6.0)
            (tone * envelope * amp).toFloat()
        }
    }

    /** Brilhinho: nota aguda com a oitava por cima, sumindo rápido. */
    private fun twinkle(mix: FloatArray, start: Int, freq: Double, amp: Float, decaySeconds: Double) {
        val length = (decaySeconds * SAMPLE_RATE).toInt()
        val attack = (0.003 * SAMPLE_RATE).toInt()
        addEvent(mix, start, length) { k ->
            val t = k.toDouble() / SAMPLE_RATE
            val envelope = (if (k < attack) k.toDouble() / attack else 1.0) * exp(-t * 5.0 / decaySeconds)
            ((sin(2 * PI * freq * t) + 0.3 * sin(4 * PI * freq * t)) * envelope * amp).toFloat()
        }
    }

    // --- Utilidades --------------------------------------------------------------------------------

    private fun seconds(n: Int): Double = n.toDouble() / SAMPLE_RATE

    private fun sampleAt(seconds: Double): Int = (seconds * SAMPLE_RATE).toInt()

    /** Intervalo até o próximo som, como num processo aleatório (chuva de estalos, de bolhas...). */
    private fun nextGap(rng: Random, perSecond: Double): Double = -ln(1.0 - rng.nextDouble()) / perSecond

    private fun noise(n: Int, rng: Random): FloatArray = FloatArray(n) { rng.nextFloat() * 2f - 1f }

    /** Oscilação lenta com um número inteiro de voltas por loop: o fim encaixa no começo. */
    private fun lfo(i: Int, n: Int, cycles: Int, phase: Double): Float =
        sin(2 * PI * (cycles.toDouble() * i / n + phase)).toFloat()

    /** Soma um som curto começando em [start]; o que passar do fim continua no começo do loop. */
    private inline fun addEvent(mix: FloatArray, start: Int, length: Int, sample: (Int) -> Float) {
        val n = mix.size
        val origin = ((start % n) + n) % n
        for (k in 0 until length) {
            val index = origin + k
            mix[if (index >= n) index % n else index] += sample(k)
        }
    }

    /** Passa-baixa de um polo, em duas voltas para o resultado ser periódico. */
    private fun lowPass(x: FloatArray, cutoffHz: Double): FloatArray {
        val a = (1.0 - exp(-2.0 * PI * cutoffHz / SAMPLE_RATE)).toFloat()
        val y = FloatArray(x.size)
        var state = 0f
        repeat(2) { pass ->
            for (i in x.indices) {
                state += a * (x[i] - state)
                if (pass == 1) y[i] = state
            }
        }
        return y
    }

    private fun highPass(x: FloatArray, cutoffHz: Double): FloatArray {
        val low = lowPass(x, cutoffHz)
        return FloatArray(x.size) { x[it] - low[it] }
    }

    /**
     * Passa-faixa (biquad do "Audio EQ Cookbook") com o centro variando no tempo, também em
     * duas voltas. Os coeficientes são recalculados a cada 16 amostras, o que basta para
     * variações lentas.
     */
    private fun bandPass(x: FloatArray, q: Double, centerHz: (Int) -> Double): FloatArray {
        val y = FloatArray(x.size)
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        var b0 = 0.0
        var b2 = 0.0
        var a1 = 0.0
        var a2 = 0.0
        repeat(2) { pass ->
            for (i in x.indices) {
                if (i % 16 == 0) {
                    val w0 = 2 * PI * centerHz(i) / SAMPLE_RATE
                    val alpha = sin(w0) / (2 * q)
                    val a0 = 1 + alpha
                    b0 = alpha / a0
                    b2 = -alpha / a0
                    a1 = -2 * cos(w0) / a0
                    a2 = (1 - alpha) / a0
                }
                val out = b0 * x[i] + b2 * x2 - a1 * y1 - a2 * y2
                x2 = x1
                x1 = x[i].toDouble()
                y2 = y1
                y1 = out
                if (pass == 1) y[i] = out.toFloat()
            }
        }
        return y
    }

    /** Leva um ruído lento para mais ou menos entre -1 e 1 (usado como modulação). */
    private fun normalized(x: FloatArray): FloatArray {
        val rms = sqrt(x.sumOf { (it * it).toDouble() } / x.size).toFloat().coerceAtLeast(1e-6f)
        return FloatArray(x.size) { (x[it] / (3f * rms)).coerceIn(-1f, 1f) }
    }

    /** Ajusta o volume (pela média e sem passar do pico) e converte para PCM de 16 bits. */
    private fun toPcm(mix: FloatArray): ShortArray {
        var sumSquares = 0.0
        var peak = 0f
        for (v in mix) {
            sumSquares += v * v
            peak = max(peak, abs(v))
        }
        val rms = sqrt(sumSquares / mix.size).toFloat().coerceAtLeast(1e-6f)
        val gain = min(TARGET_RMS / rms, MAX_PEAK / peak.coerceAtLeast(1e-6f))
        return ShortArray(mix.size) { i ->
            (mix[i] * gain * Short.MAX_VALUE).roundToInt().coerceIn(-32_768, 32_767).toShort()
        }
    }
}
