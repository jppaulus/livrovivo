package com.livrovivo.app.core.ai

import com.livrovivo.app.core.ai.JsonUtils.string
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonUtilsTest {

    @Test
    fun `parses plain json`() {
        assertEquals("Oi", JsonUtils.extractJsonObject("""{"content":"Oi"}""")?.string("content"))
    }

    @Test
    fun `parses json wrapped in markdown fences`() {
        val raw = "```json\n{\"title\": \"Leo e a Lua\"}\n```"
        assertEquals("Leo e a Lua", JsonUtils.extractJsonObject(raw)?.string("title"))
    }

    @Test
    fun `extracts object surrounded by text and braces inside strings`() {
        val raw = "Aqui está a história: {\"content\": \"Ele disse {olá} e sorriu\", \"n\": 1} Espero que goste!"
        assertEquals("Ele disse {olá} e sorriu", JsonUtils.extractJsonObject(raw)?.string("content"))
    }

    @Test
    fun `returns null when there is no json`() {
        assertNull(JsonUtils.extractJsonObject("Desculpe, não consegui."))
    }
}
