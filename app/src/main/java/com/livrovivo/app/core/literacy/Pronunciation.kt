package com.livrovivo.app.core.literacy

import com.livrovivo.app.domain.model.VocabularyWord

/**
 * Como a voz deve falar letras e sílabas soltas. Os motores de voz leem "BA" como "bê-á" e "B" de jeitos
 * diferentes; escrito do jeito que se fala ("bá", "bê"), eles acertam.
 *
 * A mesma regra está em `ferramentas/gerar_audios.py`, que grava os áudios definitivos: se mudar aqui,
 * mude lá também.
 */
object Pronunciation {

    /** Nome das letras, como a criança ouve em "Toque na letra B" ("letra bê"). */
    private val LETTER_NAMES = mapOf(
        "A" to "á", "E" to "é", "I" to "i", "O" to "ó", "U" to "u",
        "B" to "bê", "C" to "cê", "D" to "dê", "F" to "éfe", "G" to "gê", "H" to "agá", "J" to "jota",
        "K" to "cá", "L" to "éle", "M" to "ême", "N" to "êne", "P" to "pê", "Q" to "quê", "R" to "érre",
        "S" to "ésse", "T" to "tê", "V" to "vê", "W" to "dáblio", "X" to "xis", "Y" to "ípsilon", "Z" to "zê"
    )

    /** Vogal da sílaba com acento, para a voz ler a sílaba inteira de uma vez (BA → bá, BE → bê). */
    private val SYLLABLE_VOWELS = mapOf('A' to "á", 'E' to "ê", 'I' to "i", 'O' to "ô", 'U' to "u")

    /** Arquivo gravado da letra ou sílaba em res/raw: "BA" → som_ba. */
    fun resourceName(unit: String): String = "som_" + unit.lowercase()

    /** Como escrever a letra ou sílaba para a voz: "B" → "bê", "BA" → "bá". Outras palavras: em minúsculas. */
    fun hint(unit: String): String {
        val upper = unit.uppercase()
        LETTER_NAMES[upper]?.let { return it }
        if (upper.length == 2 && upper[0].isLetter() && upper[1] in SYLLABLE_VOWELS) {
            return upper[0].lowercase() + SYLLABLE_VOWELS.getValue(upper[1])
        }
        return unit.lowercase()
    }

    /**
     * Palavra de exemplo do vocabulário que começa com a letra ou sílaba (B → BOLA, BA → BALA).
     * Vogais não têm exemplo: nenhuma palavra do vocabulário começa com vogal.
     */
    fun exampleWord(unit: String, vocabulary: List<VocabularyWord>): String? {
        val upper = unit.uppercase()
        if (upper.length == 1 && upper in "AEIOU") return null
        return vocabulary.firstOrNull { it.word.startsWith(upper) }?.word
    }

    /** O que a voz do app fala quando não há áudio gravado: "bá, de bala" (ou só "bê", sem exemplo). */
    fun fallbackText(unit: String, example: String?): String =
        example?.let { "${hint(unit)}, de ${it.lowercase()}" } ?: hint(unit)

    /** Uma instrução dividida em frase ("Toque na sílaba") e a letra ou sílaba do fim ("BE"). */
    data class PromptParts(val carrier: String, val target: String)

    /**
     * Separa a letra ou sílaba que fecha a instrução, para ela sair pelo áudio gravado (ou pela dica de
     * pronúncia) em vez de a voz ler "BE" soletrado. Instruções sem letra/sílaba no fim devolvem null.
     * @param units letras e sílabas da trilha, em maiúsculas.
     */
    fun promptTarget(prompt: String, units: Set<String>): PromptParts? {
        val trimmed = prompt.trim()
        val match = Regex("^(.*\\S)\\s+(\\p{Lu}{1,2})[.!?]*$").find(trimmed) ?: return null
        val (carrier, target) = match.destructured
        if (target !in units) return null
        return PromptParts(carrier.trimEnd(',', ':'), target)
    }
}
