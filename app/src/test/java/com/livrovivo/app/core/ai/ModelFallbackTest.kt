package com.livrovivo.app.core.ai

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ModelFallbackTest {

    private fun http(code: Int, message: String, status: String? = null) = AiException.classifyHttp(code, message, status)

    @Test
    fun `403 on the configured model falls back to the next one and switches the default`() = runTest {
        val tried = mutableListOf<String>()
        val outcome = ModelFallback.run(listOf("gemini-2.5-flash", "gemini-3.5-flash"), retryDelay = {}) { model, _ ->
            tried += model
            if (model == "gemini-2.5-flash") throw http(403, "The caller does not have permission", "PERMISSION_DENIED")
            "ok"
        }
        assertEquals(listOf("gemini-2.5-flash", "gemini-3.5-flash"), tried)
        assertEquals("gemini-3.5-flash", outcome.model)
        assertTrue(outcome.switchDefault)
    }

    @Test
    fun `transient quota errors fall back without changing the saved default`() = runTest {
        val outcome = ModelFallback.run(listOf("a", "b"), retryDelay = {}) { model, _ ->
            if (model == "a") throw http(429, "Resource has been exhausted", "RESOURCE_EXHAUSTED")
            42
        }
        assertEquals("b", outcome.model)
        assertFalse(outcome.switchDefault)
    }

    @Test
    fun `invalid key stops immediately without trying other models`() = runTest {
        val tried = mutableListOf<String>()
        try {
            ModelFallback.run(listOf("a", "b", "c"), retryDelay = {}) { model, _ ->
                tried += model
                throw http(400, "API key not valid. Please pass a valid API key.", "INVALID_ARGUMENT")
            }
            fail("deveria falhar")
        } catch (e: AiException) {
            assertEquals(AiException.Kind.INVALID_KEY, e.kind)
            assertEquals(listOf("a"), tried)
        }
    }

    @Test
    fun `bad request is retried once in lite mode before moving on`() = runTest {
        val attempts = mutableListOf<Pair<String, Boolean>>()
        val outcome = ModelFallback.run(listOf("a"), retryDelay = {}) { model, lite ->
            attempts += model to lite
            if (!lite) throw http(400, "Invalid JSON payload received. Unknown name \"thinkingLevel\"")
            "ok"
        }
        assertEquals(listOf("a" to false, "a" to true), attempts)
        assertEquals("ok", outcome.value)
    }

    @Test
    fun `server errors are retried once on the same model`() = runTest {
        var calls = 0
        var delays = 0
        val outcome = ModelFallback.run(listOf("a", "b"), retryDelay = { delays++ }) { model, _ ->
            calls++
            if (calls == 1) throw http(503, "The model is overloaded")
            model
        }
        assertEquals("a", outcome.model)
        assertEquals(1, delays)
    }

    @Test
    fun `free tier quota details at the end of google messages are preserved`() = runTest {
        val googleMessage = "You exceeded your current quota, please check your plan and billing details. " +
            "For more information on this error, head to: https://ai.google.dev/gemini-api/docs/rate-limits.\n" +
            "* Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 0, model: gemini-3.1-flash-image"
        try {
            ModelFallback.run(listOf("gemini-3.1-flash-image"), retryDelay = {}) { _, _ ->
                throw AiException(AiException.Kind.QUOTA, googleMessage, 429)
            }
            fail("deveria falhar")
        } catch (e: AiException) {
            assertTrue(e.detail.contains("limit: 0"))
        }
    }

    @Test
    fun `when every model fails the most informative error is reported with per-model details`() = runTest {
        try {
            ModelFallback.run(listOf("gemini-3.1-flash-image", "gemini-2.5-flash-image"), retryDelay = {}) { model, _ ->
                if (model == "gemini-3.1-flash-image") throw http(403, "The caller does not have permission", "PERMISSION_DENIED")
                throw http(404, "models/gemini-2.5-flash-image is not found", "NOT_FOUND")
            }
            fail("deveria falhar")
        } catch (e: AiException) {
            assertEquals(AiException.Kind.PERMISSION_DENIED, e.kind)
            assertTrue(e.detail.contains("gemini-3.1-flash-image: HTTP 403"))
            assertTrue(e.detail.contains("gemini-2.5-flash-image: HTTP 404"))
        }
    }
}
