package com.livrovivo.app.core.illustration

import com.livrovivo.app.core.ai.AiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IllustrationPauseTest {

    private val hour = 60 * 60 * 1000L
    private val key = IllustrationPause.fingerprint("AIza-chave-dos-pais")

    @Test
    fun `account problems pause the illustrations, one-off problems do not`() {
        assertTrue(IllustrationPause.pausesFor(AiException.Kind.BILLING))
        assertTrue(IllustrationPause.pausesFor(AiException.Kind.QUOTA))
        assertTrue(IllustrationPause.pausesFor(AiException.Kind.INVALID_KEY))
        assertFalse(IllustrationPause.pausesFor(AiException.Kind.NETWORK))
        assertFalse(IllustrationPause.pausesFor(AiException.Kind.SERVER))
        assertFalse("filtro de segurança vale só para aquela página", IllustrationPause.pausesFor(AiException.Kind.BLOCKED))
        assertFalse(IllustrationPause.pausesFor(AiException.Kind.NOT_CONFIGURED))
    }

    @Test
    fun `a billing pause lasts a day and then the app tries again`() {
        val raw = IllustrationPause.encode(AiException.Kind.BILLING, key, since = 1_000L)
        assertEquals(IllustrationPause(AiException.Kind.BILLING, 1_000L), IllustrationPause.decode(raw, key, now = 1_000L + 23 * hour))
        assertNull(IllustrationPause.decode(raw, key, now = 1_000L + 24 * hour))
    }

    @Test
    fun `a quota pause lasts only an hour`() {
        val raw = IllustrationPause.encode(AiException.Kind.QUOTA, key, since = 0L)
        assertEquals(AiException.Kind.QUOTA, IllustrationPause.decode(raw, key, now = 59 * 60 * 1000L)?.reason)
        assertNull(IllustrationPause.decode(raw, key, now = hour))
    }

    @Test
    fun `a new key lifts the pause`() {
        val raw = IllustrationPause.encode(AiException.Kind.BILLING, key, since = 0L)
        val otherKey = IllustrationPause.fingerprint("AIza-chave-nova")
        assertNotEquals(key, otherKey)
        assertNull(IllustrationPause.decode(raw, otherKey, now = 1L))
    }

    @Test
    fun `broken or missing records never pause`() {
        assertNull(IllustrationPause.decode(null, key, now = 0L))
        assertNull(IllustrationPause.decode("lixo", key, now = 0L))
        assertNull(IllustrationPause.decode("NAO_EXISTE|$key|0", key, now = 0L))
        assertNull(IllustrationPause.decode("BILLING|$key|abc", key, now = 0L))
        // Relógio que voltou no tempo.
        assertNull(IllustrationPause.decode(IllustrationPause.encode(AiException.Kind.BILLING, key, since = 5_000L), key, now = 1_000L))
    }

    @Test
    fun `the stored fingerprint does not reveal the key`() {
        assertFalse(key.contains("AIza"))
        assertEquals(16, key.length)
        assertEquals(key, IllustrationPause.fingerprint("  AIza-chave-dos-pais "))
    }
}
