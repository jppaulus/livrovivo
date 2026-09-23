package com.livrovivo.app.domain.model

/**
 * Conteúdo da Trilha da Leitura, lido de `assets/alfabetizacao/trilha.json`.
 * Nada deste conteúdo fica escrito no código: para mudar a trilha, edite `ferramentas/gerar_conteudo.py`.
 */
data class LiteracyTrail(
    val version: Int,
    /** Palavrinhas aprendidas "de vista"; podem aparecer nos livros "Eu leio" desde o início. */
    val supportWords: List<String>,
    val vocabulary: List<VocabularyWord>,
    /** Módulos já ordenados pelo campo `ordem`. */
    val modules: List<LiteracyModule>
) {
    val phases: List<LiteracyPhase> get() = modules.flatMap { it.phases }

    fun module(id: String): LiteracyModule? = modules.find { it.id == id }
    fun phase(id: String): LiteracyPhase? = phases.find { it.id == id }
    fun moduleOf(phaseId: String): LiteracyModule? = modules.find { module -> module.phases.any { it.id == phaseId } }
}

/** Palavra de 2 sílabas do vocabulário, com a figura e o artigo usados nos livros "Eu leio". */
data class VocabularyWord(
    val word: String,
    val syllables: List<String>,
    /** Nome do drawable (ex.: `img_bola`). A figura pode ainda não existir no app. */
    val image: String,
    /** "O" ou "A". */
    val article: String
)

data class LiteracyModule(
    val id: String,
    val title: String,
    val order: Int,
    val phases: List<LiteracyPhase>
)

data class LiteracyPhase(
    val id: String,
    val title: String,
    /** Liberada sem assinatura. */
    val isFree: Boolean,
    /** O que a criança aprende ao concluir a fase: uma letra, 5 sílabas ou 5 palavras. */
    val teaches: List<String>,
    val questions: List<LiteracyQuestion>
)

data class LiteracyQuestion(
    val type: QuestionType,
    /** Frase narrada para a criança. */
    val prompt: String,
    val answer: String,
    /** Botões para tocar (já embaralhados e com a resposta). */
    val options: List<String>,
    /** Blocos para arrastar. No ditado há 2 peças extras erradas. */
    val pieces: List<String>,
    /** Nome do drawable, ou null. */
    val image: String?
)

enum class QuestionType(val code: String) {
    LISTEN_AND_TAP("ouvir_tocar"),
    JOIN("juntar"),
    BUILD_WORD("montar_palavra"),
    PICTURE_WORD("figura_palavra"),
    DICTATION("ditado");

    /** Perguntas respondidas tocando num botão; as outras montam a resposta com peças. */
    val usesOptions: Boolean get() = this == LISTEN_AND_TAP || this == PICTURE_WORD

    companion object {
        fun fromCode(code: String): QuestionType? = entries.find { it.code == code }
    }
}

/**
 * O que a criança já sabe, calculado a partir das fases concluídas (não fica numa tabela).
 * É a base dos livros "Eu leio": só entram palavras que ela consegue ler.
 */
data class LiteracyKnowledge(
    /** Vogais + consoantes concluídas. */
    val letters: Set<String> = emptySet(),
    /** União do "ensina" das fases de sílabas concluídas. */
    val syllables: Set<String> = emptySet(),
    /** União do "ensina" das fases de palavras concluídas. */
    val words: Set<String> = emptySet()
)

/** Resultado guardado de uma fase para uma criança (tabela `literacy_progress`). */
data class PhaseProgress(
    val childId: String,
    val phaseId: String,
    /** Melhor nota já alcançada, de 0 a 3. */
    val stars: Int,
    val attempts: Int,
    /** Total de erros em todas as tentativas (para o painel dos pais). */
    val mistakes: Int,
    /** Primeira conclusão com pelo menos 1 estrela. */
    val completedAt: Long?
) {
    val isCompleted: Boolean get() = stars >= 1

    companion object {
        const val MAX_STARS = 3
    }
}
