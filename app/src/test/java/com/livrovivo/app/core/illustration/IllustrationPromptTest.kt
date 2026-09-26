package com.livrovivo.app.core.illustration

import org.junit.Assert.assertEquals
import org.junit.Test

class IllustrationPromptTest {

    @Test
    fun `exact ages leave the image prompt`() {
        assertEquals(
            "Lia: a young girl, wearing a cozy yellow sweater. Bento: a small baby dragon.",
            IllustrationService.withoutExactAges("Lia: a 4-year-old girl, wearing a cozy yellow sweater. Bento: a small baby dragon.")
        )
        assertEquals("a young boy", IllustrationService.withoutExactAges("a 7 year old boy"))
        assertEquals("a young child", IllustrationService.withoutExactAges("a 10-years-old child"))
    }

    @Test
    fun `other numbers stay`() {
        val text = "Bento has 2 stubby horns and 5 spots"
        assertEquals(text, IllustrationService.withoutExactAges(text))
    }
}
