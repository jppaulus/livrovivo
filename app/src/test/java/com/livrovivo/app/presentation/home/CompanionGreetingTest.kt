package com.livrovivo.app.presentation.home

import com.livrovivo.app.domain.model.AdventureMemory
import com.livrovivo.app.domain.model.MagicalCompanion
import com.livrovivo.app.domain.model.Virtue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionGreetingTest {

    private val bento = MagicalCompanion.findById("bento")
    private val luna = MagicalCompanion.findById("luna")
    private val now = 10 * 24 * 60 * 60 * 1000L
    private val umDia = 24 * 60 * 60 * 1000L

    private fun memory(
        updatedAt: Long = now - umDia,
        companionId: String = "bento",
        choice: String? = "Acender a lanterna mágica",
        virtue: Virtue? = Virtue.CORAGEM
    ) = AdventureMemory(
        storyId = "s",
        title = "Leo e a Lanterna das Estrelas",
        theme = "Hora de dormir",
        companionId = companionId,
        choices = listOfNotNull(choice),
        virtues = listOfNotNull(virtue),
        isFinished = true,
        updatedAt = updatedAt,
        highlightChoice = choice,
        highlightVirtue = virtue
    )

    @Test
    fun `without a memory the greeting is the same as before`() {
        assertEquals(
            "Bom dia! Que tal uma aventura para começar o dia com um sorriso?",
            CompanionGreeting.forHome(hour = 8, companion = bento, memory = null, now = now)
        )
        assertEquals(
            "Boa tarde! Estou com a imaginação a mil. Vamos criar uma história?",
            CompanionGreeting.forHome(hour = 15, companion = bento, memory = null, now = now)
        )
        assertEquals(
            "Boa noite! Eu já estou de pijama. Que tal uma história calminha antes de dormir?",
            CompanionGreeting.forHome(hour = 21, companion = bento, memory = null, now = now)
        )
    }

    @Test
    fun `a recent adventure is remembered by the child's choice`() {
        val greeting = CompanionGreeting.forHome(hour = 21, companion = bento, memory = memory(), now = now)

        assertEquals(
            "Boa noite! Ainda lembro quando você escolheu acender a lanterna mágica. Que coragem! " +
                "Que tal uma história calminha antes de dormir?",
            greeting
        )
    }

    @Test
    fun `a companion that was not there only heard about it`() {
        val greeting = CompanionGreeting.forHome(hour = 21, companion = luna, memory = memory(), now = now)

        assertTrue(greeting.contains("Bento me contou que você escolheu acender a lanterna mágica."))
        assertFalse(greeting.contains("Ainda lembro"))
    }

    @Test
    fun `an old adventure is not brought up, and nobody is blamed for the absence`() {
        val greeting = CompanionGreeting.forHome(
            hour = 21,
            companion = bento,
            memory = memory(updatedAt = now - 10 * umDia),
            now = now
        )

        assertEquals("Boa noite! Eu já estou de pijama. Que tal uma história calminha antes de dormir?", greeting)
        listOf("saudade", "sumiu", "triste", "esqueceu", "demorou").forEach { culpa ->
            assertFalse("a saudação não pode cobrar a criança: $culpa", greeting.contains(culpa, ignoreCase = true))
        }
    }

    @Test
    fun `each virtue gets its own compliment and the invitation is kept`() {
        val greetings = Virtue.entries.map { virtue ->
            CompanionGreeting.forHome(hour = 10, companion = bento, memory = memory(virtue = virtue), now = now)
        }

        assertEquals("elogios repetidos entre virtudes", greetings.size, greetings.toSet().size)
        greetings.forEach { assertTrue(it.endsWith("Que tal uma aventura para começar o dia com um sorriso?")) }
    }
}
