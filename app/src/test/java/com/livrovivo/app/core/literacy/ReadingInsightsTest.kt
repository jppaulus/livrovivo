package com.livrovivo.app.core.literacy

import com.livrovivo.app.domain.model.PhaseProgress
import com.livrovivo.app.domain.model.Story
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReadingInsightsTest {

    private val trail = TrailParser.parse(File("src/main/assets/${TrailParser.ASSET_PATH}").readText(Charsets.UTF_8))

    private fun done(phaseId: String, mistakes: Int = 0, at: Long = 1L, stars: Int = 3) =
        PhaseProgress("lia", phaseId, stars, attempts = 1, mistakes = mistakes, completedAt = at.takeIf { stars > 0 })

    private fun book(completed: Boolean) = Story(id = "b$completed", childId = "lia", title = "O DOCE DE LIA", theme = "Eu leio",
        objectiveType = "cognitivo", isCompleted = completed, kind = Story.KIND_EU_LEIO)

    @Test
    fun `counts show what the child did`() {
        val progress = listOf(done("vogais_a"), done("vogais_e"), done("vogais_i", stars = 0))

        val insights = ReadingInsightsBuilder.build(trail, progress, listOf(book(true), book(false)), pagesReadAlone = 5)

        assertEquals(2, insights.completedPhases)
        assertEquals(51, insights.totalPhases)
        assertEquals(1, insights.booksRead)
        assertEquals(2, insights.booksTotal)
        assertEquals(5, insights.pagesReadAlone)
    }

    @Test
    fun `letters and syllables of completed phases are highlighted`() {
        val progress = listOf(done("vogais_a"), done("consoantes_b"), done("silabas_b"))

        val insights = ReadingInsightsBuilder.build(trail, progress, emptyList(), 0)

        assertEquals(18, insights.letters.size)
        assertEquals(setOf("A", "B"), insights.letters.filter { it.second }.map { it.first }.toSet())
        assertEquals(13, insights.syllableRows.size)
        assertEquals(65, insights.totalSyllables)
        assertEquals(listOf("BA", "BE", "BI", "BO", "BU"), insights.syllableRows.first().map { it.first })
        assertEquals(5, insights.learnedSyllables)
        assertTrue(insights.syllableRows.first().all { it.second })
        assertFalse(insights.syllableRows[1].any { it.second })
    }

    @Test
    fun `practice together lists up to 3 phases with the most mistakes`() {
        val progress = listOf(
            done("silabas_r", mistakes = 9),
            done("silabas_b", mistakes = 2),
            done("silabas_c", mistakes = 0),
            done("silabas_d", mistakes = 4),
            done("silabas_f", mistakes = 1)
        )

        val practice = ReadingInsightsBuilder.build(trail, progress, emptyList(), 0).practiceTogether

        assertEquals(listOf("Sílabas com R", "Sílabas com D", "Sílabas com B"), practice)
    }

    @Test
    fun `the off-screen idea follows the last thing the child did`() {
        fun idea(vararg progress: PhaseProgress) = ReadingInsightsBuilder.offlineIdea(trail, progress.toList())

        assertTrue(idea().contains("letras do nome"))
        assertEquals("Procurem na cozinha objetos que começam com a letra P.", idea(done("vogais_a", at = 1), done("consoantes_p", at = 9)))
        assertEquals(
            "Brinquem de falar palavras que começam com BA, BE, BI, BO ou BU.",
            idea(done("consoantes_b", at = 1), done("silabas_b", at = 5))
        )
        assertTrue(idea(done("palavras_01", at = 7)).contains("a palavra BOLA"))
        assertTrue(idea(done("ditado_01", at = 8)).contains("ditado de brincadeira"))
    }

    @Test
    fun `no text promises learning results`() {
        val progress = listOf(done("vogais_a"), done("silabas_b", mistakes = 3), done("palavras_01", at = 3))
        val insights = ReadingInsightsBuilder.build(trail, progress, emptyList(), 0)
        val texts = insights.practiceTogether + insights.offlineIdea

        texts.forEach { text ->
            listOf("aprendeu", "domina", "garant", "nível", "nota").forEach { word -> assertFalse("$word em: $text", text.contains(word, ignoreCase = true)) }
        }
    }
}
