package com.livrovivo.app.core.ai

import com.livrovivo.app.domain.model.AdventureMemory
import com.livrovivo.app.domain.model.AgeGroup
import com.livrovivo.app.domain.model.ChildGender
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.MagicalCompanion
import com.livrovivo.app.domain.model.ObjectiveType
import com.livrovivo.app.domain.model.Story
import com.livrovivo.app.domain.model.ThemeOption
import com.livrovivo.app.domain.model.Virtue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineStoryEngineTest {

    private val englishLeftovers = Regex("\\b(when|knowing|tunnel|awaited|the|and)\\b", RegexOption.IGNORE_CASE)

    private fun brief(theme: ThemeOption?, gender: ChildGender, age: AgeGroup, companion: MagicalCompanion) = StoryBrief(
        child = ChildProfile(
            id = "c1",
            name = "Maya",
            ageGroup = age.code,
            interests = listOf("mar"),
            companionId = companion.id,
            gender = gender
        ),
        companion = companion,
        theme = theme?.title ?: "Um dragão com medo de altura",
        objective = ObjectiveType.AVENTURA,
        plannedChapters = OfflineStoryEngine.plannedChapters(age)
    )

    /** Percorre a história inteira sempre escolhendo a opção [pick]. */
    private fun playThrough(brief: StoryBrief, themeId: String?, pick: Int): Story {
        val opening = OfflineStoryEngine.opening(brief, themeId)
        var story = Story(
            id = "s",
            childId = "c1",
            title = opening.title,
            theme = brief.theme,
            objectiveType = "aventura",
            chapters = listOf(opening.chapter),
            themeId = themeId,
            companionId = brief.companion.id,
            plannedChapters = opening.plannedChapters,
            isOffline = true
        )
        while (story.lastChapter?.isEnding != true) {
            val last = story.lastChapter!!
            assertEquals("capítulo ${last.index} deveria ter 2 escolhas", 2, last.choices.size)
            val choice = last.choices[pick]
            val next = OfflineStoryEngine.continuation(brief, themeId, story, choice)
            assertEquals(last.index + 1, next.index)
            assertTrue("a continuação reage à escolha", next.content.contains(choice.text.replaceFirstChar { it.lowercase() }))
            story = story.copy(
                chapters = story.chapters.map { if (it.index == last.index) it.copy(selectedChoiceText = choice.text) else it } + next
            )
            assertTrue("história não termina", story.chapters.size <= 10)
        }
        return story
    }

    @Test
    fun `every theme can be played to the end with both paths`() {
        val themes = ThemeOption.PRESETS + listOf<ThemeOption?>(null)
        themes.forEach { theme ->
            AgeGroup.entries.forEach { age ->
                listOf(0, 1).forEach { pick ->
                    val companion = MagicalCompanion.ALL[(age.ordinal + pick) % MagicalCompanion.ALL.size]
                    val b = brief(theme, ChildGender.entries[pick], age, companion)
                    val story = playThrough(b, theme?.id ?: ThemeOption.CUSTOM_ID, pick)

                    assertEquals(story.plannedChapters, story.chapters.size)
                    val ending = story.lastChapter!!
                    assertTrue(ending.isEnding)
                    assertTrue(ending.choices.isEmpty())
                    story.chapters.forEach { chapter ->
                        assertTrue(chapter.content.contains("Maya"))
                        assertFalse("sobras em inglês em ${theme?.id}: ${chapter.content}", englishLeftovers.containsMatchIn(chapter.content))
                        assertFalse(chapter.content.contains("{") || chapter.content.contains("$"))
                    }
                }
            }
        }
    }

    @Test
    fun `ending celebrates the virtues chosen along the way`() {
        val b = brief(ThemeOption.findById("medo_do_escuro"), ChildGender.GIRL, AgeGroup.KID, MagicalCompanion.ALL.first())
        val story = playThrough(b, "medo_do_escuro", pick = 0)
        assertTrue(story.lastChapter!!.content.contains("Você mostrou"))
    }

    @Test
    fun `virtue summary lists virtues in natural portuguese`() {
        val summary = OfflineStoryEngine.virtueSummary(
            listOf(com.livrovivo.app.domain.model.Virtue.CORAGEM, com.livrovivo.app.domain.model.Virtue.EMPATIA)
        )
        assertEquals("Você mostrou muita coragem e um coração enorme.", summary)
    }

    // --- memórias de aventuras anteriores ---

    private fun memory(companionId: String) = AdventureMemory(
        storyId = "antiga",
        title = "Maya e a Lanterna das Estrelas",
        theme = "Hora de Dormir sem Medo do Escuro",
        companionId = companionId,
        choices = listOf("Cantar uma canção de ninar para acalmar o céu"),
        virtues = listOf(Virtue.CALMA),
        isFinished = true,
        updatedAt = 1L,
        highlightChoice = "Cantar uma canção de ninar para acalmar o céu",
        highlightVirtue = Virtue.CALMA
    )

    @Test
    fun `the companion remembers a past choice right before the new choices`() {
        val bento = MagicalCompanion.findById("bento")
        val b = brief(ThemeOption.findById("fundo_do_mar"), ChildGender.GIRL, AgeGroup.KID, bento)
            .copy(memories = listOf(memory(companionId = "bento")))

        val content = OfflineStoryEngine.opening(b, "fundo_do_mar").chapter.content

        val lastParagraph = content.split("\n\n").last()
        assertTrue(lastParagraph.startsWith("— Lembra quando você escolheu cantar uma canção de ninar para acalmar o céu?"))
        assertTrue(lastParagraph.contains("perguntou Bento, todo orgulhoso"))
    }

    @Test
    fun `a new companion only heard about the old adventure`() {
        val luna = MagicalCompanion.findById("luna")
        val b = brief(ThemeOption.findById("fundo_do_mar"), ChildGender.GIRL, AgeGroup.KID, luna)
            .copy(memories = listOf(memory(companionId = "bento")))

        val content = OfflineStoryEngine.opening(b, "fundo_do_mar").chapter.content

        assertTrue(content.contains("Sabia que Bento me contou de quando você escolheu cantar uma canção de ninar"))
        assertTrue(content.contains("disse Luna, toda animada"))
        assertFalse(content.contains("Lembra quando"))
    }

    @Test
    fun `without memories the opening is unchanged`() {
        val b = brief(ThemeOption.findById("fundo_do_mar"), ChildGender.GIRL, AgeGroup.KID, MagicalCompanion.ALL.first())

        val semMemoria = OfflineStoryEngine.opening(b, "fundo_do_mar").chapter.content
        val comMemoriaVazia = OfflineStoryEngine.opening(b.copy(memories = emptyList()), "fundo_do_mar").chapter.content

        assertEquals(semMemoria, comMemoriaVazia)
        assertFalse(semMemoria.contains("Lembra quando"))
    }
}
