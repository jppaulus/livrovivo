package com.livrovivo.app.core.literacy

import com.livrovivo.app.domain.model.LiteracyTrail
import com.livrovivo.app.domain.model.PhaseProgress
import com.livrovivo.app.domain.model.Story

/**
 * Seção "Leitura" do painel dos pais. Os números mostram o que a criança **fez** no app (atividade de uso),
 * não medem aprendizagem; os textos não prometem resultado.
 */
data class ReadingInsights(
    val completedPhases: Int = 0,
    val totalPhases: Int = 0,
    /** Letras da trilha na ordem, com true para as das fases concluídas. */
    val letters: List<Pair<String, Boolean>> = emptyList(),
    /** Uma linha por família de sílabas (BA BE BI BO BU), com true para as das fases concluídas. */
    val syllableRows: List<List<Pair<String, Boolean>>> = emptyList(),
    val booksRead: Int = 0,
    val booksTotal: Int = 0,
    val pagesReadAlone: Int = 0,
    /** Até 3 fases com mais tentativas erradas ("Sílabas com R"). */
    val practiceTogether: List<String> = emptyList(),
    /** Sugestão de brincadeira fora da tela, ligada ao que a criança fez por último. */
    val offlineIdea: String = ""
) {
    val learnedSyllables: Int get() = syllableRows.sumOf { row -> row.count { it.second } }
    val totalSyllables: Int get() = syllableRows.sumOf { it.size }
}

object ReadingInsightsBuilder {

    fun build(trail: LiteracyTrail, progress: List<PhaseProgress>, books: List<Story>, pagesReadAlone: Int): ReadingInsights {
        val completed = LiteracyRules.completedPhaseIds(progress)
        val knowledge = LiteracyRules.knowledge(trail, completed)
        val letterModules = listOf(LiteracyRules.MODULE_VOWELS, LiteracyRules.MODULE_CONSONANTS)
        val letters = letterModules.mapNotNull { trail.module(it) }
            .flatMap { module -> module.phases.flatMap { it.teaches } }
            .map { it to (it in knowledge.letters) }
        val syllableRows = trail.module(LiteracyRules.MODULE_SYLLABLES)?.phases.orEmpty()
            .map { phase -> phase.teaches.map { it to (it in knowledge.syllables) } }
        val practice = progress
            .filter { it.mistakes > 0 }
            .sortedByDescending { it.mistakes }
            .mapNotNull { trail.phase(it.phaseId)?.title }
            .take(3)
        return ReadingInsights(
            completedPhases = completed.count { trail.phase(it) != null },
            totalPhases = trail.phases.size,
            letters = letters,
            syllableRows = syllableRows,
            booksRead = books.count { it.isCompleted },
            booksTotal = books.size,
            pagesReadAlone = pagesReadAlone,
            practiceTogether = practice,
            offlineIdea = offlineIdea(trail, progress)
        )
    }

    /** Brincadeira fora da tela a partir da fase concluída mais recente. */
    fun offlineIdea(trail: LiteracyTrail, progress: List<PhaseProgress>): String {
        val latest = progress.filter { it.isCompleted }.maxByOrNull { it.completedAt ?: 0L }
            ?.let { trail.phase(it.phaseId) }
            ?: return "Procurem juntos as letras do nome da criança em placas, embalagens e livros pela casa."
        val module = trail.moduleOf(latest.id)?.id
        val first = latest.teaches.firstOrNull()
        return when {
            module == LiteracyRules.MODULE_VOWELS || module == LiteracyRules.MODULE_CONSONANTS ->
                "Procurem na cozinha objetos que começam com a letra ${first ?: "que a criança aprendeu"}."
            module == LiteracyRules.MODULE_SYLLABLES ->
                "Brinquem de falar palavras que começam com ${spokenList(latest.teaches)}."
            module == LiteracyRules.MODULE_WORDS && first != null ->
                "Escrevam juntos, num papel, a palavra $first e desenhem o que ela é."
            else ->
                "Façam um ditado de brincadeira: um adulto diz uma palavra de um livro \"Eu leio\" e a criança monta com letras de papel."
        }
    }

    /** "BA, BE, BI, BO ou BU". */
    private fun spokenList(items: List<String>): String =
        if (items.size <= 1) items.joinToString() else items.dropLast(1).joinToString(", ") + " ou " + items.last()
}
