package com.livrovivo.app.core.ai

import com.livrovivo.app.domain.model.AgeGroup
import com.livrovivo.app.domain.model.Chapter
import com.livrovivo.app.domain.model.ChildAppearance
import com.livrovivo.app.domain.model.ChildGender
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.Choice
import com.livrovivo.app.domain.model.MagicalCompanion
import com.livrovivo.app.domain.model.ObjectiveType
import com.livrovivo.app.domain.model.Story
import com.livrovivo.app.domain.model.Virtue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryPromptsTest {

    private val child = ChildProfile(
        id = "c1",
        name = "Theo",
        ageGroup = AgeGroup.TODDLER.code,
        interests = listOf("dinossauros"),
        gender = ChildGender.BOY,
        appearance = ChildAppearance(skinTone = "marrom", hairColor = "preto", hairStyle = "crespo", wearsGlasses = true)
    )
    private val brief = StoryBrief(child, MagicalCompanion.findById("luna"), "Terra dos dinossauros", ObjectiveType.AVENTURA, 4)

    @Test
    fun `opening prompt carries child data, age guidance and arc size`() {
        val prompt = StoryPrompts.openingPrompt(brief)
        assertTrue(prompt.contains("Theo"))
        assertTrue(prompt.contains("capítulo 1 de 4"))
        assertTrue(prompt.contains("80 a 120 palavras"))
        assertTrue(prompt.contains("masculino"))
        assertTrue(prompt.contains("characterSheet"))
        assertTrue(prompt.contains("coily afro-textured black hair"))
        assertTrue(prompt.contains("round glasses"))
    }

    @Test
    fun `continuation prompt includes history, the choice and the final chapter rules`() {
        val story = Story(
            id = "s",
            childId = "c1",
            title = "Theo e o Ovo Dourado",
            theme = "Terra dos dinossauros",
            objectiveType = "aventura",
            plannedChapters = 4,
            chapters = listOf(
                Chapter(1, "Capítulo um.", listOf(Choice("Seguir as pegadas", 2, Virtue.CURIOSIDADE)), selectedChoiceText = "Seguir as pegadas"),
                Chapter(2, "Capítulo dois.", listOf(Choice("Ajudar o filhote", 3, Virtue.EMPATIA)), selectedChoiceText = "Ajudar o filhote"),
                Chapter(3, "Capítulo três.", listOf(Choice("Tocar a bolha", 4, Virtue.CORAGEM)))
            )
        )
        val prompt = StoryPrompts.continuationPrompt(brief, story, Choice("Tocar a bolha", 4, Virtue.CORAGEM))

        assertTrue(prompt.contains("Capítulo dois."))
        assertTrue(prompt.contains("escolheu: \"Ajudar o filhote\""))
        assertTrue(prompt.contains("CAPÍTULO 4 DE 4"))
        assertTrue(prompt.contains("ÚLTIMO capítulo"))
        assertTrue(prompt.contains("curiosidade, empatia, coragem"))
    }

    @Test
    fun `middle chapters are not asked to end`() {
        val story = Story(
            id = "s", childId = "c1", title = "T", theme = "t", objectiveType = "aventura", plannedChapters = 6,
            chapters = listOf(Chapter(1, "Um.", listOf(Choice("A", 2))))
        )
        val prompt = StoryPrompts.continuationPrompt(brief, story, Choice("A", 2))
        assertFalse(prompt.contains("ÚLTIMO capítulo"))
        assertTrue(prompt.contains("\"isEnding\": false"))
    }

    @Test
    fun `free text inputs are sanitized`() {
        assertEquals("linha1 linha2 'aspas'", StoryPrompts.sanitizeInput("linha1\n\nlinha2 \"aspas\"", 100))
        assertEquals(5, StoryPrompts.sanitizeInput("abcdefghij", 5).length)
    }

    @Test
    fun `neutral gender asks the model to avoid gendered words`() {
        assertTrue(StoryPrompts.genderRule(ChildGender.NEUTRAL, "Alex").contains("evite adjetivos"))
    }

    @Test
    fun `schema requires the fields the reader depends on`() {
        val schema = StoryPrompts.schema(includeOpeningFields = true).toString()
        listOf("title", "characterSheet", "content", "narration", "choices", "isEnding", "illustrationPrompt", "virtue").forEach {
            assertTrue("schema sem $it", schema.contains("\"$it\""))
        }
    }
}
