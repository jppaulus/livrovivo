package com.livrovivo.app.presentation.bedtime

import com.livrovivo.app.core.ai.ChapterSanitizer
import com.livrovivo.app.domain.model.MagicalCompanion
import com.livrovivo.app.presentation.home.CompanionGreeting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** O que a criança ouve e lê no ritual de dormir. */
class BedtimeScriptTest {

    /** As marcações de emoção que as vozes do app já sabem interpretar. */
    private val knownTags = setOf("warmly", "whispers", "excited", "giggles", "gasp", "curious", "softly", "sighs")

    private val allLines: List<String>
        get() = MagicalCompanion.ALL.flatMap { companion ->
            listOf(
                BedtimeScript.introScript(companion, "Mia"),
                BedtimeScript.goodnightScript("Mia"),
                BedtimeScript.exhaleCaption(companion)
            )
        } + BedtimeScript.INHALE_CAPTION + CompanionGreeting.WIND_DOWN

    @Test
    fun `each companion breathes out in its own way`() {
        val images = MagicalCompanion.ALL.map { BedtimeScript.exhaleImage(it) }

        assertEquals(images.size, images.toSet().size)
        assertTrue(BedtimeScript.exhaleImage(MagicalCompanion.findById("bento")).contains("foguinho"))
    }

    @Test
    fun `the ritual speaks to the child by name`() {
        val bento = MagicalCompanion.findById("bento")

        assertTrue(BedtimeScript.introScript(bento, "Mia").contains("Bento está com soninho"))
        assertTrue(BedtimeScript.introScript(bento, "Mia").contains("Mia, vamos respirar"))
        assertTrue(BedtimeScript.goodnightScript("Mia").contains("Boa noite, Mia."))
    }

    @Test
    fun `nothing in the ritual makes the child feel guilty or asks to stay`() {
        listOf("triste", "saudade", "sumiu", "fica comigo", "não vá", "sozinho").forEach { culpa ->
            allLines.forEach { line ->
                assertFalse("\"$culpa\" em: $line", line.contains(culpa, ignoreCase = true))
            }
        }
    }

    @Test
    fun `lines do not depend on the child's gender`() {
        listOf("obrigado", "obrigada", "cansado", "cansada", "querido", "querida", "lindo", "linda").forEach { flexionada ->
            allLines.forEach { line ->
                assertFalse("\"$flexionada\" em: $line", Regex("\\b$flexionada\\b", RegexOption.IGNORE_CASE).containsMatchIn(line))
            }
        }
    }

    @Test
    fun `only emotion tags the voices understand, and none reach the screen`() {
        val scripts = MagicalCompanion.ALL.map { BedtimeScript.introScript(it, "Mia") } + BedtimeScript.goodnightScript("Mia")

        scripts.forEach { script ->
            Regex("\\[(\\w+)]").findAll(script).forEach { tag ->
                assertTrue("marcação desconhecida: ${tag.value}", tag.groupValues[1] in knownTags)
            }
            assertFalse(ChapterSanitizer.stripAudioTags(script).contains("["))
        }
    }

    @Test
    fun `three calm breaths, breathing out slower than in`() {
        assertEquals(3, BedtimeScript.BREATHS)
        assertTrue(BedtimeScript.EXHALE_MS > BedtimeScript.INHALE_MS)
    }
}
