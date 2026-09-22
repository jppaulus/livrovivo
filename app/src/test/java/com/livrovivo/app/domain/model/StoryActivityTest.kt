package com.livrovivo.app.domain.model

import org.junit.Assert.*
import org.junit.Test

class StoryActivityTest {
    private val choices = listOf(Choice("Acender a lanterna", 2, Virtue.CORAGEM), Choice("Cantar", 2, Virtue.CALMA))
    private val page = Chapter(1, "Uma trilha.", choices, newWords = listOf("trilha"))
    private val story = Story("s", "a", "Livro", "Floresta", "aventura", chapters = listOf(page))

    @Test fun `generation alone is not reading or presented vocabulary`() {
        val activity = StoryActivity.from(listOf(story))
        assertEquals(0, activity.pagesOpened)
        assertTrue(activity.vocabulary.isEmpty())
    }

    @Test fun `saved copies do not double count reading but retain distinct decisions`() {
        val original = story.copy(chapters = listOf(page.copy(openedAt = 1, selectedChoiceText = choices[0].text)))
        val copy = original.copy(id = "copy", originId = "s")
        val alternate = copy.copy(id = "alternate", chapters = listOf(page.copy(openedAt = 2, selectedChoiceText = choices[1].text)))
        val activity = StoryActivity.from(listOf(original, copy, alternate))
        assertEquals(1, activity.pagesOpened)
        assertEquals(2, activity.choices.size)
        assertEquals(listOf("trilha"), activity.vocabulary)
    }

    @Test fun `rewound original does not erase choices or opened pages from saved paths`() {
        val saved = story.copy(id = "copy", originId = "s", chapters = listOf(page.copy(openedAt = 1, selectedChoiceText = choices[0].text)))
        val activity = StoryActivity.from(listOf(story, saved))
        assertEquals(1, activity.pagesOpened)
        assertEquals(choices[0], activity.choices.single())
        assertEquals(0, StoryActivity.from(listOf(saved.copy(deletedAt = 1))).pagesOpened)
    }
}
