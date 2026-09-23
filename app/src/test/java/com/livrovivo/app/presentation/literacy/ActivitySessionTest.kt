package com.livrovivo.app.presentation.literacy

import com.livrovivo.app.core.literacy.LiteracyRules
import com.livrovivo.app.core.literacy.TrailParser
import com.livrovivo.app.domain.model.LiteracyPhase
import com.livrovivo.app.domain.model.LiteracyQuestion
import com.livrovivo.app.domain.model.QuestionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ActivitySessionTest {

    private val tap = LiteracyQuestion(QuestionType.LISTEN_AND_TAP, "Toque na letra A", "A", listOf("E", "U", "A"), emptyList(), null)
    private val join = LiteracyQuestion(QuestionType.JOIN, "Forme BA", "BA", emptyList(), listOf("A", "B"), null)
    private val dictation = LiteracyQuestion(QuestionType.DICTATION, "Escreva dado", "DADO", emptyList(), listOf("DA", "NI", "MO", "DO"), null)

    private fun phase(vararg questions: LiteracyQuestion) = LiteracyPhase("f", "Fase", true, emptyList(), questions.toList())

    /** Responde certo (e confirma que avançou). */
    private fun answerRight(state: ActivityState): ActivityState {
        val q = state.question
        val answered = if (q.type.usesOptions) {
            ActivitySession.chooseOption(state, q.answer)
        } else {
            LiteracyRules.solvePieces(q.answer, q.pieces)!!.fold(state) { s, piece -> ActivitySession.placePiece(s, piece) }
        }
        assertEquals(ActivityFeedback.CORRECT, answered.feedback)
        return ActivitySession.next(answered)
    }

    @Test
    fun `stars follow the spec table`() {
        assertEquals(listOf(0, 1, 1, 2, 2, 3), (0..5).map { LiteracyRules.starsFor(it) })
    }

    @Test
    fun `trail is hidden only for 9+ unless parents enable it`() {
        assertTrue(LiteracyRules.isTrailVisible("3-5"))
        assertTrue(LiteracyRules.isTrailVisible("6-8"))
        assertFalse(LiteracyRules.isTrailVisible("9+"))
        assertTrue(LiteracyRules.isTrailVisible("9+", enabledByParents = true))
    }

    @Test
    fun `perfect phase gives 3 stars and finishes`() {
        var state = ActivitySession.start(phase(tap, join, dictation, tap, join))
        repeat(5) { state = answerRight(state) }

        assertTrue(state.finished)
        assertEquals(5, state.correctAnswers)
        assertEquals(3, state.stars)
        assertEquals(0, state.totalMistakes)
    }

    @Test
    fun `a wrong try keeps the child on the question but it no longer counts as a hit`() {
        var state = ActivitySession.start(phase(tap, tap))
        state = ActivitySession.chooseOption(state, "E")

        assertEquals(ActivityFeedback.TRY_AGAIN, state.feedback)
        assertEquals("E", state.wrongOption)
        assertEquals(0, state.index)

        state = answerRight(ActivitySession.clearFeedback(state))
        state = answerRight(state)

        assertEquals(1, state.correctAnswers)
        assertEquals(1, state.totalMistakes)
        assertEquals(1, state.stars)
    }

    @Test
    fun `tapping a piece fills the next empty slot and a placed piece can go back`() {
        var state = ActivitySession.start(phase(join))
        assertEquals(2, state.slots.size)

        state = ActivitySession.placePiece(state, pieceIndex = 1) // B
        assertEquals(listOf(1, null), state.slots)
        assertEquals(listOf(0), state.trayPieces)

        state = ActivitySession.removeFromSlot(state, 0)
        assertEquals(listOf(null, null), state.slots)
        assertEquals(listOf(0, 1), state.trayPieces)
    }

    @Test
    fun `dragging onto a filled slot sends the old piece back to the tray`() {
        var state = ActivitySession.start(phase(dictation))
        state = ActivitySession.placePiece(state, pieceIndex = 1, slot = 0) // NI
        state = ActivitySession.placePiece(state, pieceIndex = 0, slot = 0) // DA no lugar do NI

        assertEquals(listOf(0, null), state.slots)
        assertTrue(1 in state.trayPieces)
    }

    @Test
    fun `dictation has only the slots the answer needs and a wrong word empties them`() {
        var state = ActivitySession.start(phase(dictation))
        assertEquals(2, state.slots.size)

        state = ActivitySession.placePiece(state, 0) // DA
        state = ActivitySession.placePiece(state, 2) // MO -> DAMO

        assertEquals(ActivityFeedback.TRY_AGAIN, state.feedback)
        assertEquals(listOf(null, null), state.slots)
        assertEquals(1, state.mistakesOnQuestion)
    }

    @Test
    fun `after two mistakes the hint points at the right option or piece`() {
        var tapState = ActivitySession.start(phase(tap))
        tapState = ActivitySession.chooseOption(tapState, "E")
        assertFalse(tapState.showHint)
        tapState = ActivitySession.chooseOption(ActivitySession.clearFeedback(tapState), "U")
        assertTrue(tapState.showHint)

        var pieceState = ActivitySession.start(phase(dictation))
        repeat(2) {
            pieceState = ActivitySession.placePiece(pieceState, 1) // NI
            pieceState = ActivitySession.placePiece(pieceState, 2) // MO
        }
        assertTrue(pieceState.showHint)
        assertEquals("DA goes first", 0, pieceState.hintPiece)
        pieceState = ActivitySession.placePiece(pieceState, 0)
        assertEquals("then DO", 3, pieceState.hintPiece)
    }

    @Test
    fun `input is ignored while the correct answer is being celebrated`() {
        var state = ActivitySession.start(phase(tap, tap))
        state = ActivitySession.chooseOption(state, "A")
        val celebrating = state

        assertEquals(celebrating, ActivitySession.chooseOption(state, "E"))
        assertEquals(1, celebrating.correctAnswers)
    }

    @Test
    fun `next does nothing until the question is answered`() {
        val state = ActivitySession.start(phase(tap, tap))
        assertEquals(state, ActivitySession.next(state))
        assertNull(state.hintPiece)
    }

    @Test
    fun `every phase of the real trail can be played to 3 stars`() {
        val trail = TrailParser.parse(File("src/main/assets/${TrailParser.ASSET_PATH}").readText(Charsets.UTF_8))
        trail.phases.forEach { phase ->
            var state = ActivitySession.start(phase)
            repeat(phase.questions.size) { state = answerRight(state) }
            assertTrue(phase.id, state.finished)
            assertEquals(phase.id, 3, state.stars)
        }
    }
}
