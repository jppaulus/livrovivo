package com.livrovivo.app.core.parentalgate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentalGateTest {

    @Test
    fun `math challenge is written out in words`() {
        val challenge = MathChallenge(num1 = 6, num2 = 7, expectedAnswer = 42)
        assertEquals(42, challenge.expectedAnswer)
        assertTrue(challenge.questionText.contains("seis"))
        assertTrue(challenge.questionText.contains("sete"))
    }

    @Test
    fun `generate challenge creates valid multiplication within expected range`() {
        repeat(200) {
            val challenge = MathChallenge.generate()
            assertTrue(challenge.num1 in 3..9)
            assertTrue(challenge.num2 in 3..9)
            assertEquals(challenge.num1 * challenge.num2, challenge.expectedAnswer)
        }
    }
}
