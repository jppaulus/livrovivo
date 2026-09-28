package com.livrovivo.app.core.ai

import com.livrovivo.app.core.audio.AzureNarrationEngine
import com.livrovivo.app.core.audio.VoicePersona
import com.livrovivo.app.domain.repository.BillingRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LivroVivoServerTest {

    /** As regras do servidor (TypeScript) precisam bater com as do app. */
    private val serverRules = File("../supabase/functions/ai-gateway/regras.ts").readText()

    private fun serverLimit(plan: String, name: String): Int {
        val block = Regex("""$plan: \{(.*?)\n  \}""", RegexOption.DOT_MATCHES_ALL).find(serverRules)!!.groupValues[1]
        return Regex("""\b$name: ([\d_]+),""").find(block)!!.groupValues[1].replace("_", "").toInt()
    }

    @Test
    fun `server errors become the errors the app already handles`() {
        fun kind(code: Int, body: String) = LivroVivoServer.errorFor(code, body).kind
        assertEquals(AiException.Kind.BILLING, kind(402, """{"erro":{"codigo":"LIMITE_GRATIS","mensagem":"x"}}"""))
        assertEquals(AiException.Kind.BILLING, kind(402, """{"erro":{"codigo":"SO_ASSINANTE","mensagem":"x"}}"""))
        assertEquals(AiException.Kind.QUOTA, kind(503, """{"erro":{"codigo":"TETO_DIARIO","mensagem":"x"}}"""))
        assertEquals(AiException.Kind.BLOCKED, kind(422, """{"erro":{"codigo":"BLOQUEADO","mensagem":"x"}}"""))
        assertEquals(AiException.Kind.NOT_CONFIGURED, kind(501, """{"erro":{"codigo":"NAO_CONFIGURADO","mensagem":"x"}}"""))
        assertEquals(AiException.Kind.PERMISSION_DENIED, kind(403, """{"erro":{"codigo":"HISTORIA_DESCONHECIDA","mensagem":"x"}}"""))
        // Recusado pelo Supabase antes de chegar na função.
        assertEquals(AiException.Kind.INVALID_KEY, kind(401, """{"code":401,"message":"Invalid JWT"}"""))
        assertEquals(AiException.Kind.NOT_CONFIGURED, kind(404, "Function not found"))
        assertEquals("LIMITE_MES: acabou", LivroVivoServer.errorFor(402, """{"erro":{"codigo":"LIMITE_MES","mensagem":"acabou"}}""").detail)
    }

    @Test
    fun `the server accepts every narrator voice and the app speed`() {
        VoicePersona.entries.forEach { persona ->
            assertTrue("o servidor não aceita a voz ${persona.azureVoice} (VOZES em regras.ts)", "\"${persona.azureVoice}\"" in serverRules)
        }
        val percent = Regex("""^[+-]?(\d{1,2})%$""").find(AzureNarrationEngine.RATE)!!.groupValues[1].toInt()
        assertTrue(percent <= 30)
    }

    @Test
    fun `server and app agree on the free plan`() {
        assertEquals(BillingRepository.FREE_STORIES, serverLimit("gratis", "historias"))
        assertEquals(BillingRepository.FREE_ILLUSTRATED_PAGES, serverLimit("gratis", "imagensHistoria"))
    }

    @Test
    fun `server uses the same flux deployment`() {
        assertTrue("FLUX_PUBLICACAO = \"${FluxImageService.DEPLOYMENT}\"" in serverRules)
    }
}
