package com.livrovivo.app.core.literacy

import com.livrovivo.app.domain.model.LiteracyKnowledge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DecodableValidatorTest {

    /** A criança fez Sílabas com B, C e D. */
    private val knowledge = LiteracyKnowledge(
        syllables = setOf("BA", "BE", "BI", "BO", "BU", "CA", "CE", "CI", "CO", "CU", "DA", "DE", "DI", "DO", "DU")
    )
    private val support = setOf("O", "A", "E", "É", "UM", "UMA", "DE", "NO", "NA", "COM", "NÃO", "SIM", "TEM", "ESTÁ", "SÃO")

    private fun invalid(text: String, name: String = "Lia") =
        DecodableValidator.invalidWords(text, knowledge, support, name)

    @Test
    fun `decodable words split into learned syllables`() {
        assertTrue(DecodableValidator.isDecodable("BOCA", knowledge.syllables))
        assertTrue(DecodableValidator.isDecodable("DADO", knowledge.syllables))
        assertTrue(DecodableValidator.isDecodable("CABIDE", knowledge.syllables))
        assertFalse(DecodableValidator.isDecodable("", knowledge.syllables))
    }

    @Test
    fun `accented support words are readable`() {
        assertEquals(emptyList<String>(), invalid("O DADO É DE LIA. NÃO ESTÁ NA BOCA."))
    }

    @Test
    fun `accents typed as separate marks still match the support words`() {
        val decomposed = "O DADO ESTÁ NA BOCA." // "Á" escrito como A + acento solto
        assertEquals(emptyList<String>(), invalid(decomposed))
    }

    @Test
    fun `accented names are readable`() {
        assertEquals(emptyList<String>(), invalid("JOÃO TEM UM DADO.", name = "João"))
        assertEquals(emptyList<String>(), invalid("ZÉ TEM UM DADO.", name = "zé"))
    }

    @Test
    fun `every part of a compound name is readable`() {
        assertEquals(emptyList<String>(), invalid("MARIA E CLARA TEM UM DADO.", name = "Maria Clara"))
        assertEquals(emptyList<String>(), invalid("ANA TEM UM DADO.", name = "Ana-Luísa"))
        assertEquals(listOf("ANA", "LUÍSA"), DecodableValidator.nameParts("Ana-Luísa"))
        assertEquals(listOf("LIA"), DecodableValidator.nameParts(" lia2 "))
    }

    @Test
    fun `the name of another child is not readable`() {
        assertEquals(listOf("CAIO"), invalid("CAIO TEM UM DADO.", name = "Lia"))
    }

    @Test
    fun `odd length words are never decodable`() {
        assertFalse(DecodableValidator.isDecodable("BOB", knowledge.syllables))
        assertEquals(listOf("DOCES"), invalid("LIA TEM UM DOCES."))
    }

    @Test
    fun `a syllable the child has not learned makes the word unreadable`() {
        assertEquals(listOf("GATO", "BOLA"), invalid("O GATO E A BOLA SÃO DE LIA."))
    }

    @Test
    fun `allowed punctuation stuck to the word is ignored`() {
        assertEquals(emptyList<String>(), invalid("LIA, É UM DADO? NÃO, É UMA BOCA! SIM."))
        assertEquals(listOf("DADO", "BOCA", "LIA", "DOCE"), DecodableValidator.words("DADO, BOCA! LIA? DOCE."))
    }

    @Test
    fun `other punctuation keeps the word invalid`() {
        assertEquals(listOf("BOCA;", "\"DADO\""), invalid("A BOCA; O \"DADO\"."))
    }

    @Test
    fun `lowercase words are not accepted`() {
        assertEquals(listOf("dado", "Lia"), invalid("O dado É DE Lia."))
    }

    @Test
    fun `each invalid word is reported once`() {
        assertEquals(listOf("GATO"), invalid("O GATO. O GATO. O GATO."))
    }

    @Test
    fun `words learned in a word phase are readable`() {
        val learned = knowledge.copy(words = setOf("GATO"))
        assertEquals(emptyList<String>(), DecodableValidator.invalidWords("O GATO É DE LIA.", learned, support, "Lia"))
    }

    private val goodPages = listOf(
        "LIA TEM UM DADO.",
        "O DADO É DE LIA.",
        "LIA, É UM DADO? NÃO, É UMA BOCA!",
        "O DADO E O DOCE. SÃO DE LIA!"
    )

    @Test
    fun `a well formed book has no format problems`() {
        assertEquals(emptyList<String>(), DecodableValidator.formatProblems("O DADO DE LIA", goodPages))
    }

    @Test
    fun `a book needs exactly 4 pages`() {
        assertTrue(DecodableValidator.formatProblems("O DADO", goodPages.take(3)).any { "3 páginas" in it })
    }

    @Test
    fun `sentences must have 3 to 7 words`() {
        val short = DecodableValidator.pageProblems(1, "LIA TEM.")
        val long = DecodableValidator.pageProblems(1, "LIA TEM UM DADO E UM DOCE BOM.")
        assertTrue(short.any { "2 palavras" in it })
        assertTrue(long.any { "8 palavras" in it })
    }

    @Test
    fun `pages have 1 or 2 sentences`() {
        val problems = DecodableValidator.pageProblems(2, "LIA TEM UM DADO. O DADO É DE LIA. LIA TEM UMA BOCA.")
        assertTrue(problems.any { "3 frases" in it })
    }

    @Test
    fun `every sentence ends with a period, exclamation or question mark`() {
        assertTrue(DecodableValidator.pageProblems(1, "LIA TEM UM DADO").any { "sem ponto final" in it })
    }

    @Test
    fun `lowercase letters and other signs break the format`() {
        assertTrue(DecodableValidator.pageProblems(1, "Lia tem um dado.").any { "minúscula" in it })
        assertTrue(DecodableValidator.pageProblems(1, "LIA TEM 2 DADOS.").any { "'2'" in it })
        assertTrue(DecodableValidator.pageProblems(1, "LIA TEM UM DADO; O DADO.").any { "';'" in it })
    }
}
