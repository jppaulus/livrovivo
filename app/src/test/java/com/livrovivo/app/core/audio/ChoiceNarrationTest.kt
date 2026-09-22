package com.livrovivo.app.core.audio

import com.livrovivo.app.domain.model.Chapter
import com.livrovivo.app.domain.model.Choice
import org.junit.Assert.*
import org.junit.Test

class ChoiceNarrationTest {
    private val chapter = Chapter(
        index = 1,
        content = "Lia encontrou uma trilha. O que havia lá?",
        narrationScript = "[warmly] Lia encontrou uma trilha. O que havia lá?",
        choices = listOf(Choice("Acender a lanterna", 2), Choice("Pedir ajuda. Ir juntos!", 2))
    )

    @Test fun `choices follow the page and retain the expressive script`() {
        val plan = ChoiceNarration.build(chapter, includeChoices = true)
        assertTrue(plan.text.startsWith(chapter.content))
        assertTrue(plan.script!!.startsWith(chapter.narrationScript!!))
        assertTrue(plan.script!!.contains("Opção 1: Acender a lanterna."))
        val sentences = NarrationTimeline.build(plan.text).sentences
        plan.choiceSentences.forEachIndexed { choice, range ->
            assertTrue(range.first >= 0)
            range.forEach { assertEquals(choice, plan.choiceAt(it)) }
            assertTrue(plan.text.substring(sentences[range.first]).startsWith("Opção ${choice + 1}:"))
        }
        assertEquals(-1, plan.choiceAt(0))
        assertEquals(-1, plan.choiceAt(-1))
    }

    @Test fun `replaying options does not read the whole page`() {
        val plan = ChoiceNarration.build(chapter, true, choicesOnly = true)
        assertFalse(plan.text.contains(chapter.content))
        assertNull(plan.script)
        assertEquals(2, plan.choiceSentences.size)
        assertEquals(1, plan.choiceAt(plan.choiceSentences.last().last))
    }

    @Test fun `endings and already chosen pages do not offer choices`() {
        for (plan in listOf(ChoiceNarration.build(chapter.copy(isEnding = true), true),
            ChoiceNarration.build(chapter, false))) {
            assertEquals(chapter.content, plan.text)
            assertTrue(plan.choiceSentences.isEmpty())
        }
    }

    @Test fun `spoken options remain aligned through audio chunking`() {
        val plan = ChoiceNarration.build(chapter, true)
        val chunks = NarrationChunker.chunk(plan.text, plan.script)
        assertTrue(chunks.joinToString(" ") { it.speakText }.contains("Opção 2:"))
        assertEquals(plan.text.lastIndex, chunks.last().displayRange.last)
    }

    @Test fun `action symbols support accents and have a neutral fallback`() {
        assertEquals("🌳", Choice("Encontrar a árvore", 2).actionSymbol(0))
        assertEquals("🔦", chapter.choices.first().actionSymbol(0))
        assertEquals("2️⃣", Choice("Esperar um pouco", 2).actionSymbol(1))
    }
}
