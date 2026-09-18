package com.livrovivo.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** O que o companheiro consegue lembrar das aventuras anteriores da criança. */
class AdventureMemoryTest {

    private fun story(
        id: String,
        updatedAt: Long,
        choices: List<Choice?>,
        completed: Boolean = true,
        companionId: String = "bento"
    ) = Story(
        id = id,
        childId = "leo",
        title = "Aventura $id",
        theme = "floresta",
        objectiveType = "aventura",
        companionId = companionId,
        isCompleted = completed,
        updatedAt = updatedAt,
        chapters = choices.mapIndexed { i, chosen ->
            Chapter(
                index = i + 1,
                content = "Página ${i + 1}",
                choices = listOfNotNull(chosen, Choice("Outra opção", i + 2, Virtue.CALMA)),
                selectedChoiceText = chosen?.text
            )
        }
    )

    private val corajosa = Choice("Acender a lanterna mágica", 2, Virtue.CORAGEM)
    private val bondosa = Choice("Abraçar a estrelinha", 3, Virtue.EMPATIA)

    @Test
    fun `the most recent adventures come first and are limited`() {
        val stories = listOf(
            story("antiga", updatedAt = 1_000, choices = listOf(corajosa)),
            story("recente", updatedAt = 3_000, choices = listOf(corajosa)),
            story("meio", updatedAt = 2_000, choices = listOf(corajosa)),
            story("velhissima", updatedAt = 500, choices = listOf(corajosa))
        )

        val memories = AdventureMemory.recent(stories, limit = 3)

        assertEquals(listOf("recente", "meio", "antiga"), memories.map { it.storyId })
    }

    @Test
    fun `the story being written now is not remembered`() {
        val stories = listOf(
            story("atual", updatedAt = 9_000, choices = listOf(corajosa)),
            story("ontem", updatedAt = 1_000, choices = listOf(corajosa))
        )

        val memories = AdventureMemory.recent(stories, excludeStoryId = "atual")

        assertEquals(listOf("ontem"), memories.map { it.storyId })
    }

    @Test
    fun `stories without any choice have nothing to remember`() {
        val stories = listOf(story("so-comecou", updatedAt = 1_000, choices = listOf(null), completed = false))

        assertTrue(AdventureMemory.recent(stories).isEmpty())
    }

    @Test
    fun `the highlight is the last choice, usually the climax`() {
        val memory = AdventureMemory.from(story("s", updatedAt = 1, choices = listOf(corajosa, bondosa)))

        assertEquals(listOf("Acender a lanterna mágica", "Abraçar a estrelinha"), memory.choices)
        assertEquals("Abraçar a estrelinha", memory.highlightChoice)
        assertEquals(Virtue.EMPATIA, memory.highlightVirtue)
        assertEquals(listOf(Virtue.CORAGEM, Virtue.EMPATIA), memory.virtues)
    }

    @Test
    fun `a preset theme is remembered by its title`() {
        val memory = AdventureMemory.from(
            story("s", updatedAt = 1, choices = listOf(corajosa)).copy(themeId = "medo_do_escuro", theme = "x")
        )

        assertEquals("Hora de Dormir sem Medo do Escuro", memory.theme)
    }

    @Test
    fun `an infinitive choice fits in the middle of a sentence`() {
        val memory = AdventureMemory.from(story("s", updatedAt = 1, choices = listOf(corajosa)))

        // "você escolheu acender a lanterna mágica"
        assertEquals("acender a lanterna mágica", memory.highlightAsClause())
    }

    @Test
    fun `short verbs like ir are also infinitives`() {
        val memory = AdventureMemory.from(
            story("s", updatedAt = 1, choices = listOf(Choice("Ir até a caverna brilhante", 2, Virtue.CURIOSIDADE)))
        )

        assertEquals("ir até a caverna brilhante", memory.highlightAsClause())
    }

    @Test
    fun `a choice that is not an infinitive goes in quotes to keep the sentence right`() {
        val memory = AdventureMemory.from(
            story("s", updatedAt = 1, choices = listOf(Choice("O dragão voa para casa!", 2, Virtue.CORAGEM)))
        )

        assertEquals("‘O dragão voa para casa’", memory.highlightAsClause())
    }

    @Test
    fun `long choices are shortened on a word boundary`() {
        val longa = Choice(
            "Pedir ajuda ao guardião da floresta para abrir o portal antigo das estrelas cadentes",
            2,
            Virtue.COOPERACAO
        )
        val clause = AdventureMemory.from(story("s", updatedAt = 1, choices = listOf(longa))).highlightAsClause(40)!!

        assertTrue(clause.endsWith("…"))
        assertTrue(clause.length <= 41)
        assertFalse("cortou no meio de uma palavra: $clause", clause.removeSuffix("…").endsWith(" "))
        assertTrue(clause.startsWith("pedir ajuda"))
    }

    @Test
    fun `no choice means no clause`() {
        val memory = AdventureMemory.from(story("s", updatedAt = 1, choices = listOf(null)))

        assertNull(memory.highlightAsClause())
    }
}
