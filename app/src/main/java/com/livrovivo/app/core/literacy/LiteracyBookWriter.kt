package com.livrovivo.app.core.literacy

import android.util.Log
import com.livrovivo.app.core.ai.DecodableRequest
import com.livrovivo.app.core.ai.GeminiService
import com.livrovivo.app.core.ai.JsonUtils.array
import com.livrovivo.app.core.ai.JsonUtils.asObjectOrNull
import com.livrovivo.app.core.ai.JsonUtils.string
import com.livrovivo.app.core.ai.StoryPrompts
import com.livrovivo.app.domain.model.ChildProfile
import com.livrovivo.app.domain.model.DecodableBook
import com.livrovivo.app.domain.model.DecodablePage
import com.livrovivo.app.domain.model.LiteracyKnowledge
import com.livrovivo.app.domain.model.LiteracyTrail
import com.livrovivo.app.domain.model.MagicalCompanion
import com.livrovivo.app.domain.model.VocabularyWord
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject

/** Livro escrito e de onde ele veio. */
data class WrittenBook(val book: DecodableBook, val isOffline: Boolean)

/** A parte da IA que o escritor usa. No app é o Gemini; nos testes, uma versão falsa. */
interface BookAi {
    suspend fun isAvailable(): Boolean
    suspend fun generateJson(systemPrompt: String, userPrompt: String, schema: JsonObject): JsonObject
}

/** Liga o escritor ao [GeminiService] das aventuras (mesma chave, mesmos modelos e mesma troca automática). */
class GeminiBookAi(private val gemini: GeminiService) : BookAi {
    override suspend fun isAvailable(): Boolean = gemini.isAvailable()

    override suspend fun generateJson(systemPrompt: String, userPrompt: String, schema: JsonObject): JsonObject =
        gemini.generateJson(systemPrompt, userPrompt, schema, temperature = 0.8)
}

/**
 * Escreve os livros "Eu leio", com a mesma ideia do StoryWriter das aventuras: IA quando possível e o
 * [OfflineDecodableEngine] como garantia.
 *
 * 1. Com IA: pede o livro com uma lista fechada de palavras ([StoryPrompts.decodablePrompt]).
 * 2. Confere título e páginas com o [DecodableValidator]. Se houver palavra que a criança não lê (ou
 *    formato errado), tenta **uma vez** de novo, dizendo à IA o que foi recusado.
 * 3. Se falhar de novo, se der erro de rede/cota ou se não houver IA: usa o motor offline.
 */
class LiteracyBookWriter(private val ai: BookAi? = null) {

    suspend fun write(
        trail: LiteracyTrail,
        knowledge: LiteracyKnowledge,
        child: ChildProfile,
        seed: Int,
        focusSyllables: Set<String>
    ): WrittenBook? {
        val ai = ai
        if (ai != null && runCatching { ai.isAvailable() }.getOrDefault(false)) {
            writeWithAi(ai, trail, knowledge, child, focusSyllables)?.let { return WrittenBook(it, isOffline = false) }
        }
        val companion = MagicalCompanion.findById(child.companionId).name
        return OfflineDecodableEngine.write(trail, knowledge, child.name, companion, seed, focusSyllables)
            ?.let { WrittenBook(it, isOffline = true) }
    }

    private suspend fun writeWithAi(
        ai: BookAi,
        trail: LiteracyTrail,
        knowledge: LiteracyKnowledge,
        child: ChildProfile,
        focusSyllables: Set<String>
    ): DecodableBook? {
        val request = requestFor(trail, knowledge, child, focusSyllables) ?: return null
        val rejected = linkedSetOf<String>()
        var formatProblems = emptyList<String>()
        repeat(AI_ATTEMPTS) { attempt ->
            val json = try {
                ai.generateJson(
                    systemPrompt = StoryPrompts.DECODABLE_SYSTEM_PROMPT,
                    userPrompt = StoryPrompts.decodablePrompt(request, rejected, formatProblems),
                    schema = StoryPrompts.decodableSchema()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Rede, cota, chave: o livro offline resolve na hora, sem a criança esperar mais.
                Log.w(TAG, "IA indisponível para o livro Eu leio: ${e.message}")
                return null
            }
            val book = parse(json, trail.vocabulary)
            val check = check(book, trail, knowledge, child.name)
            if (book != null && check.isValid) return book.withCharacterSheet(request, child)
            rejected += check.invalidWords
            formatProblems = check.formatProblems
            Log.w(TAG, "Livro da IA recusado (tentativa ${attempt + 1}): ${check.invalidWords} ${check.formatProblems}")
        }
        return null
    }

    /** Lista fechada para a IA: palavras de apoio, palavras com figura que a criança lê, o nome e o companheiro. */
    internal fun requestFor(
        trail: LiteracyTrail,
        knowledge: LiteracyKnowledge,
        child: ChildProfile,
        focusSyllables: Set<String>
    ): DecodableRequest? {
        val name = DecodableValidator.nameParts(child.name).firstOrNull { it.length >= 2 } ?: return null
        val words = trail.vocabulary.filter { canRead(it.word, knowledge) }
        if (words.size < MIN_WORDS) return null
        val companion = DecodableValidator.nameParts(MagicalCompanion.findById(child.companionId).name).singleOrNull()
            ?.takeIf { it != name && canRead(it, knowledge) }
        return DecodableRequest(
            childName = name,
            gender = child.gender,
            appearance = StoryPrompts.appearanceDescription(child),
            companionName = companion,
            supportWords = trail.supportWords,
            words = words,
            focusWords = words.filter { word -> word.syllables.any { it in focusSyllables } }.map { it.word }
        )
    }

    private fun canRead(word: String, knowledge: LiteracyKnowledge): Boolean =
        DecodableValidator.isDecodable(word, knowledge.syllables) || word in knowledge.words

    /** Resultado da conferência de um livro da IA. */
    internal data class Check(val invalidWords: List<String>, val formatProblems: List<String>) {
        val isValid: Boolean get() = invalidWords.isEmpty() && formatProblems.isEmpty()
    }

    internal fun check(book: DecodableBook?, trail: LiteracyTrail, knowledge: LiteracyKnowledge, childName: String): Check {
        if (book == null) return Check(emptyList(), listOf("a resposta precisa de título e de 4 páginas com texto"))
        val support = trail.supportWords.toSet()
        val texts = listOf(book.title) + book.pages.map { it.text }
        val invalid = texts.flatMap { DecodableValidator.invalidWords(it, knowledge, support, childName) }.distinct()
        return Check(invalid, DecodableValidator.formatProblems(book.title, book.pages.map { it.text }))
    }

    /**
     * Lê a resposta da IA. Passa tudo para maiúsculas e junta espaços (a IA às vezes erra nisso, e não é
     * motivo para recusar o livro). A palavra principal de cada página é a primeira palavra com figura.
     */
    internal fun parse(json: JsonObject, vocabulary: List<VocabularyWord>): DecodableBook? {
        fun clean(text: String?) = text.orEmpty().trim().trim('"', '*').replace(Regex("\\s+"), " ").uppercase()
        val title = clean(json.string("title")).trimEnd('.', '!', '?')
        val rawPages = json.array("pages")?.mapNotNull { it.asObjectOrNull() }.orEmpty()
        if (title.isBlank() || rawPages.isEmpty()) return null
        val byWord = vocabulary.associateBy { it.word }
        var lastWord: VocabularyWord? = null
        val drafts = rawPages.map { page ->
            val text = clean(page.string("text"))
            val main = DecodableValidator.words(text).firstNotNullOfOrNull { byWord[it] } ?: lastWord
            lastWord = main ?: lastWord
            Triple(text, main, page.string("illustrationPrompt")?.trim()?.take(600))
        }
        // Páginas sem palavra com figura usam a da página anterior (ou a primeira do livro).
        val firstWord = drafts.firstNotNullOfOrNull { it.second }
            ?: DecodableValidator.words(title).firstNotNullOfOrNull { byWord[it] }
            ?: return null
        return DecodableBook(
            title = title,
            pages = drafts.map { (text, main, illustration) ->
                DecodablePage(text, main ?: firstWord, illustrationPrompt = illustration?.takeIf { it.isNotBlank() })
            }
        )
    }

    /** Ficha dos personagens (em inglês) para as ilustrações ficarem parecidas em todas as páginas. */
    private fun DecodableBook.withCharacterSheet(request: DecodableRequest, child: ChildProfile): DecodableBook {
        val companion = MagicalCompanion.findById(child.companionId)
        val usesCompanion = request.companionName != null && pages.any { request.companionName in DecodableValidator.words(it.text) }
        val sheet = buildString {
            append("${request.childName}: ${request.appearance}, wearing a cozy yellow sweater.")
            if (usesCompanion) append(" ${companion.name}: ${companion.visualDescription}.")
        }
        return copy(usesCompanion = usesCompanion, characterSheet = sheet)
    }

    private companion object {
        const val TAG = "LivroVivoIA"
        /** Uma tentativa e mais uma com a lista do que foi recusado. */
        const val AI_ATTEMPTS = 2
        /** Com menos palavras que isso, o motor offline já faz o melhor livro possível. */
        const val MIN_WORDS = 3
    }
}
