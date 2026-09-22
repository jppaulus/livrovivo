package com.livrovivo.app.core.ai

import com.livrovivo.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class PictureBookLibraryTest {
    private fun brief(theme: ThemeOption, seed: String = "a") = StoryBrief(
        ChildProfile(id = "child", name = "Maya", ageGroup = AgeGroup.TODDLER.code),
        MagicalCompanion.ALL.first(), theme.title, theme.defaultObjective, 4, seed)

    @Test fun `eight themes have distinct conflicts and three different openings each`() {
        val titles = mutableSetOf<String>()
        val openings = mutableSetOf<String>()
        ThemeOption.PRESETS.forEach { theme ->
            val variants = listOf("a", "b", "c").map { seed ->
                val opening = PictureBookLibrary.opening(brief(theme, seed), theme.id)
                titles += opening.title
                assertFalse(opening.chapter.content.contains("aventura ia começar"))
                assertTrue(opening.chapter.content.split(Regex("\\s+")).size < 100)
                opening.chapter.content
            }
            assertEquals(3, variants.distinct().size)
            openings.addAll(variants)
        }
        assertEquals(8, titles.size)
        assertEquals(24, openings.size)
    }

    @Test fun `all sixty four branch combinations end with valid choices and personalized text`() {
        ThemeOption.PRESETS.forEach { theme ->
            for (path in 0..7) {
                val brief = brief(theme)
                val opening = PictureBookLibrary.opening(brief, theme.id)
                var story = Story(id = "book", childId = "child", title = opening.title, theme = theme.title,
                    objectiveType = theme.defaultObjective.code, chapters = listOf(opening.chapter), plannedChapters = 4)
                for (stage in 0..2) {
                    val last = story.lastChapter!!
                    val choice = last.choices[(path shr stage) and 1]
                    val chapter = PictureBookLibrary.continuation(brief, story, choice)
                    assertTrue(chapter.content.contains("Maya"))
                    assertFalse(chapter.content.contains("{n}"))
                    assertFalse(chapter.content.contains("{c}"))
                    assertEquals(stage + 2, chapter.index)
                    story = story.copy(chapters = story.chapters + chapter)
                }
                assertTrue(story.lastChapter!!.isEnding)
                assertTrue(story.lastChapter!!.choices.isEmpty())
            }
        }
    }
}
