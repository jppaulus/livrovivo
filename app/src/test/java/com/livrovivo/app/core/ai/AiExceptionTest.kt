package com.livrovivo.app.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiExceptionTest {

    @Test
    fun `invalid gemini key arrives as http 400 and is not retried with other models`() {
        val error = AiException.classifyHttp(400, "API key not valid. Please pass a valid API key. API_KEY_INVALID", "INVALID_ARGUMENT")
        assertEquals(AiException.Kind.INVALID_KEY, error.kind)
        assertFalse(error.shouldTryNextModel)
    }

    @Test
    fun `generic 403 means no access to that model and tries the next one`() {
        val error = AiException.classifyHttp(403, "The caller does not have permission", "PERMISSION_DENIED")
        assertEquals(AiException.Kind.PERMISSION_DENIED, error.kind)
        assertTrue(error.shouldTryNextModel)
        assertFalse("não deve culpar a chave", error.friendlyMessage.contains("inválida"))
    }

    @Test
    fun `account and project level 403 errors are distinguished`() {
        assertEquals(
            AiException.Kind.ACCOUNT_BLOCKED,
            AiException.classifyHttp(403, "Your project has been denied access. Please contact support.", "PERMISSION_DENIED").kind
        )
        assertEquals(
            AiException.Kind.ACCOUNT_BLOCKED,
            AiException.classifyHttp(403, "Your API key was reported as leaked. Please use another API key.", "PERMISSION_DENIED").kind
        )
        assertEquals(
            AiException.Kind.API_DISABLED,
            AiException.classifyHttp(403, "Generative Language API has not been used in project 123 before or it is disabled. SERVICE_DISABLED").kind
        )
        assertEquals(
            AiException.Kind.KEY_RESTRICTED,
            AiException.classifyHttp(403, "Requests from this Android client application <empty> are blocked. API_KEY_ANDROID_APP_BLOCKED").kind
        )
        listOf(AiException.Kind.ACCOUNT_BLOCKED, AiException.Kind.API_DISABLED, AiException.Kind.KEY_RESTRICTED).forEach {
            assertFalse(AiException(it).shouldTryNextModel)
        }
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
    fun `key problems are reported before per-model access problems`() {
        val invalid = AiException(AiException.Kind.INVALID_KEY)
        val permission = AiException(AiException.Kind.PERMISSION_DENIED)
        val missing = AiException(AiException.Kind.MODEL_UNAVAILABLE)
        assertTrue(invalid.reportPriority > permission.reportPriority)
        assertTrue(permission.reportPriority > missing.reportPriority)
        assertTrue(invalid.friendlyMessage.isNotBlank())
    }
}
