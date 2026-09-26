package com.livrovivo.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/**
 * As pausas abaixo foram medidas em 25/09/2026 em páginas narradas pelas quatro vozes da Azure, e o início de cada
 * frase ("verdade") veio do Whisper, palavra por palavra.
 */
class SentenceAlignerTest {

    private class Recording(val name: String, val text: String, val audioMs: Long, val pauses: List<LongRange>, val truth: Map<Int, Long>)

    private companion object {
        const val BLUB = "— Blub... blub... crac!\n\nPedro acordou com um barulho esquisito de bolhas de sabão estourando na grama do quintal. " +
            "Ao espiar pela janela, não viu o jardim de sempre, mas um vale repleto de samambaias gigantescas que quase " +
            "tocavam o céu estrelado.\n\nBem ao lado da sua rede, um filhote de dinossauro azul espiava tudo, curioso. — " +
            "Quem é você? — sussurrou Pedro. O filhote piscou duas vezes e soltou mais uma bolha: plop!"
        const val GLUB = "— Glub, glub! — borbulhou Bento, ajustando seus pequenos óculos de mergulho que insistiam em escorregar pelo " +
            "focinho.\n\nPedro ajustou sua própria máscara. Eles estavam no fundo do mar, cercados por corais que pareciam " +
            "dinossauros de pedra colorida. Pedro queria muito encontrar a lendária Concha Cintilante, mas havia uma " +
            "regra: nada de nadar para longe."
    }

    private val recordings = listOf(
        Recording("blub|pt-BR-LeticiaNeural", BLUB, 34960L, listOf(0L..120L, 580L..740L, 1140L..1380L, 1660L..2780L, 8220L..9180L, 10720L..11020L, 12640L..12940L, 13820L..13920L, 18180L..19220L, 20920L..21200L, 23560L..23680L, 24020L..24240L, 24860L..25840L, 26660L..27700L, 28380L..28480L, 28900L..29880L, 33440L..33720L, 34060L..34960L), mapOf(0 to 0L, 1 to 800L, 2 to 1360L, 3 to 2720L, 4 to 9180L, 5 to 19180L, 6 to 25740L, 7 to 27640L, 8 to 29760L)),
        Recording("blub|pt-BR-Macerio:DragonHDLatestNeural", BLUB, 32040L, listOf(580L..920L, 1420L..2080L, 2320L..2420L, 2540L..3440L, 8860L..9600L, 11000L..11220L, 12880L..13240L, 18680L..19140L, 22740L..22840L, 23200L..23300L, 24360L..25160L, 26000L..26340L, 27420L..27900L, 31180L..31420L, 31660L..31780L, 31860L..32040L), mapOf(0 to 0L, 1 to 980L, 2 to 2060L, 3 to 3400L, 4 to 9600L, 5 to 19140L, 6 to 25160L, 7 to 26280L, 8 to 27880L)),
        Recording("blub|pt-BR-ValerioNeural", BLUB, 34820L, listOf(0L..120L, 660L..920L, 1500L..1840L, 2120L..3260L, 8420L..9440L, 10860L..11100L, 12680L..13000L, 13820L..13920L, 17980L..18980L, 20660L..20900L, 23160L..23260L, 23580L..23900L, 24500L..25520L, 26560L..27580L, 28320L..28420L, 28800L..29820L, 33260L..33520L, 33800L..34820L), mapOf(0 to 0L, 1 to 1080L, 2 to 1880L, 3 to 3180L, 4 to 9400L, 5 to 18940L, 6 to 25080L, 7 to 27560L, 8 to 29260L)),
        Recording("blub|pt-BR-ThalitaMultilingualNeural", BLUB, 29900L, listOf(560L..760L, 1280L..1500L, 1740L..1860L, 1880L..2220L, 7380L..7800L, 9100L..9300L, 10800L..11020L, 11860L..12020L, 14380L..14520L, 16520L..16920L, 18320L..18560L, 20980L..21080L, 21420L..21600L, 22260L..22660L, 23380L..23820L, 24540L..24640L, 25040L..25440L, 28940L..29240L, 29460L..29900L), mapOf(0 to 0L, 1 to 900L, 2 to 1540L, 3 to 2180L, 4 to 7800L, 5 to 16920L, 6 to 22600L, 7 to 23520L, 8 to 25420L)),
        Recording("glub|pt-BR-LeticiaNeural", GLUB, 28340L, listOf(0L..120L, 580L..800L, 1200L..2140L, 3160L..3500L, 8680L..9680L, 11960L..12960L, 14780L..15060L, 19160L..20140L, 23760L..24060L, 25400L..25700L, 27400L..28340L), mapOf(0 to 0L, 1 to 2100L, 2 to 9600L, 3 to 12880L, 4 to 20060L)),
        Recording("glub|pt-BR-Macerio:DragonHDLatestNeural", GLUB, 25240L, listOf(1120L..1460L, 2560L..2840L, 7860L..8600L, 10800L..11440L, 13120L..13360L, 17560L..18060L, 20200L..20300L, 23020L..23320L, 25080L..25240L), mapOf(0 to 60L, 1 to 1540L, 2 to 8520L, 3 to 11360L, 4 to 18020L)),
        Recording("glub|pt-BR-ValerioNeural", GLUB, 27540L, listOf(0L..120L, 660L..960L, 1460L..2480L, 3600L..3860L, 8740L..9760L, 12040L..13040L, 14820L..15080L, 18940L..19960L, 22140L..22240L, 23360L..23660L, 26640L..27540L), mapOf(0 to 0L, 1 to 1680L, 2 to 9700L, 3 to 12980L, 4 to 19900L)),
        Recording("glub|pt-BR-ThalitaMultilingualNeural", GLUB, 24400L, listOf(580L..760L, 1260L..1660L, 2780L..2980L, 7940L..8380L, 9560L..9660L, 10580L..10960L, 12540L..12760L, 15840L..15960L, 16960L..17380L, 19520L..19620L, 20700L..20900L, 22100L..22420L, 24040L..24400L), mapOf(0 to 0L, 1 to 1640L, 2 to 8340L, 3 to 11080L, 4 to 17340L)),
    )

    private fun errors(complete: Boolean = true, receivedMs: (Recording) -> Long = { it.audioMs }): List<Long> =
        recordings.flatMap { rec ->
            val timeline = NarrationTimeline.build(rec.text)
            val received = receivedMs(rec)
            val pauses = rec.pauses.filter { it.last <= received }
            val starts = SentenceAligner.align(timeline.weights, pauses, received, complete)
            assertEquals(rec.name, timeline.sentences.size, starts.size)
            rec.truth.filter { (_, ms) -> ms < received - 1_500 }.map { (i, ms) -> abs(starts[i] - ms) }
        }

    @Test
    fun `sentence starts follow the pauses of the voice`() {
        val errors = errors()
        assertTrue("erro médio ${errors.average()} ms", errors.average() < 200)
        assertTrue("${errors.count { it > 500 }} frases longe da voz", errors.count { it > 500 } <= 2)
    }

    @Test
    fun `pauses beat the letter estimate`() {
        val estimate = recordings.flatMap { rec ->
            val timeline = NarrationTimeline.build(rec.text)
            rec.truth.map { (i, ms) ->
                val before = timeline.weights.take(i).sum()
                abs((rec.audioMs * before / timeline.totalWeight).toLong() - ms)
            }
        }
        assertTrue(errors().average() * 2 < estimate.average())
    }

    @Test
    fun `alignment works while the audio is still arriving`() {
        val errors = errors(complete = false) { 12_000L }
        assertTrue("erro médio ${errors.average()} ms", errors.average() < 250)
    }

    @Test
    fun `ellipsis counts as one pause`() {
        val timeline = NarrationTimeline.build("Blub... blub... crac! Pedro acordou com um barulho esquisito.")
        assertEquals(4, timeline.sentences.size)
        assertEquals(10.0, timeline.weights[0], 0.0) // 4 letras + reticências uma vez só
        assertTrue(timeline.weights[3] > timeline.weights[0] * 3)
    }

    @Test
    fun `sentence at a position`() {
        val starts = longArrayOf(100, 900, 2_000)
        assertEquals(0, SentenceAligner.sentenceAt(starts, 0))
        assertEquals(0, SentenceAligner.sentenceAt(starts, 899))
        assertEquals(1, SentenceAligner.sentenceAt(starts, 900))
        assertEquals(2, SentenceAligner.sentenceAt(starts, 60_000))
        assertEquals(-1, SentenceAligner.sentenceAt(LongArray(0), 10))
    }

    @Test
    fun `speech pauses are found in pcm as it arrives`() {
        val rate = 24_000
        fun tone(ms: Int) = ByteArray(rate * ms / 1000 * 2).also { bytes ->
            for (i in 0 until bytes.size / 2) {
                val sample = (8_000 * sin(i * 2 * Math.PI * 220 / rate)).toInt()
                bytes[2 * i] = sample.toByte()
                bytes[2 * i + 1] = (sample shr 8).toByte()
            }
        }
        fun silence(ms: Int) = ByteArray(rate * ms / 1000 * 2)
        val pauses = SpeechPauses(rate)
        // Em pedaços de tamanhos variados, como chegam da rede.
        listOf(tone(500), silence(300), tone(800), silence(60), tone(400), silence(700)).forEach { piece ->
            piece.toList().chunked(1_234).forEach { pauses.append(it.toByteArray()) }
        }
        val found = pauses.pauses()
        assertEquals(2, found.size) // a pausa de 60 ms é curta demais para separar frases
        assertTrue(abs(found[0].first - 500) <= 20 && abs(found[0].last - 800) <= 20)
        assertTrue(abs(found[1].first - 2_060) <= 20)
        assertEquals(2_760L, pauses.durationMs)
    }
}
