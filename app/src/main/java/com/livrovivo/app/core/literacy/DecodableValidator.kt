package com.livrovivo.app.core.literacy

import com.livrovivo.app.domain.model.LiteracyKnowledge
import java.text.Normalizer

/**
 * Confere se a criança consegue ler um texto dos livros "Eu leio" sozinha.
 *
 * Uma palavra vale se for:
 * 1. palavra de apoio (aprendida "de vista": O, É, NÃO, ESTÁ...);
 * 2. o nome da criança (o narrador lê o nome, e ele aparece destacado);
 * 3. decodificável: tamanho par e, dividida em pedaços de 2 letras, cada pedaço já aprendido
 *    (BOLA = BO + LA). Palavras já aprendidas numa fase de palavras também valem.
 *
 * Regras de formato: letra maiúscula, pontuação só `. , ! ?`, frases de 3 a 7 palavras,
 * páginas com 1 ou 2 frases e 4 páginas por livro.
 */
object DecodableValidator {
    const val PAGES_PER_BOOK = 4
    val WORDS_PER_SENTENCE = 3..7
    val SENTENCES_PER_PAGE = 1..2
    val WORDS_PER_TITLE = 1..6

    private const val PUNCTUATION = ".,!?"
    private val SENTENCE_END = Regex("(?<=[.!?])\\s+")

    /** Palavras do texto que a criança ainda não consegue ler (lista vazia = texto válido). */
    fun invalidWords(
        text: String,
        knowledge: LiteracyKnowledge,
        supportWords: Set<String>,
        childName: String
    ): List<String> {
        val support = supportWords.map { normalize(it) }.toSet()
        val name = nameParts(childName).toSet()
        return words(text).filterNot { word ->
            word in support || word in name || word in knowledge.words || isDecodable(word, knowledge.syllables)
        }.distinct()
    }

    /** true se a palavra tem tamanho par e cada pedaço de 2 letras está em [syllables] (BOLA = BO + LA). */
    fun isDecodable(word: String, syllables: Set<String>): Boolean =
        word.isNotEmpty() && word.length % 2 == 0 && word.chunked(2).all { it in syllables }

    /** Palavras do texto, sem a pontuação grudada (". , ! ?"). Qualquer outro sinal continua na palavra. */
    fun words(text: String): List<String> =
        normalize(text).split(Regex("\\s+"))
            .map { it.trim(*PUNCTUATION.toCharArray()) }
            .filter { it.isNotEmpty() }

    /**
     * Partes do nome em maiúsculas, só com letras, na ordem: "Maria Clara" → MARIA, CLARA;
     * "Ana-Lu" → ANA, LU; "Lia2" → LIA. O motor offline usa a primeira parte no livro.
     */
    fun nameParts(childName: String): List<String> =
        normalize(childName).uppercase().split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }.distinct()

    /** Problemas de formato de um livro inteiro (lista vazia = formato certo). */
    fun formatProblems(title: String, pages: List<String>): List<String> {
        val problems = mutableListOf<String>()
        problems += textProblems("título", title)
        val titleWords = words(title).size
        if (titleWords !in WORDS_PER_TITLE) problems += "título com $titleWords palavras"
        if (pages.size != PAGES_PER_BOOK) problems += "o livro tem ${pages.size} páginas (precisa de $PAGES_PER_BOOK)"
        pages.forEachIndexed { index, page -> problems += pageProblems(index + 1, page) }
        return problems
    }

    /** Problemas de formato de uma página: 1 ou 2 frases de 3 a 7 palavras, cada uma terminando em . ! ou ? */
    fun pageProblems(number: Int, page: String): List<String> {
        val where = "página $number"
        val problems = textProblems(where, page).toMutableList()
        val sentences = normalize(page).trim().split(SENTENCE_END).filter { it.isNotBlank() }
        if (sentences.size !in SENTENCES_PER_PAGE) problems += "$where tem ${sentences.size} frases"
        sentences.forEach { sentence ->
            if (sentence.last() !in ".!?") problems += "$where: frase sem ponto final (\"$sentence\")"
            val count = words(sentence).size
            if (count !in WORDS_PER_SENTENCE) problems += "$where: frase com $count palavras (\"$sentence\")"
        }
        return problems
    }

    private fun textProblems(where: String, text: String): List<String> {
        val problems = mutableListOf<String>()
        if (text.isBlank()) return listOf("$where vazio")
        normalize(text).forEach { char ->
            when {
                char.isLetter() && char.isLowerCase() -> problems += "$where tem letra minúscula"
                char.isLetter() || char.isWhitespace() || char in PUNCTUATION -> Unit
                else -> problems += "$where tem um sinal que não pode: '$char'"
            }
        }
        return problems.distinct()
    }

    /** Junta acentos escritos separados (E + ´) num caractere só, para "ESTÁ" sempre bater com "ESTÁ". */
    private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFC)
}
