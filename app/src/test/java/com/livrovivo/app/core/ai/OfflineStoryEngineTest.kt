package com.livrovivo.app.core.ai

import com.livrovivo.app.domain.model.AgeGroup
import com.livrovivo.app.domain.model.ChildGender
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.MagicalCompanion
import com.livrovivo.app.domain.model.ObjectiveType
import com.livrovivo.app.domain.model.Story
import com.livrovivo.app.domain.model.ThemeOption
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
            if (!PictureBookLibrary.recognizes(story)) {
                assertTrue("a continuação reage à escolha", next.content.contains(choice.text.replaceFirstChar { it.lowercase() }))
            } else {
                val other = OfflineStoryEngine.continuation(brief, themeId, story, last.choices[1 - pick])
                assertTrue("as escolhas precisam produzir acontecimentos diferentes", next.content != other.content)
            }
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
}
