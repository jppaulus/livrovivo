package com.livrovivo.app.data.model

import com.livrovivo.app.domain.model.Chapter
import com.livrovivo.app.domain.model.ChildAppearance
import com.livrovivo.app.domain.model.ChildGender
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.Choice
import com.livrovivo.app.domain.model.DecodableBook
import com.livrovivo.app.domain.model.DecodablePage
import com.livrovivo.app.domain.model.VocabularyWord
import com.livrovivo.app.domain.model.Story
import com.livrovivo.app.domain.model.Virtue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MappersTest {

    @Test
    fun `child profile mapper preserves all properties`() {
        val domainProfile = ChildProfile(
            id = "child-123",
            name = "Sofia",
            ageGroup = "6-8",
            interests = listOf("dinossauros", "espaço"),
            companionId = "luna",
            gender = ChildGender.GIRL,
            appearance = ChildAppearance(skinTone = "morena", hairColor = "preto", hairStyle = "cacheado", wearsGlasses = true)
        )

        val mappedBack = domainProfile.toEntity().toDomain()

        assertEquals(domainProfile, mappedBack)
    }

    @Test
    fun `chapter mapper keeps choices, virtues, chosen path and new words`() {
        val chapter = Chapter(
            index = 2,
            content = "Texto do capítulo",
            choices = listOf(
                Choice("Seguir as pegadas", 3, Virtue.CURIOSIDADE),
                Choice("Chamar os amigos", 3, Virtue.COOPERACAO)
            ),
            isEnding = false,
            sceneImagePrompt = "A child following glowing footprints",
            imagePath = "/data/page_2.jpg",
            narrationScript = "[warmly] Texto do capítulo",
            selectedChoiceText = "Chamar os amigos",
            newWords = listOf("cintilante"),
            mood = "aventura"
        )

        val mappedBack = chapter.toEntity("story-1").toDomain()

        assertEquals(chapter, mappedBack)
        assertEquals(Virtue.COOPERACAO, mappedBack.selectedChoice?.virtue)
    }

    @Test
    fun `story mapper sorts chapters and keeps progress fields`() {
        val story = Story(
            id = "s-1",
            childId = "c-1",
            title = "A Caverna dos Cristais",
            theme = "aventura",
            objectiveType = "cognitivo",
            themeId = "floresta_encantada",
            companionId = "pipoca",
            characterSheet = "Sofia: a girl with curly hair",
            plannedChapters = 5,
            isCompleted = false,
            lastReadChapter = 2,
            createdAt = 10L,
            updatedAt = 20L
        )
        val chapters = listOf(
            Chapter(index = 2, content = "Dois").toEntity(story.id),
            Chapter(index = 1, content = "Um").toEntity(story.id)
        )

        val mapped = story.toEntity().toDomain(chapters)

        assertEquals(listOf(1, 2), mapped.chapters.map { it.index })
        assertEquals(story.copy(chapters = mapped.chapters), mapped)
    }

    @Test
    fun `an eu leio book becomes a 4 page story without choices`() {
        val child = ChildProfile(id = "c-1", name = "Lia", ageGroup = "3-5", companionId = "luna")
        val dado = VocabularyWord("DADO", listOf("DA", "DO"), "img_dado", "O")
        val doce = VocabularyWord("DOCE", listOf("DO", "CE"), "img_doce", "O")
        val book = DecodableBook(
            title = "O DOCE DE LIA",
            pages = listOf(
                DecodablePage("LIA TEM UM DOCE.", doce),
                DecodablePage("O DOCE É DE LIA.", doce),
                DecodablePage("LIA TEM UM DADO.", dado),
                DecodablePage("O DOCE E O DADO. SÃO DE LIA!", dado)
            )
        )

        val story = book.toEuLeioStory("s-1", child, moduleId = "silabas", isOffline = true, createdAt = 5L)

        assertTrue(story.isEuLeio)
        assertEquals("O DOCE DE LIA", story.title)
        assertEquals(4, story.plannedChapters)
        assertEquals(listOf(1, 2, 3, 4), story.chapters.map { it.index })
        assertTrue(story.chapters.all { it.choices.isEmpty() })
        assertEquals(listOf(false, false, false, true), story.chapters.map { it.isEnding })
        assertEquals(listOf("DOCE", "DOCE", "DADO", "DADO"), story.chapters.map { it.newWords.single() })
        assertEquals("silabas", story.themeId)
        assertEquals("Lia", story.childSnapshot?.name)
        assertTrue(story.isOffline)

        val saved = story.toEntity().toDomain(story.chapters.map { it.toEntity(story.id) })
        assertEquals(Story.KIND_EU_LEIO, saved.kind)
        assertEquals(story.chapters, saved.chapters)
    }

    @Test
    fun `corrupted json columns fall back to empty lists`() {
        val entity = ChapterEntity(
            storyId = "s",
            chapterIndex = 1,
            content = "x",
            choicesJson = "{quebrado",
            isEnding = false,
            audioUrl = null,
            sceneImagePrompt = null,
            newWordsJson = "nao-json"
        )
        val chapter = entity.toDomain()
        assertTrue(chapter.choices.isEmpty())
        assertTrue(chapter.newWords.isEmpty())
    }
}
