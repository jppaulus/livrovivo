package com.livrovivo.app.core.literacy

import com.livrovivo.app.domain.model.AgeGroup
import com.livrovivo.app.domain.model.LiteracyKnowledge
import com.livrovivo.app.domain.model.LiteracyTrail
import com.livrovivo.app.domain.model.PhaseProgress

/** Regras da Trilha da Leitura que não dependem de tela nem de banco. */
object LiteracyRules {

    // Ids dos módulos no trilha.json. Dizem o que o "ensina" de cada fase significa.
    const val MODULE_VOWELS = "vogais"
    const val MODULE_CONSONANTS = "consoantes"
    const val MODULE_SYLLABLES = "silabas"
    const val MODULE_WORDS = "palavras"
    const val MODULE_DICTATION = "ditado"

    /** O primeiro livro "Eu leio" sai com 3 fases de Sílabas (com B, C e D já dá: BOCA, DADO, DOCE). */
    const val FIRST_BOOK_SYLLABLE_PHASES = 3

    /** Depois do primeiro, um livro novo a cada 2 fases de sílabas, palavras ou ditado. */
    const val PHASES_PER_NEW_BOOK = 2

    /** Fases com pelo menos 1 estrela. */
    fun completedPhaseIds(progress: List<PhaseProgress>): Set<String> =
        progress.filter { it.isCompleted }.map { it.phaseId }.toSet()

    /**
     * Fases liberadas. Um módulo libera quando todas as fases do módulo anterior têm pelo menos 1 estrela;
     * dentro do módulo, as fases liberam em ordem. Fase já concluída continua liberada para jogar de novo.
     */
    fun unlockedPhaseIds(trail: LiteracyTrail, completed: Set<String>): Set<String> {
        val unlocked = mutableSetOf<String>()
        for (module in trail.modules) {
            for (phase in module.phases) {
                unlocked += phase.id
                if (phase.id !in completed) break
            }
            if (module.phases.any { it.id !in completed }) break
        }
        // Fase já concluída sempre pode ser jogada de novo.
        return unlocked + completed.filter { trail.phase(it) != null }
    }

    /** Quantos livros "Eu leio" a criança já ganhou com as fases concluídas. */
    fun booksEarned(trail: LiteracyTrail, completed: Set<String>): Int {
        fun done(moduleId: String): Int = trail.module(moduleId)?.phases?.count { it.id in completed } ?: 0
        val syllablePhases = done(MODULE_SYLLABLES)
        if (syllablePhases < FIRST_BOOK_SYLLABLE_PHASES) return 0
        val counted = syllablePhases + done(MODULE_WORDS) + done(MODULE_DICTATION)
        return 1 + (counted - FIRST_BOOK_SYLLABLE_PHASES) / PHASES_PER_NEW_BOOK
    }

    fun knowledge(trail: LiteracyTrail, completed: Set<String>): LiteracyKnowledge {
        fun taught(vararg moduleIds: String): Set<String> = moduleIds
            .mapNotNull { trail.module(it) }
            .flatMap { module -> module.phases.filter { it.id in completed }.flatMap { it.teaches } }
            .toSet()
        return LiteracyKnowledge(
            letters = taught(MODULE_VOWELS, MODULE_CONSONANTS),
            syllables = taught(MODULE_SYLLABLES),
            words = taught(MODULE_WORDS)
        )
    }

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
