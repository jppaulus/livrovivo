package com.livrovivo.app.core.literacy

import com.livrovivo.app.core.audio.NarrationTimeline

/** Uma palavra da página no leitor "Eu leio". */
data class ReaderWord(
    /** Como aparece na tela, com a pontuação grudada ("DOCE."). */
    val display: String,
    /** A palavra sem pontuação ("DOCE"): é o que o narrador lê. */
    val word: String,
    /** Frase da página a que a palavra pertence, para o destaque da narração ("Ouvir a página"). */
    val sentence: Int,
    /** Nome da criança: a "palavra especial", que ela não precisa decodificar. */
    val isName: Boolean,
    /** Sílabas para "tocar e segurar" (BO, LA); null quando a palavra não se separa assim. */
    val syllables: List<String>?
)

object ReaderWords {

    /**
     * Divide a página em palavras tocáveis.
     * @param syllables todas as sílabas da trilha: só palavras feitas delas são separadas (DADO → DA · DO);
     * palavras de apoio (É, NÃO, ESTÁ) e o nome são lidos inteiros.
     */
    fun of(text: String, childName: String, syllables: Set<String>): List<ReaderWord> {
        val sentences = NarrationTimeline.build(text).sentences
        val name = DecodableValidator.nameParts(childName).toSet()
        return Regex("\\S+").findAll(text).mapNotNull { match ->
            val word = DecodableValidator.words(match.value).firstOrNull() ?: return@mapNotNull null
            val isName = word in name
            ReaderWord(
                display = match.value,
                word = word,
                sentence = sentences.indexOfFirst { match.range.first in it }.coerceAtLeast(0),
                isName = isName,
                syllables = if (!isName && word.length >= 4 && DecodableValidator.isDecodable(word, syllables)) {
                    word.chunked(2)
                } else {
                    null
                }
            )
        }.toList()
    }
}
