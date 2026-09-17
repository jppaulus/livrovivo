package com.livrovivo.app.core.audio

import com.livrovivo.app.core.ai.ElevenLabsVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class AudioSynthesisTest {

    @Test
    fun `wav header describes 24kHz mono 16-bit pcm`() {
        val header = ByteBuffer.wrap(WavWriter.header(pcmBytes = 48_000, sampleRate = 24_000)).order(ByteOrder.LITTLE_ENDIAN)
        val bytes = header.array()
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
        assertEquals(36 + 48_000, header.getInt(4))
        assertEquals(1, header.getShort(22).toInt()) // canais
        assertEquals(24_000, header.getInt(24)) // sample rate
        assertEquals(48_000, header.getInt(28)) // byte rate
        assertEquals(16, header.getShort(34).toInt())
        assertEquals(48_000, header.getInt(40))
    }

    @Test
    fun `lullaby renders a soft seamless loop`() {
        val pcm = LullabySynth.render()
        val seconds = pcm.size / LullabySynth.SAMPLE_RATE.toDouble()
        assertTrue("duração de ${seconds}s", seconds in 30.0..60.0)
        val peak = pcm.maxOf { abs(it.toInt()) }
        assertTrue("sem distorção", peak < Short.MAX_VALUE * 0.6)
        assertTrue("som audível", peak > Short.MAX_VALUE * 0.3)
        assertTrue("começa em silêncio para o loop não estalar", abs(pcm.first().toInt()) < 50)
        assertTrue("termina em silêncio", abs(pcm.last().toInt()) < 50)
    }

    @Test
    fun `gemini voice prompt follows the director structure and keeps the transcript`() {
        val prompt = GeminiNarrationEngine.buildPrompt(
            NarrationRequest(
                text = "Era uma vez.",
                script = "[whispers] Era uma vez.",
                persona = VoicePersona.VOVO,
                mood = "sonolento",
                listenerAge = "4-year-old child"
            )
        )
        assertTrue(prompt.contains("# AUDIO PROFILE: Vovó Contadora"))
        assertTrue(prompt.contains("Brazilian Portuguese"))
        assertTrue(prompt.contains("bedtime"))
        assertTrue(prompt.trimEnd().endsWith("### TRANSCRIPT\n[whispers] Era uma vez."))
    }

    @Test
    fun `elevenlabs voice picker prefers matching gender, portuguese and storytelling`() {
        val voices = listOf(
            ElevenLabsVoice("1", "Radio Man", "male", "middle_aged", "american", "news", "deep", listOf("en")),
            ElevenLabsVoice("2", "Clara", "female", "young", "brazilian", "narrative_story", "warm and gentle", listOf("pt-br")),
            ElevenLabsVoice("3", "Jess", "female", "young", "american", "social_media", "upbeat", listOf("en"))
        )
        assertEquals("2", ElevenLabsNarrationEngine.pickVoice(voices, VoicePersona.FADA)?.id)
        assertEquals("1", ElevenLabsNarrationEngine.pickVoice(voices, VoicePersona.URSINHO)?.id)
        assertEquals(null, ElevenLabsNarrationEngine.pickVoice(emptyList(), VoicePersona.FADA))
    }
}
