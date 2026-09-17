package com.livrovivo.app.core.ai

import com.livrovivo.app.domain.model.Choice
import com.livrovivo.app.domain.model.Virtue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterSanitizerTest {

    private val content = """
A lua espiava pela janela e o quarto estava quietinho. Só o relógio fazia tic-tac, tic-tac.

— Psiu! — sussurrou Bento. — Aquela sombra não é monstro nenhum. Olha só: ela tem orelhinhas e está bocejando!

A sombra se espreguiçou e disse que precisava de ajuda para acender as estrelas de novo, uma por uma, com muito carinho.
""".trim()

    private fun response(
        choices: List<Pair<String, String>> = listOf("Acender a lanterna" to "coragem", "Cantar uma canção" to "calma"),
        narration: String? = "[whispers] $content",
        isEnding: Boolean = false,
        newWords: List<String> = listOf("espreguiçou", "palavraInventada")
    ): JsonObject = buildJsonObject {
        put("content", "Capítulo 3: $content")
        narration?.let { put("narration", it) }
        putJsonArray("choices") {
            choices.forEach { (text, virtue) ->
                addJsonObject {
                    put("text", text)
                    put("virtue", virtue)
                }
            }
        }
        put("isEnding", isEnding)
        put("illustrationPrompt", "A child and a baby dragon looking at a friendly shadow shaped like a bunny")
        put("mood", "sonolento")
        putJsonArray("newWords") { newWords.forEach { add(it) } }
    }

    private val fallback = { listOf(Choice("Seguir em frente", 99, Virtue.CORAGEM), Choice("Pedir ajuda", 99, Virtue.COOPERACAO)) }

    @Test
    fun `middle chapter keeps two choices pointing to the next page`() {
        val chapter = ChapterSanitizer.toChapter(response(), index = 3, isFinal = false, fallbackChoices = fallback)

        assertEquals(2, chapter.choices.size)
        assertTrue(chapter.choices.all { it.targetChapterIndex == 4 })
        assertEquals(Virtue.CORAGEM, chapter.choices[0].virtue)
        assertFalse(chapter.isEnding)
        assertFalse("remove o prefixo 'Capítulo 3:'", chapter.content.startsWith("Capítulo"))
        assertEquals("sonolento", chapter.mood)
    }

    @Test
    fun `final chapter never has choices even if the model sends them`() {
        val chapter = ChapterSanitizer.toChapter(response(isEnding = false), index = 5, isFinal = true, fallbackChoices = fallback)

        assertTrue(chapter.isEnding)
        assertTrue(chapter.choices.isEmpty())
    }

    @Test
    fun `missing choices are completed with fallback choices`() {
        val chapter = ChapterSanitizer.toChapter(
            response(choices = listOf("Única opção" to "empatia")),
            index = 2,
            isFinal = false,
            fallbackChoices = fallback
        )

        assertEquals(listOf("Única opção", "Seguir em frente"), chapter.choices.map { it.text })
        assertTrue(chapter.choices.all { it.targetChapterIndex == 3 })
    }

    @Test
    fun `narration with tags that matches the text is accepted`() {
        val chapter = ChapterSanitizer.toChapter(response(), index = 1, isFinal = false, fallbackChoices = fallback)
        assertNotNull(chapter.narrationScript)
    }

    @Test
    fun `narration that rewrites the text is rejected`() {
        val chapter = ChapterSanitizer.toChapter(
            response(narration = "[excited] Era uma vez uma história completamente diferente do texto exibido na tela."),
            index = 1,
            isFinal = false,
            fallbackChoices = fallback
        )
        assertNull(chapter.narrationScript)
    }

    @Test
    fun `new words must exist in the chapter text`() {
        val chapter = ChapterSanitizer.toChapter(response(), index = 1, isFinal = false, fallbackChoices = fallback)
        assertEquals(listOf("espreguiçou"), chapter.newWords)
    }

    @Test(expected = AiException::class)
    fun `too short chapters are rejected`() {
        ChapterSanitizer.toChapter(buildJsonObject { put("content", "Curto demais.") }, index = 1, isFinal = false, fallbackChoices = fallback)
    }

    @Test
    fun `audio tags are removed for voices that do not support them`() {
        assertEquals(
            "Oi! Vamos brincar?",
            ChapterSanitizer.stripAudioTags("[warmly] Oi! [excited] Vamos brincar?")
        )
    }
}
