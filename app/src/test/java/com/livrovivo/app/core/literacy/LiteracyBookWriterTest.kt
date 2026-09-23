package com.livrovivo.app.core.literacy

import com.livrovivo.app.core.ai.AiException
import com.livrovivo.app.core.ai.StoryPrompts
import com.livrovivo.app.domain.model.ChildGender
import com.livrovivo.app.domain.model.ChildProfile
import kotlinx.coroutines.test.runTest
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
import java.io.File

class LiteracyBookWriterTest {

    private val trail = TrailParser.parse(File("src/main/assets/${TrailParser.ASSET_PATH}").readText(Charsets.UTF_8))
    private val child = ChildProfile("c1", "Lia", "3-5", companionId = "luna", gender = ChildGender.GIRL)

    private fun knowledgeAfter(syllablePhases: Int) = LiteracyRules.knowledge(
        trail,
        (trail.module("vogais")!!.phases + trail.module("consoantes")!!.phases +
            trail.module("silabas")!!.phases.take(syllablePhases)).map { it.id }.toSet()
    )

    /** Com B, C e D a criança lê DADO, BOCA, DEDO e DOCE. */
    private val firstBook = knowledgeAfter(3)

    private fun book(title: String, vararg pages: String) = buildJsonObject {
        put("title", title)
        putJsonArray("pages") {
            pages.forEachIndexed { index, text ->
                addJsonObject {
                    put("text", text)
                    put("illustrationPrompt", "Page ${index + 1}: Lia smiles holding a candy.")
                }
            }
        }
    }

    private val validBook = book(
        "O DOCE DE LIA",
        "LIA TEM UM DOCE.",
        "O DOCE É DE LIA.",
        "O DOCE ESTÁ NA BOCA? SIM!  SIM, ESTÁ NA BOCA.",
        "LIA TEM UM DADO E UM DOCE."
    )

    /** IA de mentira: devolve as respostas da fila e guarda os pedidos. */
    private class FakeAi(private val available: Boolean = true, vararg answers: Any) : BookAi {
        private val queue = ArrayDeque(answers.toList())
        val prompts = mutableListOf<String>()
        override suspend fun isAvailable() = available
        override suspend fun generateJson(systemPrompt: String, userPrompt: String, schema: JsonObject): JsonObject {
            prompts += userPrompt
            return when (val next = queue.removeFirst()) {
                is Exception -> throw next
                else -> next as JsonObject
            }
        }
    }

    private suspend fun write(ai: BookAi?) = LiteracyBookWriter(ai).write(trail, firstBook, child, seed = 1, focusSyllables = setOf("DA", "DO"))!!

    @Test
    fun `without AI the offline engine writes the book`() = runTest {
        assertTrue(write(ai = null).isOffline)
        val unavailable = FakeAi(available = false)
        assertTrue(write(unavailable).isOffline)
        assertTrue(unavailable.prompts.isEmpty())
    }

    @Test
    fun `a readable AI book is accepted on the first try`() = runTest {
        // "SIM!" sozinho seria uma frase de 1 palavra; esta versão tem frases de 3 a 7.
        val ai = FakeAi(true, book("O DOCE DE LIA", "LIA TEM UM DOCE.", "O DOCE É DE LIA.", "O DOCE ESTÁ NA BOCA.", "LIA TEM UM DADO E UM DOCE."))

        val written = write(ai)

        assertFalse(written.isOffline)
        assertEquals(1, ai.prompts.size)
        assertEquals("O DOCE DE LIA", written.book.title)
        assertEquals("LIA TEM UM DOCE.", written.book.pages.first().text)
        assertEquals("Page 1: Lia smiles holding a candy.", written.book.pages.first().illustrationPrompt)
        assertEquals(listOf("DOCE", "DOCE", "DOCE", "DADO"), written.book.pages.map { it.mainWord.word })
        assertTrue(written.book.characterSheet!!.startsWith("LIA: a 4-year-old girl"))
    }

    @Test
    fun `lowercase letters and extra spaces from the AI are cleaned, not refused`() = runTest {
        val ai = FakeAi(true, book("o doce de lia", "lia tem   um doce.", " o doce é de lia. ", "o doce está na boca.", "lia tem um dado e um doce."))

        val written = write(ai)

        assertFalse(written.isOffline)
        assertEquals(listOf("LIA TEM UM DOCE.", "O DOCE É DE LIA."), written.book.pages.take(2).map { it.text })
    }

    @Test
    fun `unreadable words make the writer ask once more, naming them`() = runTest {
        val withCat = book("O GATO DE LIA", "LIA TEM UM GATO.", "O GATO É DE LIA.", "O GATO ESTÁ NA BOCA.", "LIA TEM UM DADO.")
        val good = book("O DOCE DE LIA", "LIA TEM UM DOCE.", "O DOCE É DE LIA.", "O DOCE ESTÁ NA BOCA.", "LIA TEM UM DADO E UM DOCE.")
        val ai = FakeAi(true, withCat, good)

        val written = write(ai)

        assertFalse(written.isOffline)
        assertEquals(2, ai.prompts.size)
        assertFalse(ai.prompts[0].contains("RECUSADA"))
        assertTrue(ai.prompts[1].contains("não podem aparecer: GATO."))
    }

    @Test
    fun `format problems are sent back on the second try`() = runTest {
        val threePages = book("O DOCE DE LIA", "LIA TEM UM DOCE.", "O DOCE É DE LIA.", "LIA TEM UM DADO.")
        val good = book("O DOCE DE LIA", "LIA TEM UM DOCE.", "O DOCE É DE LIA.", "O DOCE ESTÁ NA BOCA.", "LIA TEM UM DADO E UM DOCE.")
        val ai = FakeAi(true, threePages, good)

        assertFalse(write(ai).isOffline)
        assertTrue(ai.prompts[1].contains("Formato: o livro tem 3 páginas"))
    }

    @Test
    fun `two refused answers fall back to the offline book`() = runTest {
        val withCat = book("O GATO DE LIA", "LIA TEM UM GATO.", "O GATO É DE LIA.", "O GATO ESTÁ NA BOCA.", "LIA TEM UM DADO.")
        val ai = FakeAi(true, withCat, withCat)

        val written = write(ai)

        assertTrue(written.isOffline)
        assertEquals("tenta só uma vez de novo", 2, ai.prompts.size)
    }

    @Test
    fun `an AI error goes straight to the offline book`() = runTest {
        val ai = FakeAi(true, AiException(AiException.Kind.QUOTA, "cota"))

        val written = write(ai)

        assertTrue(written.isOffline)
        assertEquals(1, ai.prompts.size)
    }

    @Test
    fun `the closed list has only what the child can read`() {
        val request = LiteracyBookWriter().requestFor(trail, firstBook, child, focusSyllables = setOf("DA", "DO"))!!

        assertEquals(listOf("BOCA", "DADO", "DEDO", "DOCE").sorted(), request.words.map { it.word }.sorted())
        assertEquals("LIA", request.childName)
        assertNull("LU e NA ainda não foram aprendidas", request.companionName)
        assertEquals(listOf("DADO", "DEDO", "DOCE").sorted(), request.focusWords.sorted())

        val prompt = StoryPrompts.decodablePrompt(request)
        assertTrue(prompt.contains("A BOCA"))
        assertTrue(prompt.contains("O DADO"))
        assertTrue(prompt.contains("ESTÁ"))
        assertFalse(prompt.contains("GATO"))
        assertFalse(prompt.contains("LUNA"))
    }

    @Test
    fun `the companion joins the list once the child can read its name`() {
        val everything = knowledgeAfter(13)

        val request = LiteracyBookWriter().requestFor(trail, everything, child, emptySet())!!

        assertEquals("LUNA", request.companionName)
        assertTrue(StoryPrompts.decodablePrompt(request).contains("LUNA pode aparecer"))
    }

    @Test
    fun `the decodable prompt reuses the child safety rules`() {
        assertTrue(StoryPrompts.DECODABLE_SYSTEM_PROMPT.contains(StoryPrompts.SAFETY_RULES))
        assertTrue(StoryPrompts.SYSTEM_PROMPT.contains(StoryPrompts.SAFETY_RULES + "\n\nESTILO"))
        assertTrue(StoryPrompts.SYSTEM_PROMPT.startsWith("Você é o \"Livro Vivo\", um premiado autor"))
    }

    @Test
    fun `a book without any picture word is refused`() {
        val writer = LiteracyBookWriter()
        val noPictures = book("É DE LIA", "É DE LIA.", "SIM, É DE LIA.", "NÃO, NÃO É.", "SIM, SIM, SIM.")

        assertNull(writer.parse(noPictures, trail.vocabulary))
        assertNotNull(writer.parse(validBook, trail.vocabulary))
    }
}
