package com.livrovivo.app.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiExceptionTest {

    @Test
    fun `invalid gemini key arrives as http 400 and is not retried with other models`() {
        val error = AiException.classifyHttp(400, "API key not valid. Please pass a valid API key.", "INVALID_ARGUMENT")
        assertEquals(AiException.Kind.INVALID_KEY, error.kind)
        assertFalse(error.shouldTryNextModel)
    }

    @Test
    fun `retired model triggers fallback to the next model`() {
        val error = AiException.classifyHttp(404, "models/gemini-1.5-flash is not found for API version v1beta", "NOT_FOUND")
        assertEquals(AiException.Kind.MODEL_UNAVAILABLE, error.kind)
        assertTrue(error.shouldTryNextModel)
    }

    @Test
    fun `quota and server errors are classified`() {
        assertEquals(AiException.Kind.QUOTA, AiException.classifyHttp(429, "Resource has been exhausted", "RESOURCE_EXHAUSTED").kind)
        assertEquals(AiException.Kind.SERVER, AiException.classifyHttp(503, "The model is overloaded").kind)
        assertEquals(AiException.Kind.QUOTA, AiException.classifyHttp(401, "quota_exceeded: not enough credits").kind)
        assertEquals(AiException.Kind.INVALID_KEY, AiException.classifyHttp(401, "invalid_api_key").kind)
        assertEquals(AiException.Kind.REGION, AiException.classifyHttp(400, "User location is not supported for the API use.").kind)
    }

    @Test
    fun `invalid key is reported instead of model unavailable`() {
        val invalid = AiException(AiException.Kind.INVALID_KEY)
        val missing = AiException(AiException.Kind.MODEL_UNAVAILABLE)
        assertTrue(invalid.reportPriority > missing.reportPriority)
        assertTrue(invalid.friendlyMessage.isNotBlank())
    }
}
