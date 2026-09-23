package com.livrovivo.app.core.literacy

import com.livrovivo.app.domain.model.DecodableBook
import com.livrovivo.app.domain.model.LiteracyKnowledge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OfflineDecodableEngineTest {

    private val trail = TrailParser.parse(File("src/main/assets/${TrailParser.ASSET_PATH}").readText(Charsets.UTF_8))
    private val support = trail.supportWords.toSet()
    private fun phasesOf(moduleId: String) = trail.module(moduleId)!!.phases.map { it.id }

    /** Conhecimento depois de concluir, em ordem, as primeiras [count] fases que vêm depois das consoantes. */
    private fun knowledgeAfter(count: Int): LiteracyKnowledge {
        val letters = phasesOf("vogais") + phasesOf("consoantes")
        val later = phasesOf("silabas") + phasesOf("palavras") + phasesOf("ditado")
        return LiteracyRules.knowledge(trail, (letters + later.take(count)).toSet())
    }

    private val names = listOf("Lia", "João", "Maria Clara", "Ana-Luísa", "Zé", "lia2", "Maximiliano", "D'Ávila", "", "😀")
    private val companions = listOf(null, "Luna", "Pipoca", "Bento", "Aurora")
    private val seeds = 0 until 25

    private fun problems(book: DecodableBook, knowledge: LiteracyKnowledge, name: String): List<String> {
        val texts = listOf(book.title) + book.pages.map { it.text }
        return texts.flatMap { DecodableValidator.invalidWords(it, knowledge, support, name) } +
            DecodableValidator.formatProblems(book.title, book.pages.map { it.text })
    }

    @Test
    fun `every book in every point of the trail is readable and well formed`() {
        var books = 0
        val laterPhases = phasesOf("silabas").size + phasesOf("palavras").size + phasesOf("ditado").size
        for (count in 0..laterPhases) {
            val knowledge = knowledgeAfter(count)
            for (name in names) for (companion in companions) for (seed in seeds) {
                val book = OfflineDecodableEngine.write(trail, knowledge, name, companion, seed) ?: continue
                books++
                val found = problems(book, knowledge, name)
                assertTrue("fases=$count nome=$name companheiro=$companion semente=$seed\n$book\n$found", found.isEmpty())
            }
        }
        assertTrue("o teste precisa de fato gerar livros ($books)", books > 10_000)
    }

    @Test
    fun `the first book already exists after 3 syllable phases`() {
        val knowledge = knowledgeAfter(LiteracyRules.FIRST_BOOK_SYLLABLE_PHASES)
        for (name in names) for (seed in seeds) {
            assertNotNull("nome=$name semente=$seed", OfflineDecodableEngine.write(trail, knowledge, name, seed = seed))
        }
    }

    @Test
    fun `before the first book there are not enough words`() {
        for (count in 0 until LiteracyRules.FIRST_BOOK_SYLLABLE_PHASES) {
            assertNull(OfflineDecodableEngine.write(trail, knowledgeAfter(count), "Lia"))
        }
    }

    @Test
    fun `the child is the main character`() {
        val knowledge = knowledgeAfter(8)
        for (seed in seeds) {
            val book = OfflineDecodableEngine.write(trail, knowledge, "Maria Clara", seed = seed)!!
            assertTrue(book.toString(), book.pages.any { "MARIA" in it.text })
        }
    }

    @Test
    fun `possession and place sentences only use words that make sense there`() {
        val byWord = trail.vocabulary.associateBy { it.word }
        val owned = Regex("(?:TEM (?:UM|UMA)|COM (?:O|A)) (\\p{L}+)|(?:^|[ ,.!?])(?:O|A) (\\p{L}+) (?:É DE|ESTÁ)")
        val place = Regex("ESTÁ (?:NO|NA) (\\p{L}+)")
        for (count in 3..30) for (seed in seeds) {
            val book = OfflineDecodableEngine.write(trail, knowledgeAfter(count), "Lia", "Luna", seed) ?: continue
            book.pages.forEach { page ->
                owned.findAll(page.text).mapNotNull { it.groupValues.drop(1).firstOrNull(String::isNotEmpty) }
                    .filter { it in byWord }
                    .forEach { assertTrue("\"$it\" em: ${page.text}", byWord.getValue(it).isObject) }
                place.findAll(page.text).map { it.groupValues[1] }
                    .forEach { assertTrue("\"$it\" em: ${page.text}", byWord.getValue(it).isPlace) }
            }
        }
    }

    @Test
    fun `each page shows the word its picture illustrates`() {
        for (count in 3..30) for (seed in seeds) {
            val book = OfflineDecodableEngine.write(trail, knowledgeAfter(count), "Lia", seed = seed) ?: continue
            book.pages.forEach { page -> assertTrue(page.toString(), page.mainWord.word in page.text) }
        }
    }

    @Test
    fun `the companion appears only when the child can read its name`() {
        val early = knowledgeAfter(3)
        val everything = knowledgeAfter(33)
        for (seed in seeds) {
            listOf("Luna", "Pipoca").forEach { companion ->
                val book = OfflineDecodableEngine.write(trail, early, "Lia", companion, seed)!!
                assertFalse(book.usesCompanion)
                assertFalse(book.toString(), book.pages.any { companion.uppercase() in it.text })
            }
            listOf("Bento", "Aurora").forEach { companion ->
                val book = OfflineDecodableEngine.write(trail, everything, "Lia", companion, seed)!!
                assertFalse(book.toString(), book.pages.any { companion.uppercase() in it.text })
            }
        }
        val withLuna = seeds.map { OfflineDecodableEngine.write(trail, everything, "Lia", "Luna", it)!! }
        assertTrue(withLuna.any { it.usesCompanion })
        withLuna.forEach { book -> assertEquals(book.toString(), book.usesCompanion, book.pages.any { "LUNA" in it.text }) }
    }

    @Test
    fun `the same seed writes the same book and different seeds vary`() {
        val knowledge = knowledgeAfter(13)
        assertEquals(
            OfflineDecodableEngine.write(trail, knowledge, "Lia", "Luna", seed = 7),
            OfflineDecodableEngine.write(trail, knowledge, "Lia", "Luna", seed = 7)
        )
        val distinct = seeds.map { OfflineDecodableEngine.write(trail, knowledge, "Lia", seed = it) }.toSet()
        assertTrue("só ${distinct.size} livros diferentes", distinct.size >= 15)
    }

    @Test
    fun `the newest syllables show up in the book`() {
        val lastSyllablePhase = trail.phase(phasesOf("silabas").last())!!
        val focus = lastSyllablePhase.teaches.toSet()
        val knowledge = knowledgeAfter(phasesOf("silabas").size)
        val focusWords = trail.vocabulary.filter { word -> word.syllables.any { it in focus } }.map { it.word }
        for (seed in seeds) {
            val book = OfflineDecodableEngine.write(trail, knowledge, "Lia", seed = seed, focusSyllables = focus)!!
            val text = book.pages.joinToString(" ") { it.text }
            assertTrue("$focus em: $text", focusWords.any { it in text })
        }
    }

    @Test
    fun `the first book with Lia uses only BOCA, DADO, DEDO and DOCE plus support words`() {
        val knowledge = knowledgeAfter(3)
        val allowed = support + setOf("LIA", "BOCA", "DADO", "DEDO", "DOCE")
        for (seed in seeds) {
            val book = OfflineDecodableEngine.write(trail, knowledge, "Lia", seed = seed)!!
            val words = (listOf(book.title) + book.pages.map { it.text }).flatMap { DecodableValidator.words(it) }
            assertTrue(book.toString(), allowed.containsAll(words))
        }
    }
}
