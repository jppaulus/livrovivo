package com.livrovivo.app.core.literacy

import com.livrovivo.app.domain.model.DecodableBook
import com.livrovivo.app.domain.model.DecodablePage
import com.livrovivo.app.domain.model.LiteracyKnowledge
import com.livrovivo.app.domain.model.LiteracyTrail
import com.livrovivo.app.domain.model.VocabularyWord
import kotlin.random.Random

/**
 * Escreve livros "Eu leio" sem IA, com frases de modelo e só as palavras do vocabulário que a criança
 * já consegue ler. É a garantia de sempre haver livro, com ou sem internet.
 *
 * Há três roteiros, todos de 4 páginas:
 * - **Coleção**: "LIA TEM UM DADO." ... "O DADO E O DOCE. SÃO DE LIA!"
 * - **Cadê?**: a criança procura um objeto: "O GATO ESTÁ NA CAMA? SIM, ESTÁ NA CAMA!"
 * - **Adivinha**: "LIA, É UM DADO? NÃO, É UM DOCE!" (a figura da página mostra a resposta)
 *
 * O resultado passa pelo [DecodableValidator] antes de sair: um roteiro que gerasse palavra ilegível
 * ou frase fora do formato é descartado. Os testes conferem isso em todos os pontos da trilha.
 */
object OfflineDecodableEngine {

    /**
     * @param childName nome da criança como está no perfil; o livro usa a primeira parte ("Maria Clara" → MARIA).
     * @param companionName nome do companheiro mágico; só entra se a criança já consegue lê-lo (LU-NA, PI-PO-CA).
     * @param seed muda o roteiro e as palavras escolhidas (use o número do livro para variar).
     * @param focusSyllables sílabas aprendidas por último: palavras com elas aparecem primeiro.
     * @return null quando ainda não há palavras suficientes para um livro.
     */
    fun write(
        trail: LiteracyTrail,
        knowledge: LiteracyKnowledge,
        childName: String,
        companionName: String? = null,
        seed: Int = 0,
        focusSyllables: Set<String> = emptySet()
    ): DecodableBook? {
        val random = Random(seed)
        val readable = trail.vocabulary.filter { canRead(it.word, knowledge) }
        if (readable.isEmpty()) return null
        // O embaralhamento varia o livro; a ordenação estável põe as sílabas novas na frente.
        val words = readable.shuffled(random).sortedByDescending { word -> word.syllables.any { it in focusSyllables } }
        val name = DecodableValidator.nameParts(childName).firstOrNull { it.length >= 2 }
        val companion = companionName
            ?.let { DecodableValidator.nameParts(it).singleOrNull() }
            ?.takeIf { it != name && canRead(it, knowledge) }
        val story = Story(words, name, companion)
        return listOfNotNull(collection(story), search(story), riddle(story))
            .shuffled(random)
            .firstOrNull { book -> isValid(book, trail, knowledge, childName) }
    }

    private fun canRead(word: String, knowledge: LiteracyKnowledge): Boolean =
        DecodableValidator.isDecodable(word, knowledge.syllables) || word in knowledge.words

    private fun isValid(book: DecodableBook, trail: LiteracyTrail, knowledge: LiteracyKnowledge, childName: String): Boolean {
        val support = trail.supportWords.toSet()
        val texts = listOf(book.title) + book.pages.map { it.text }
        return texts.all { DecodableValidator.invalidWords(it, knowledge, support, childName).isEmpty() } &&
            DecodableValidator.formatProblems(book.title, book.pages.map { it.text }).isEmpty()
    }

    private class Story(val words: List<VocabularyWord>, val name: String?, val companion: String?) {
        /** "LUNA ESTÁ COM LIA." quando o companheiro pode aparecer. */
        fun companionLine(): String? = if (companion != null && name != null) "$companion ESTÁ COM $name." else null
    }

    // Atalhos para as frases: "O"/"A", "UM"/"UMA" e "NO"/"NA".
    private val VocabularyWord.the: String get() = "$article $word"
    private val VocabularyWord.a: String get() = "$indefiniteArticle $word"
    private val VocabularyWord.inThe: String get() = "$inArticle $word"

    private fun page(mainWord: VocabularyWord, vararg sentences: String?) =
        DecodablePage(sentences.filterNotNull().joinToString(" "), mainWord)

    /** Coleção: a criança mostra o que tem. Precisa do nome e de 2 ou 3 objetos. */
    private fun collection(story: Story): DecodableBook? {
        val name = story.name ?: return null
        val objects = story.words.filter { it.isObject }.take(3)
        if (objects.size < 2) return null
        val (first, second) = objects
        val pages = if (objects.size == 3) {
            val third = objects[2]
            listOf(
                page(first, "$name TEM ${first.a}.", story.companionLine()),
                page(second, "$name TEM ${second.a}."),
                page(third, "$name TEM ${third.a}."),
                page(first, "${first.the}, ${second.the} E ${third.the}.", "SÃO DE $name!")
            )
        } else {
            listOf(
                page(first, "$name TEM ${first.a}.", story.companionLine()),
                page(first, "${first.the} É DE $name."),
                page(second, "$name TEM ${second.a}."),
                page(second, "${first.the} E ${second.the}.", "SÃO DE $name!")
            )
        }
        return DecodableBook("${first.the} DE $name", pages, usesCompanion = story.companionLine() != null)
    }

    /** Cadê? A criança procura um objeto em um ou dois lugares. Precisa do nome, de um objeto e de um lugar. */
    private fun search(story: Story): DecodableBook? {
        val name = story.name ?: return null
        val thing = story.words.firstOrNull { it.isObject && !it.isPlace } ?: return null
        val places = story.words.filter { it.isPlace }.take(2)
        if (places.isEmpty()) return null
        val start = listOf(
            page(thing, "$name TEM ${thing.a}.", "${thing.the} É DE $name."),
            page(thing, "$name NÃO ESTÁ COM ${thing.the}.", story.companionLine())
        )
        val ending = if (places.size == 2) {
            val (wrong, right) = places
            listOf(
                page(wrong, "${thing.the} ESTÁ ${wrong.inThe}?", "NÃO, NÃO ESTÁ."),
                page(right, "${thing.the} ESTÁ ${right.inThe}!", "$name ESTÁ COM ${thing.the}.")
            )
        } else {
            val place = places.single()
            listOf(
                page(place, "${thing.the} ESTÁ ${place.inThe}?", "SIM, ESTÁ ${place.inThe}!"),
                page(thing, "$name ESTÁ COM ${thing.the}!")
            )
        }
        return DecodableBook("${thing.the} DE $name", start + ending, usesCompanion = story.companionLine() != null)
    }

    /** Adivinha: "É UM DADO? NÃO, É UM DOCE!". Serve para qualquer palavra; precisa de 3 diferentes. */
    private fun riddle(story: Story): DecodableBook? {
        val words = story.words.take(3)
        if (words.size < 3) return null
        val (first, second, third) = words
        val call = story.name?.let { "$it, " } ?: ""
        val pages = listOf(
            page(second, "${call}É ${first.a}?", "NÃO, É ${second.a}!"),
            page(third, "É ${second.a}?", "NÃO, É ${third.a}!"),
            page(first, "É ${third.a}?", "NÃO, É ${first.a}!"),
            page(first, "${call}É ${first.a}?", "SIM, É ${first.a}!")
        )
        return DecodableBook("É ${first.a}?", pages)
    }
}
