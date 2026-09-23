package com.livrovivo.app.core.literacy

import com.livrovivo.app.domain.model.AgeGroup
import com.livrovivo.app.domain.model.LiteracyKnowledge
import com.livrovivo.app.domain.model.LiteracyPhase
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

    /** Todas as sílabas que a trilha ensina (BA ... VU). */
    fun allSyllables(trail: LiteracyTrail): Set<String> =
        trail.module(MODULE_SYLLABLES)?.phases?.flatMap { it.teaches }?.toSet().orEmpty()

    /** Fases com pelo menos 1 estrela. */
    fun completedPhaseIds(progress: List<PhaseProgress>): Set<String> =
        progress.filter { it.isCompleted }.map { it.phaseId }.toSet()

    /** A criança pode jogar esta fase? Sem assinatura, só as fases grátis. */
    fun canPlay(phase: LiteracyPhase, subscriber: Boolean): Boolean = subscriber || phase.isFree

    /**
     * Fases liberadas. Um módulo libera quando todas as fases **que a criança pode jogar** do módulo anterior
     * têm pelo menos 1 estrela; dentro do módulo, as fases liberam em ordem. Sem assinatura, as fases pagas
     * ficam de fora (e não seguram a trilha). Fase já concluída continua liberada para jogar de novo.
     */
    fun unlockedPhaseIds(trail: LiteracyTrail, completed: Set<String>, subscriber: Boolean = true): Set<String> {
        val unlocked = mutableSetOf<String>()
        for (module in trail.modules) {
            val playable = module.phases.filter { canPlay(it, subscriber) }
            for (phase in playable) {
                unlocked += phase.id
                if (phase.id !in completed) break
            }
            if (playable.any { it.id !in completed }) break
        }
        // Fase já concluída sempre pode ser jogada de novo.
        return unlocked + completed.filter { trail.phase(it) != null }
    }

    /** Quantos livros "Eu leio" a criança já ganhou com as fases concluídas. */
    fun booksEarned(trail: LiteracyTrail, completed: Set<String>, subscriber: Boolean = true): Int =
        bookTriggerPhases(trail, completed, subscriber).size

    /**
     * A fase que liberou cada livro, em ordem. O livro usa o que a criança sabia naquela fase.
     * - Com assinatura: a 3ª fase de sílabas, depois uma a cada 2 fases de sílabas, palavras ou ditado.
     * - Sem assinatura: 1 livro por módulo (Sílabas, Palavras e Ditado), ao concluir as fases grátis dele,
     *   quando a criança já lê palavras suficientes para um livro. Com só B e C (Sílabas) ela lê uma palavra,
     *   então na prática saem os livros de Palavras e de Ditado.
     */
    fun bookTriggerPhases(trail: LiteracyTrail, completed: Set<String>, subscriber: Boolean = true): List<LiteracyPhase> {
        if (!subscriber) return freeBookTriggers(trail, completed)
        val syllablePhases = trail.module(MODULE_SYLLABLES)?.phases?.count { it.id in completed } ?: 0
        if (syllablePhases < FIRST_BOOK_SYLLABLE_PHASES) return emptyList()
        val counted = listOf(MODULE_SYLLABLES, MODULE_WORDS, MODULE_DICTATION)
            .mapNotNull { trail.module(it) }
            .flatMap { module -> module.phases.filter { it.id in completed } }
        return counted.filterIndexed { index, _ ->
            val position = index + 1
            position >= FIRST_BOOK_SYLLABLE_PHASES && (position - FIRST_BOOK_SYLLABLE_PHASES) % PHASES_PER_NEW_BOOK == 0
        }
    }

    private fun freeBookTriggers(trail: LiteracyTrail, completed: Set<String>): List<LiteracyPhase> =
        listOf(MODULE_SYLLABLES, MODULE_WORDS, MODULE_DICTATION).mapNotNull { trail.module(it) }.mapNotNull { module ->
            val free = module.phases.filter { it.isFree }
            val last = free.lastOrNull() ?: return@mapNotNull null
            last.takeIf {
                free.all { it.id in completed } &&
                    readableWords(trail, knowledgeUpTo(trail, completed, last)) >= MIN_BOOK_WORDS
            }
        }

    /** Com menos palavras que isso não dá para escrever um livro (o roteiro mais simples usa 3). */
    const val MIN_BOOK_WORDS = 3

    /** Palavras do vocabulário que a criança já consegue ler. */
    fun readableWords(trail: LiteracyTrail, knowledge: LiteracyKnowledge): Int =
        trail.vocabulary.count { DecodableValidator.isDecodable(it.word, knowledge.syllables) || it.word in knowledge.words }

    /** O que a criança sabia logo depois de concluir [phase] (fases concluídas até ela, na ordem da trilha). */
    fun knowledgeUpTo(trail: LiteracyTrail, completed: Set<String>, phase: LiteracyPhase): LiteracyKnowledge {
        val position = trail.phases.indexOfFirst { it.id == phase.id }
        val before = trail.phases.take(position + 1).map { it.id }.filter { it in completed }.toSet()
        return knowledge(trail, before)
    }

    /**
     * Sílabas treinadas numa fase, para o livro liberado por ela usar: a fase de sílabas ensina as próprias;
     * as de palavras e ditado treinam as sílabas das palavras delas.
     */
    fun phaseSyllables(trail: LiteracyTrail, phase: LiteracyPhase): Set<String> {
        if (trail.moduleOf(phase.id)?.id == MODULE_SYLLABLES) return phase.teaches.toSet()
        val words = (phase.teaches + phase.questions.map { it.answer }).toSet()
        return trail.vocabulary.filter { it.word in words }.flatMap { it.syllables }.toSet()
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
