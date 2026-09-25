package com.livrovivo.app.core.audio

import com.livrovivo.app.core.ai.AzureSpeechService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AzureNarrationTest {

    private fun request(persona: VoicePersona, text: String, script: String? = null) =
        NarrationRequest(text = text, script = script, persona = persona)

    @Test
    fun `each narrator has its own brazilian azure voice`() {
        val voices = VoicePersona.entries.map { it.azureVoice }
        assertTrue(voices.all { it.startsWith("pt-BR-") })
        assertEquals(voices.size, voices.toSet().size)
    }

    @Test
    fun `every narrator has a recorded greeting for the narrator picker`() {
        val folder = File("src/main/assets/voz/narradores")
        VoicePersona.entries.forEach { persona ->
            assertTrue("falta ${persona.id}.ogg (rode ferramentas/gravar_narradores.py)", File(folder, "${persona.id}.ogg").length() > 1_000)
        }
    }

    @Test
    fun `ssml uses the narrator voice and the approved speed`() {
        val ssml = AzureNarrationEngine.buildSsml(request(VoicePersona.URSINHO, "Era uma vez."))
        assertTrue(ssml.contains("<voice name=\"pt-BR-ValerioNeural\">"))
        assertTrue(ssml.contains("<prosody rate=\"${AzureNarrationEngine.RATE}\">Era uma vez.</prosody>"))
        assertTrue(ssml.contains("xml:lang=\"pt-BR\""))
    }

    @Test
    fun `gemini emotion tags are not read aloud`() {
        val ssml = AzureNarrationEngine.buildSsml(
            request(VoicePersona.FADA, "Oi! Vamos brincar?", script = "[warmly] Oi! [excited] Vamos brincar?")
        )
        assertFalse(ssml.contains("warmly"))
        assertFalse(ssml.contains("["))
        assertTrue(ssml.contains("Oi! Vamos brincar?"))
    }

    @Test
    fun `story text cannot break the ssml`() {
        val ssml = AzureSpeechService.ssml("pt-BR-LeticiaNeural", "Lia & Bento <3 \"pipoca\" d'água", "-8%")
        assertTrue(ssml.contains("Lia &amp; Bento &lt;3 &quot;pipoca&quot; d&apos;água"))
        assertEquals(1, Regex("<prosody").findAll(ssml).count())
    }
}
