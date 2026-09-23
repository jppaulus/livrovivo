package com.livrovivo.app.core.literacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderWordsTest {

    private val syllables = listOf("B", "C", "D", "L", "N").flatMap { c -> listOf("A", "E", "I", "O", "U").map { c + it } }.toSet()

    @Test
    fun `each word keeps its punctuation on screen but is read without it`() {
        val words = ReaderWords.of("LIA, É UM DADO? NÃO, É UMA BOCA!", "Lia", syllables)

        assertEquals(listOf("LIA,", "É", "UM", "DADO?", "NÃO,", "É", "UMA", "BOCA!"), words.map { it.display })
        assertEquals(listOf("LIA", "É", "UM", "DADO", "NÃO", "É", "UMA", "BOCA"), words.map { it.word })
    }

    @Test
    fun `words know their sentence for the narration highlight`() {
        val words = ReaderWords.of("LIA, É UM DADO? NÃO, É UMA BOCA!", "Lia", syllables)

        assertEquals(listOf(0, 0, 0, 0, 1, 1, 1, 1), words.map { it.sentence })
    }

    @Test
    fun `the child's name is the special word and is read whole`() {
        val words = ReaderWords.of("JOÃO TEM UM DADO.", "João", syllables)

        assertTrue(words.first().isName)
        assertNull(words.first().syllables)
        assertFalse(words.last().isName)
    }

    @Test
    fun `only words made of trail syllables split into syllables`() {
        val words = ReaderWords.of("A LUNA ESTÁ COM O DADO.", "Lia", syllables).associateBy { it.word }

        assertEquals(listOf("LU", "NA"), words.getValue("LUNA").syllables)
        assertEquals(listOf("DA", "DO"), words.getValue("DADO").syllables)
        assertNull("palavra de apoio é lida inteira", words.getValue("ESTÁ").syllables)
        assertNull(words.getValue("COM").syllables)
        assertNull("uma sílaba só não precisa separar", words.getValue("A").syllables)
    }
}
