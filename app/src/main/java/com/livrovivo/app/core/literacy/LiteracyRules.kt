package com.livrovivo.app.core.literacy

import com.livrovivo.app.domain.model.AgeGroup

/** Regras da Trilha da Leitura que não dependem de tela nem de banco. */
object LiteracyRules {

    /** 5 acertos = 3 estrelas; 3 ou 4 = 2; 1 ou 2 = 1; nenhum = 0. */
    fun starsFor(correctAnswers: Int): Int = when {
        correctAnswers >= 5 -> 3
        correctAnswers >= 3 -> 2
        correctAnswers >= 1 -> 1
        else -> 0
    }

    /** A trilha aparece para 3-5 e 6-8. Para 9+ fica escondida, a não ser que os pais a ativem. */
    fun isTrailVisible(ageGroup: String?, enabledByParents: Boolean = false): Boolean =
        enabledByParents || AgeGroup.fromCode(ageGroup) != AgeGroup.EXPLORER

    /**
     * Diz quais peças, em ordem, formam [answer] (cada peça usada no máximo uma vez).
     * Devolve os índices das peças em [pieces], ou null se não der para formar.
     * No ditado sobram 2 peças erradas; nas outras atividades todas são usadas.
     */
    fun solvePieces(answer: String, pieces: List<String>): List<Int>? {
        fun solve(rest: String, used: Set<Int>): List<Int>? {
            if (rest.isEmpty()) return emptyList()
            pieces.forEachIndexed { index, piece ->
                if (index !in used && piece.isNotEmpty() && rest.startsWith(piece)) {
                    solve(rest.removePrefix(piece), used + index)?.let { return listOf(index) + it }
                }
            }
            return null
        }
        return solve(answer, emptySet())
    }
}
