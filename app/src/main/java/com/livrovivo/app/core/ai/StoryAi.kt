package com.livrovivo.app.core.ai

import kotlinx.serialization.json.JsonObject

/**
 * Quem escreve o texto das histórias e dos livros "Eu leio": o servidor do app (Claude) ou, só na versão de teste,
 * o Gemini com a chave do local.properties (os termos do Google proíbem o Gemini em app infantil: HANDOFF §4, item 12).
 */
class StoryAi(
    private val server: LivroVivoServer,
    private val gemini: GeminiService
) {
    enum class Kind(val code: String) {
        /** Primeira página: o servidor registra a história e conta no limite do plano. */
        OPENING("abertura"),
        CONTINUATION("continuacao"),
        /** Livro "Eu leio" (só assinantes). */
        BOOK("livro")
    }

    suspend fun isAvailable(): Boolean = server.isConfigured || gemini.isAvailable()

    suspend fun generateJson(
        kind: Kind,
        storyId: String?,
        systemPrompt: String,
        userPrompt: String,
        schema: JsonObject,
        temperature: Double = 0.9
    ): JsonObject {
        if (server.isConfigured) {
            try {
                return server.story(kind.code, storyId, systemPrompt, userPrompt, schema)
            } catch (e: AiException) {
                // Na versão de teste, enquanto o servidor ainda não tem a chave do Claude, o texto segue pelo Gemini.
                if (e.kind != AiException.Kind.NOT_CONFIGURED || !gemini.isAvailable()) throw e
            }
        }
        return gemini.generateJson(systemPrompt, userPrompt, schema, temperature)
    }
}
