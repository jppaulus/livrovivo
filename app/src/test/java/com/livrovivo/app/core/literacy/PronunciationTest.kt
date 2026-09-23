package com.livrovivo.app.core.literacy

import com.livrovivo.app.domain.model.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class PronunciationTest {

    private val trail = TrailParser.parse(File("src/main/assets/${TrailParser.ASSET_PATH}").readText(Charsets.UTF_8))
    private val letters = listOf("vogais", "consoantes").flatMap { id -> trail.module(id)!!.phases.flatMap { it.teaches } }
    private val syllables = LiteracyRules.allSyllables(trail)
    private val units = letters.toSet() + syllables

    @Test
    fun `letters are read by their names`() {
        assertEquals("á", Pronunciation.hint("A"))
        assertEquals("bê", Pronunciation.hint("B"))
        assertEquals("éfe", Pronunciation.hint("F"))
        assertEquals("érre", Pronunciation.hint("R"))
    }

    @Test
    fun `syllables are read as one sound with the vowel marked`() {
        assertEquals("bá", Pronunciation.hint("BA"))
        assertEquals("bê", Pronunciation.hint("BE"))
        assertEquals("cê", Pronunciation.hint("CE"))
        assertEquals("gi", Pronunciation.hint("GI"))
        assertEquals("sô", Pronunciation.hint("SO"))
        assertEquals("vu", Pronunciation.hint("VU"))
        assertEquals("bola", Pronunciation.hint("BOLA"))
    }

    @Test
    fun `every letter and syllable of the trail has its recording name`() {
        assertEquals(83, units.size)
        assertEquals("som_ba", Pronunciation.resourceName("BA"))
        assertEquals("som_a", Pronunciation.resourceName("A"))
        units.forEach { unit -> assertEquals(unit, true, Pronunciation.resourceName(unit).matches(Regex("som_[a-z]{1,2}"))) }
    }

    @Test
    fun `the app voice gets an example word when the vocabulary has one`() {
        assertEquals("BALA", Pronunciation.exampleWord("BA", trail.vocabulary))
        assertEquals("bá, de bala", Pronunciation.fallbackText("BA", "BALA"))
        assertNull("nenhuma palavra do vocabulário começa com vogal", Pronunciation.exampleWord("A", trail.vocabulary))
        assertEquals("á", Pronunciation.fallbackText("A", null))
        assertEquals("B", Pronunciation.exampleWord("B", trail.vocabulary)!!.take(1))
    }

    @Test
    fun `every instruction that ends in a letter or syllable is split before it`() {
        assertEquals(Pronunciation.PromptParts("Toque na sílaba", "BE"), Pronunciation.promptTarget("Toque na sílaba BE", units))
        assertEquals(Pronunciation.PromptParts("Cadê a letra", "A"), Pronunciation.promptTarget("Cadê a letra A?", units))
        assertNull(Pronunciation.promptTarget("Monte a palavra bola", units))
        assertNull(Pronunciation.promptTarget("Abelha começa com qual letra?", units))

        var split = 0
        trail.phases.flatMap { it.questions }.forEach { question ->
            val parts = Pronunciation.promptTarget(question.prompt, units) ?: return@forEach
            split++
            assertEquals(question.prompt, question.answer, parts.target)
            assertEquals(QuestionType.JOIN == question.type || QuestionType.LISTEN_AND_TAP == question.type, true)
        }
        assertEquals("Toque na letra, Cadê a letra, Toque na sílaba e Junte as letras", 137, split)
    }

    @Test
    fun `the recording script uses the same pronunciation as the app`() {
        val script = File("../ferramentas/gerar_audios.py").readText(Charsets.UTF_8)
        fun table(name: String): Map<String, String> {
            val block = Regex("$name = \\{(.*?)\\}", RegexOption.DOT_MATCHES_ALL).find(script)!!.groupValues[1]
            return Regex("\"(\\w)\": \"([^\"]+)\"").findAll(block).associate { it.groupValues[1] to it.groupValues[2] }
        }
        val names = table("NOMES_DAS_LETRAS")
        val vowels = table("VOGAL_DA_SILABA")
        fun pythonHint(unit: String) = names[unit]
            ?: if (unit.length == 2 && unit.substring(1) in vowels) unit[0].lowercase() + vowels.getValue(unit.substring(1)) else unit.lowercase()

        units.forEach { unit -> assertEquals(unit, Pronunciation.hint(unit), pythonHint(unit)) }
    }
}
