package com.livrovivo.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundSoundTest {

    @Test
    fun `the reader button goes through the three options and back`() {
        assertEquals(BackgroundSound.LULLABY, BackgroundSound.AMBIENCE.next())
        assertEquals(BackgroundSound.OFF, BackgroundSound.LULLABY.next())
        assertEquals(BackgroundSound.AMBIENCE, BackgroundSound.OFF.next())
    }

    @Test
    fun `new families start with the sounds of the page`() {
        assertEquals(BackgroundSound.AMBIENCE, BackgroundSound.fromStored(id = null, legacyLullaby = null))
    }

    @Test
    fun `who had turned the lullaby on keeps it`() {
        assertEquals(BackgroundSound.LULLABY, BackgroundSound.fromStored(id = null, legacyLullaby = true))
        assertEquals(BackgroundSound.AMBIENCE, BackgroundSound.fromStored(id = null, legacyLullaby = false))
    }

    @Test
    fun `a saved choice wins over the old setting`() {
        assertEquals(BackgroundSound.OFF, BackgroundSound.fromStored(id = "desligado", legacyLullaby = true))
        assertEquals(BackgroundSound.AMBIENCE, BackgroundSound.fromStored(id = "algo-antigo", legacyLullaby = null))
    }
}
