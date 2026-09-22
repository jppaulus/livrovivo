package com.livrovivo.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrationChunkerTest {

    private val page = """
A lua espiava pela janela e o quarto de Maya estava quietinho, quietinho. Só o relógio fazia tic-tac, tic-tac.

De repente, uma sombra comprida apareceu na parede. Maya puxou o cobertor até o nariz. Mas então uma luzinha dourada piscou: era Luna, a corujinha sábia!

— Psiu, Maya! — sussurrou Luna. — Aquela sombra não é monstro nenhum. Olha só: ela tem orelhinhas de coelho e está bocejando!

A sombra se espreguiçou e falou com uma voz macia de travesseiro que parecia vir de muito longe, de um lugar feito de sonhos e algodão.
""".trim()

    private val script = page
        .replace("A lua espiava", "[warmly] A lua espiava")
        .replace("— Psiu, Maya!", "[whispers] — Psiu, Maya!")

    @Test
    fun `first part is short so the narration starts fast`() {
        val chunks = NarrationChunker.chunk(page, script = null)
        assertTrue("deveria dividir a página", chunks.size >= 2)
        val first = chunks.first()
        assertTrue("primeira parte curta", first.displayRange.count() <= 260)
        assertEquals(0, first.displayRange.first)
    }

    @Test
    fun `parts cover the whole page in order and never cut a sentence`() {
        val chunks = NarrationChunker.chunk(page, script = null)
        var previousEnd = -1
        chunks.forEach { chunk ->
            assertTrue("partes em ordem", chunk.displayRange.first > previousEnd)
            previousEnd = chunk.displayRange.last
            val text = page.substring(chunk.displayRange.first, chunk.displayRange.last + 1)
            assertTrue("parte termina em pontuação: '$text'", text.trimEnd().last() in setOf('.', '!', '?', '…', '"'))
        }
        assertEquals(page.length - 1, chunks.last().displayRange.last)
    }

    @Test
    fun `audio tags from the narration script stay in the spoken text`() {
        val chunks = NarrationChunker.chunk(page, script)
        assertTrue(chunks.first().speakText.contains("[warmly]"))
        assertTrue(chunks.any { it.speakText.contains("[whispers]") })
        // O texto exibido continua limpo.
        assertFalse(page.contains("[warmly]"))
    }

    @Test
    fun `script with a different number of paragraphs is ignored instead of misaligned`() {
        val brokenScript = "[excited] Texto completamente diferente em um só parágrafo."
        val chunks = NarrationChunker.chunk(page, brokenScript)
        assertFalse(chunks.any { it.speakText.contains("[excited]") })
        assertEquals(page.substring(chunks.first().displayRange.first, chunks.first().displayRange.last + 1), chunks.first().speakText)
    }

    @Test
    fun `short page stays in a single part`() {
        val short = "Era uma vez uma estrelinha muito pequena que morava atrás da lua."
        val chunks = NarrationChunker.chunk(short, script = null)
        assertEquals(1, chunks.size)
        assertEquals(short, chunks.first().speakText)
    }

    @Test
    fun `very long paragraph is split by sentences`() {
        val longParagraph = (1..12).joinToString(" ") { "Esta é a frase número $it desta história bem comprida sobre estrelas." }
        val chunks = NarrationChunker.chunk(longParagraph, script = null)
        assertTrue(chunks.size >= 2)
        chunks.forEach { assertTrue(it.displayRange.count() <= 520) }
    }

    @Test
    fun `sentence offsets of each part map back to the full page`() {
        val chunks = NarrationChunker.chunk(page, script = null)
        val sentences = NarrationTimeline.build(page).sentences
        // Cada parte começa exatamente no início de uma frase (o destaque depende disso).
        chunks.forEach { chunk ->
            assertTrue(sentences.any { it.first == chunk.displayRange.first })
        }
    }
}
