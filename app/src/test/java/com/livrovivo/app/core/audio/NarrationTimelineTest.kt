package com.livrovivo.app.core.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrationTimelineTest {

    private val text = """
Era uma vez uma estrelinha. Ela tinha medo do escuro!

— Psiu! — sussurrou Bento. — Vamos acender o céu?
E todos sorriram
""".trim()

    @Test
    fun `splits sentences and dialogue lines`() {
        val timeline = NarrationTimeline.build(text)
        val sentences = timeline.sentences.map { text.substring(it.first, it.last + 1) }

        assertEquals(
            listOf(
                "Era uma vez uma estrelinha.",
                "Ela tinha medo do escuro!",
                "— Psiu!",
                "— sussurrou Bento.",
                "— Vamos acender o céu?",
                "E todos sorriram"
            ),
            sentences
        )
    }

    @Test
    fun `highlight moves forward monotonically with the audio`() {
        val timeline = NarrationTimeline.build(text)
        assertEquals(0, timeline.sentenceAt(0f))
        assertEquals(timeline.sentences.lastIndex, timeline.sentenceAt(1f))
        var previous = 0
        for (step in 0..100) {
            val current = timeline.sentenceAt(step / 100f)
            assertTrue(current >= previous)
            previous = current
        }
    }

    @Test
    fun `empty text has no highlight`() {
        assertEquals(-1, NarrationTimeline.build("   ").sentenceAt(0.5f))
    }
}
