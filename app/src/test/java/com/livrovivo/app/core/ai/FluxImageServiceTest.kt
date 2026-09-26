package com.livrovivo.app.core.ai

import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class FluxImageServiceTest {

    @Test
    fun `request names the deployment and keeps the strict content filter`() {
        val body = FluxImageService.requestBody("a dragon reading", 1024, 768)
        assertEquals(FluxImageService.DEPLOYMENT, body["model"]?.jsonPrimitive?.content)
        assertEquals("jpeg", body["output_format"]?.jsonPrimitive?.content)
        assertEquals(1024, body["width"]?.jsonPrimitive?.int)
        assertEquals(768, body["height"]?.jsonPrimitive?.int)
        assertTrue(body["safety_tolerance"]!!.jsonPrimitive.int <= 1)
        assertTrue("sem imagem de referência (ainda é prévia)", body["input_image"] == null)
    }

    @Test
    fun `image comes from data b64_json`() {
        val jpeg = byteArrayOf(-1, -40, -1, -32, 1, 2, 3)
        val raw = """{"created":1,"data":[{"b64_json":"${Base64.getEncoder().encodeToString(jpeg)}"}],"request_meta":{"cost":3.0}}"""
        val image = FluxImageService.parseImage(raw)
        assertArrayEquals(jpeg, image.bytes)
        assertEquals("image/jpeg", image.mimeType)
    }

    @Test
    fun `answer without image is a parse error`() {
        val error = runCatching { FluxImageService.parseImage("""{"data":[]}""") }.exceptionOrNull() as AiException
        assertEquals(AiException.Kind.PARSE, error.kind)
    }

    @Test
    fun `errors are classified for the parents and the logs`() {
        assertEquals(AiException.Kind.BLOCKED, FluxImageService.errorFor(400, """{"error":{"message":"Request Moderated"}}""").kind)
        assertEquals(AiException.Kind.BLOCKED, FluxImageService.errorFor(400, """{"error":{"code":"content_filter","message":"Content violated RAI policy blocking criteria (BingBlockList_Prompt)."}}""").kind)
        assertEquals(AiException.Kind.INVALID_KEY, FluxImageService.errorFor(401, "{}").kind)
        assertEquals(AiException.Kind.MODEL_UNAVAILABLE, FluxImageService.errorFor(404, """{"error":{"code":"DeploymentNotFound","message":"The API deployment flux does not exist."}}""").kind)
        assertEquals(AiException.Kind.QUOTA, FluxImageService.errorFor(429, "limit").kind)
        assertEquals(AiException.Kind.SERVER, FluxImageService.errorFor(503, "").kind)
    }
}
