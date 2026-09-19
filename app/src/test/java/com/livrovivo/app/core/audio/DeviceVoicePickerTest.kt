package com.livrovivo.app.core.audio

import com.livrovivo.app.core.settings.AppSettings
import com.livrovivo.app.core.settings.VoiceEngineChoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceVoicePickerTest {

    /** As vozes em português do emulador do projeto, já agrupadas e ordenadas como o app faz. */
    private val emulatorSpeakers = listOf("pt-br-language", "pt-br-x-afs", "pt-br-x-ptd", "pt-br-x-pte")

    @Test
    fun `local and online versions of a voice are the same speaker`() {
        assertEquals("pt-br-x-pte", DeviceVoicePicker.speakerId("pt-br-x-pte-network"))
        assertEquals("pt-br-x-pte", DeviceVoicePicker.speakerId("pt-br-x-pte-local"))
        assertEquals("pt-br-language", DeviceVoicePicker.speakerId("pt-BR-language"))
    }

    @Test
    fun `the captain keeps the approved voice even when the voice list changes`() {
        val captain = VoicePersona.AVENTUREIRO
        // Logo depois de ligar o aparelho o Google ainda não lista "pt-BR-language": pela posição, o
        // Capitão trocava de voz no meio da sessão. Com a voz fixa, é sempre a mesma.
        val afterBoot = emulatorSpeakers - "pt-br-language"
        assertEquals("pt-br-x-pte", emulatorSpeakers[DeviceVoicePicker.pick(emulatorSpeakers, captain.deviceVoice, captain.ordinal)])
        assertEquals("pt-br-x-pte", afterBoot[DeviceVoicePicker.pick(afterBoot, captain.deviceVoice, captain.ordinal)])
    }

    @Test
    fun `without the google voices each narrator still gets one by position`() {
        val samsung = listOf("pt-br-samsung-a", "pt-br-samsung-b")
        assertEquals(1, DeviceVoicePicker.pick(samsung, VoicePersona.AVENTUREIRO.deviceVoice, ordinal = 3))
        assertEquals(0, DeviceVoicePicker.pick(samsung, VoicePersona.FADA.deviceVoice, ordinal = 0))
        assertEquals(-1, DeviceVoicePicker.pick(emptyList(), VoicePersona.FADA.deviceVoice, ordinal = 0))
    }

    @Test
    fun `every narrator has a google voice that matches the character`() {
        val female = setOf(GOOGLE_FEMALE_A, GOOGLE_FEMALE_B)
        assertEquals(GOOGLE_FEMALE_B, VoicePersona.AVENTUREIRO.deviceVoice)
        assertEquals(GOOGLE_MALE, VoicePersona.URSINHO.deviceVoice)
        assertTrue(VoicePersona.FADA.deviceVoice in female)
        assertTrue("a vovó não pode ter voz de homem", VoicePersona.VOVO.deviceVoice in female)
        VoicePersona.entries.forEach { persona ->
            assertTrue(persona.deviceVoice in emulatorSpeakers)
        }
    }

    @Test
    fun `the device voice is the default narration, with or without ai`() {
        assertEquals(VoiceEngineChoice.DEVICE, AppSettings().voiceEngine)
        assertEquals(VoiceEngineChoice.DEVICE, VoiceEngineChoice.fromId(null))
        assertEquals(VoiceEngineChoice.DEVICE, VoiceEngineChoice.fromId("algo-antigo"))
        assertEquals(VoiceEngineChoice.GEMINI, VoiceEngineChoice.fromId("gemini"))
    }
}
