package com.livrovivo.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** O álbum: o que se ganha lendo, o que é novidade e o que nunca sai do álbum. */
class StickerAlbumTest {

    private val zone = ZoneId.of("America/Sao_Paulo")

    private fun day(n: Int): Long = LocalDate.of(2026, 9, n).atTime(20, 0).atZone(zone).toInstant().toEpochMilli()

    /** Uma história com as escolhas (virtudes) feitas e, se [finished], a página final. */
    private fun story(
        id: String,
        themeId: String? = "fundo_do_mar",
        virtues: List<Virtue> = emptyList(),
        finished: Boolean = true,
        markedCompleted: Boolean = finished,
        createdAt: Long = day(1)
    ): Story {
        val chosen = virtues.mapIndexed { i, virtue ->
            val choice = Choice("Escolha $i", i + 2, virtue)
            Chapter(index = i + 1, content = "Página ${i + 1}", choices = listOf(choice), selectedChoiceText = choice.text)
        }
        val ending = if (finished) listOf(Chapter(index = chosen.size + 1, content = "Fim", isEnding = true)) else emptyList()
        return Story(
            id = id, childId = "mia", title = "História $id", theme = "tema", objectiveType = "aventura",
            themeId = themeId, chapters = chosen + ending, isCompleted = markedCompleted, createdAt = createdAt
        )
    }

    private fun earned(vararg stories: Story) = StickerAlbum.earned(StickerAlbum.tally(stories.toList(), zone))

    // --- catálogo ---

    @Test
    fun `the album has 34 numbered stickers in three pages`() {
        val catalog = StickerAlbum.CATALOG

        assertEquals(34, catalog.size)
        assertEquals((1..34).toList(), catalog.map { it.number })
        assertEquals(catalog.size, catalog.map { it.id }.toSet().size)
        assertEquals(9, catalog.count { it.page == StickerPage.MUNDOS })
        assertEquals(18, catalog.count { it.page == StickerPage.VIRTUDES })
        assertEquals(7, catalog.count { it.page == StickerPage.CONQUISTAS })
    }

    @Test
    fun `every world and every virtue level has its sticker`() {
        ThemeOption.PRESETS.forEach { theme -> assertTrue(StickerAlbum.find(StickerAlbum.worldId(theme.id)) != null) }
        Virtue.entries.forEach { virtue ->
            MedalLevel.entries.forEach { level -> assertTrue(StickerAlbum.find(StickerAlbum.medalId(virtue, level)) != null) }
        }
    }

    @Test
    fun `nothing in the album depends on streaks or buying`() {
        StickerAlbum.CATALOG.forEach { sticker ->
            listOf("seguid", "sem parar", "compr", "premium", "sorte").forEach { word ->
                assertFalse("${sticker.id}: $word", sticker.howToEarn.contains(word, ignoreCase = true))
            }
        }
    }

    // --- o que se ganha ---

    @Test
    fun `finishing a story earns its world, an unfinished one does not`() {
        assertTrue("mundo_fundo_do_mar" in earned(story("a")))
        assertFalse("mundo_fundo_do_mar" in earned(story("a", finished = false)))
    }

    @Test
    fun `the final page counts even before the story is marked completed`() {
        assertTrue("mundo_fundo_do_mar" in earned(story("a", finished = true, markedCompleted = false)))
    }

    @Test
    fun `a theme invented by the child earns the invented world`() {
        assertTrue("mundo_tema_livre" in earned(story("a", themeId = null)))
        assertTrue("mundo_tema_livre" in earned(story("b", themeId = ThemeOption.CUSTOM_ID)))
    }

    @Test
    fun `virtue medals grow with choices, even in unfinished stories`() {
        val coragem = { n: Int -> story("s$n", virtues = List(n) { Virtue.CORAGEM }, finished = false) }

        assertTrue(StickerAlbum.medalId(Virtue.CORAGEM, MedalLevel.BRONZE) in earned(coragem(1)))
        assertFalse(StickerAlbum.medalId(Virtue.CORAGEM, MedalLevel.PRATA) in earned(coragem(4)))
        assertTrue(StickerAlbum.medalId(Virtue.CORAGEM, MedalLevel.PRATA) in earned(coragem(5)))
        assertTrue(StickerAlbum.medalId(Virtue.CORAGEM, MedalLevel.OURO) in earned(coragem(12)))
    }

    @Test
    fun `reading milestones`() {
        val five = (1..5).map { story("s$it", createdAt = day(it)) }.toTypedArray()
        val allVirtues = story("v", virtues = Virtue.entries)
        val worlds = listOf("fundo_do_mar", "floresta_encantada", "aventura_espacial", "terra_dinossauros")
            .map { story(it, themeId = it) }.toTypedArray()

        assertTrue("primeira_aventura" in earned(story("a")))
        assertTrue("cinco_aventuras" in earned(*five))
        assertFalse("dez_aventuras" in earned(*five))
        assertTrue("arco_iris" in earned(allVirtues))
        assertTrue("explorador" in earned(*worlds))
    }

    @Test
    fun `seven different days count, not seven in a row`() {
        val spread = listOf(1, 3, 4, 8, 12, 20, 29).map { story("d$it", createdAt = day(it)) }
        val sameDay = (1..7).map { story("m$it", createdAt = day(5)) }

        assertTrue("sete_dias" in earned(*spread.toTypedArray()))
        assertFalse("sete_dias" in earned(*sameDay.toTypedArray()))
    }

    // --- colar figurinhas ---

    @Test
    fun `a new child's first story brings its stickers as news`() {
        val first = story("a", virtues = listOf(Virtue.CORAGEM, Virtue.EMPATIA))

        val result = StickerAlbum.collect(collected = emptySet(), stories = listOf(first), currentStoryId = "a", zone = zone)

        val ids = result.newlyCollected.map { it.id }.toSet()
        assertTrue("mundo_fundo_do_mar" in ids)
        assertTrue("primeira_aventura" in ids)
        assertTrue(StickerAlbum.medalId(Virtue.CORAGEM, MedalLevel.BRONZE) in ids)
        assertTrue(StickerAlbum.medalId(Virtue.EMPATIA, MedalLevel.BRONZE) in ids)
    }

    @Test
    fun `a child who already read before the album gets news only from the current story`() {
        val old = (1..7).map { story("old$it", themeId = "floresta_encantada", createdAt = day(it)) }
        val current = story("now", themeId = "fundo_do_mar", createdAt = day(10))

        val result = StickerAlbum.collect(collected = null, stories = old + current, currentStoryId = "now", zone = zone)

        // O mundo novo é da história de agora; "primeira aventura" e o mundo antigo entram sem festa.
        assertEquals(listOf("mundo_fundo_do_mar"), result.newlyCollected.map { it.id })
        assertTrue("primeira_aventura" in result.collected)
        assertTrue("mundo_floresta_encantada" in result.collected)
    }

    @Test
    fun `showing the same ending again brings no news`() {
        val stories = listOf(story("a"))
        val first = StickerAlbum.collect(emptySet(), stories, "a", zone)

        val again = StickerAlbum.collect(first.collected, stories, "a", zone)

        assertTrue(again.newlyCollected.isEmpty())
    }

    @Test
    fun `stickers never leave the album, even when stories are deleted`() {
        val collected = StickerAlbum.collect(emptySet(), listOf(story("a")), "a", zone).collected

        val album = StickerAlbum.view(collected, stories = emptyList(), zone = zone)

        assertTrue(album.slots.first { it.sticker.id == "mundo_fundo_do_mar" }.collected)
        assertTrue(album.slots.first { it.sticker.id == "primeira_aventura" }.collected)
    }

    @Test
    fun `choosing another ending does not take a medal back`() {
        val before = story("a", virtues = listOf(Virtue.CALMA))
        val collected = StickerAlbum.collect(emptySet(), listOf(before), "a", zone).collected
        // Voltou a página e escolheu outro caminho: a escolha de calma sumiu da história.
        val rewritten = story("a", virtues = listOf(Virtue.CURIOSIDADE))

        val album = StickerAlbum.view(collected, listOf(rewritten), zone)

        assertTrue(album.slots.first { it.sticker.id == StickerAlbum.medalId(Virtue.CALMA, MedalLevel.BRONZE) }.collected)
    }

    // --- como o álbum aparece ---

    @Test
    fun `empty slots show how far the child is`() {
        val album = StickerAlbum.view(
            collected = null,
            stories = listOf(story("a", virtues = List(3) { Virtue.EMPATIA }, finished = false)),
            zone = zone
        )
        val prata = album.slots.first { it.sticker.id == StickerAlbum.medalId(Virtue.EMPATIA, MedalLevel.PRATA) }
        val mundo = album.slots.first { it.sticker.id == "mundo_terra_dinossauros" }

        assertFalse(prata.collected)
        assertEquals(3 to 5, prata.progress)
        assertNull("mundos não têm contagem", mundo.progress)
        assertEquals(1, album.collectedCount) // só o bronze de empatia
    }
}
