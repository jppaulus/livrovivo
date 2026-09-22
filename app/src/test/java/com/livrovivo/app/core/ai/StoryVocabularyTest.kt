package com.livrovivo.app.core.ai

import org.junit.Assert.*
import org.junit.Test

class StoryVocabularyTest {
    @Test fun `known words use the same explanation with accents and uppercase`() {
        assertTrue(StoryVocabulary.explanation("COOPERAÇÃO", "").contains("ajudar uns aos outros"))
    }
    @Test fun `unknown words use their real context instead of inventing a definition`() {
        val explanation = StoryVocabulary.explanation("zuniluz", "Lia chegou. O zuniluz brilhou!")
        assertTrue(explanation.contains("O zuniluz brilhou!"))
        assertFalse(explanation.contains("Lia chegou"))
    }
}
