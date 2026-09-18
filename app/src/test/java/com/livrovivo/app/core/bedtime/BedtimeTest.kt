package com.livrovivo.app.core.bedtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** A noite começa no horário dos pais e atravessa a meia-noite até as 6h. */
class BedtimeTest {

    private val zone = ZoneId.of("America/Sao_Paulo")
    private val padrao = BedtimeSettings(startMinutes = 19 * 60 + 30)

    private fun at(day: Int, hour: Int, minute: Int = 0): Instant =
        LocalDateTime.of(2026, 9, day, hour, minute).atZone(zone).toInstant()

    private fun ms(day: Int, hour: Int, minute: Int = 0): Long = at(day, hour, minute).toEpochMilli()

    @Test
    fun `bedtime starts at the chosen time and crosses midnight until six`() {
        assertFalse(Bedtime.isBedtime(padrao, at(18, 19, 29), zone))
        assertTrue(Bedtime.isBedtime(padrao, at(18, 19, 30), zone))
        assertTrue(Bedtime.isBedtime(padrao, at(18, 23, 59), zone))
        assertTrue(Bedtime.isBedtime(padrao, at(19, 2, 0), zone))
        assertTrue(Bedtime.isBedtime(padrao, at(19, 5, 59), zone))
        assertFalse(Bedtime.isBedtime(padrao, at(19, 6, 0), zone))
        assertFalse(Bedtime.isBedtime(padrao, at(19, 12, 0), zone))
    }

    @Test
    fun `with the ritual turned off it is never bedtime`() {
        val desligado = BedtimeSettings(startMinutes = null, storiesPerNight = 1)

        assertFalse(Bedtime.isBedtime(desligado, at(18, 22, 0), zone))
        assertEquals(BedtimeMode.OFF, Bedtime.mode(desligado, listOf(ms(18, 21, 0)), at(18, 22, 0), zone))
    }

    @Test
    fun `after midnight the night started the day before`() {
        assertEquals(at(18, 19, 30), Bedtime.nightStartedAt(padrao, at(19, 2, 0), zone))
        assertNull(Bedtime.nightStartedAt(padrao, at(19, 15, 0), zone))
    }

    @Test
    fun `the app wakes up at the next six o'clock`() {
        assertEquals(at(19, 6, 0), Bedtime.wakeUpAt(at(18, 20, 0), zone))
        assertEquals(at(19, 6, 0), Bedtime.wakeUpAt(at(19, 3, 0), zone))
    }

    @Test
    fun `only stories finished tonight count`() {
        val fins = listOf(
            ms(18, 20, 0), // noite anterior
            ms(19, 15, 0), // tarde de hoje, antes da hora de dormir
            ms(19, 20, 10),
            ms(19, 21, 0)
        )

        assertEquals(2, Bedtime.storiesTonight(fins, padrao, at(19, 21, 30), zone))
    }

    @Test
    fun `stories on both sides of midnight belong to the same night`() {
        val fins = listOf(ms(19, 20, 0), ms(20, 0, 30))

        assertEquals(2, Bedtime.storiesTonight(fins, padrao, at(20, 1, 0), zone))
    }

    @Test
    fun `without a limit bedtime only offers the goodnight`() {
        val fins = listOf(ms(19, 20, 0), ms(19, 20, 30), ms(19, 21, 0))

        assertEquals(BedtimeMode.OFFER, Bedtime.mode(padrao, fins, at(19, 21, 30), zone))
    }

    @Test
    fun `reaching the nightly limit makes the goodnight the only way`() {
        val umaPorNoite = padrao.copy(storiesPerNight = 1)
        val duasPorNoite = padrao.copy(storiesPerNight = 2)
        val fins = listOf(ms(19, 20, 0))

        assertEquals(BedtimeMode.REQUIRED, Bedtime.mode(umaPorNoite, fins, at(19, 20, 5), zone))
        assertEquals(BedtimeMode.OFFER, Bedtime.mode(duasPorNoite, fins, at(19, 20, 5), zone))
    }

    @Test
    fun `during the day nothing changes, whatever was read`() {
        val fins = listOf(ms(19, 10, 0), ms(19, 11, 0))

        assertEquals(BedtimeMode.OFF, Bedtime.mode(padrao.copy(storiesPerNight = 1), fins, at(19, 12, 0), zone))
    }

    @Test
    fun `endings are kept per child and only the most recent ones`() {
        var fins = emptyMap<String, List<Long>>()
        repeat(15) { i -> fins = Bedtime.withEnding(fins, "leo", i.toLong()) }
        fins = Bedtime.withEnding(fins, "mia", 99L)

        assertEquals((5L..14L).toList(), fins["leo"])
        assertEquals(listOf(99L), fins["mia"])
    }

    @Test
    fun `times are shown the Brazilian way`() {
        assertEquals("19h30", Bedtime.label(19 * 60 + 30))
        assertEquals("20h", Bedtime.label(20 * 60))
        assertEquals("18h30", Bedtime.label(18 * 60 + 30))
    }
}
