package com.livrovivo.app.presentation.literacy

import com.livrovivo.app.core.literacy.LiteracyRules
import com.livrovivo.app.domain.model.LiteracyPhase
import com.livrovivo.app.domain.model.LiteracyQuestion

enum class ActivityFeedback { NONE, CORRECT, TRY_AGAIN }

/**
 * Estado de uma fase sendo jogada. Não depende de tela: a [ActivitySession] muda o estado e a tela só desenha.
 *
 * Um "acerto" é uma pergunta respondida certo na primeira tentativa. Depois de um erro a criança tenta de
 * novo até acertar, mas aquela pergunta já não conta como acerto.
 */
data class ActivityState(
    val phase: LiteracyPhase,
    val index: Int = 0,
    /** Para cada espaço da resposta, o índice da peça colocada nele (ou null se vazio). */
    val slots: List<Int?> = emptyList(),
    val mistakesOnQuestion: Int = 0,
    val correctAnswers: Int = 0,
    val totalMistakes: Int = 0,
    val feedback: ActivityFeedback = ActivityFeedback.NONE,
    /** Última opção errada tocada, para a tela balançar só aquele botão. */
    val wrongOption: String? = null,
    val finished: Boolean = false
) {
    val question: LiteracyQuestion get() = phase.questions[index]
    val questionCount: Int get() = phase.questions.size
    val stars: Int get() = LiteracyRules.starsFor(correctAnswers)

    /** Peças que ainda estão na bandeja (fora dos espaços). */
    val trayPieces: List<Int> get() = question.pieces.indices.filter { it !in slots }

    /** Depois de 2 erros na mesma pergunta, a tela destaca a resposta ou a próxima peça. */
    val showHint: Boolean get() = mistakesOnQuestion >= 2 && feedback != ActivityFeedback.CORRECT

    /** Peça que vai no primeiro espaço vazio (para a dica). */
    val hintPiece: Int?
        get() {
            val slot = slots.indexOfFirst { it == null }.takeIf { it >= 0 } ?: return null
            val solution = LiteracyRules.solvePieces(question.answer, question.pieces) ?: return null
            val needed = question.pieces[solution[slot]]
            return trayPieces.firstOrNull { question.pieces[it] == needed }
        }
}

object ActivitySession {

    fun start(phase: LiteracyPhase): ActivityState = ActivityState(phase = phase).withEmptySlots()

    /** Toque num botão (atividades "ouvir e tocar" e "figura e palavra"). */
    fun chooseOption(state: ActivityState, option: String): ActivityState {
        if (!state.acceptsInput || !state.question.type.usesOptions) return state
        return if (option == state.question.answer) state.correct() else state.wrong().copy(wrongOption = option)
    }

    /**
     * Coloca uma peça num espaço. Sem [slot], vai para o primeiro espaço vazio (tocar na peça).
     * Se o espaço já tinha uma peça, ela volta para a bandeja. Com todos os espaços cheios, confere a resposta.
     */
    fun placePiece(state: ActivityState, pieceIndex: Int, slot: Int? = null): ActivityState {
        if (!state.acceptsInput || state.question.type.usesOptions) return state
        if (pieceIndex !in state.question.pieces.indices || pieceIndex in state.slots) return state
        val target = slot ?: state.slots.indexOfFirst { it == null }
        if (target !in state.slots.indices) return state
        val filled = state.slots.toMutableList().also { it[target] = pieceIndex }
        val placed = state.copy(slots = filled, feedback = ActivityFeedback.NONE, wrongOption = null)
        if (filled.any { it == null }) return placed
        val built = filled.joinToString("") { state.question.pieces[it!!] }
        return if (built == state.question.answer) placed.correct() else placed.wrong().withEmptySlots()
    }

    /** Tocar numa peça já colocada devolve ela para a bandeja. */
    fun removeFromSlot(state: ActivityState, slot: Int): ActivityState {
        if (!state.acceptsInput || slot !in state.slots.indices || state.slots[slot] == null) return state
        return state.copy(slots = state.slots.toMutableList().also { it[slot] = null }, feedback = ActivityFeedback.NONE)
    }

    /** Depois da comemoração do acerto: próxima pergunta, ou fim da fase. */
    fun next(state: ActivityState): ActivityState {
        if (state.feedback != ActivityFeedback.CORRECT) return state
        if (state.index >= state.questionCount - 1) return state.copy(finished = true)
        return state.copy(index = state.index + 1, mistakesOnQuestion = 0, feedback = ActivityFeedback.NONE, wrongOption = null)
            .withEmptySlots()
    }

    /** Some com o "Tente de novo!" quando a criança volta a tentar. */
    fun clearFeedback(state: ActivityState): ActivityState =
        if (state.feedback == ActivityFeedback.TRY_AGAIN) state.copy(feedback = ActivityFeedback.NONE, wrongOption = null) else state

    private val ActivityState.acceptsInput: Boolean get() = !finished && feedback != ActivityFeedback.CORRECT

    private fun ActivityState.correct() = copy(
        feedback = ActivityFeedback.CORRECT,
        correctAnswers = correctAnswers + if (mistakesOnQuestion == 0) 1 else 0,
        wrongOption = null
    )

    private fun ActivityState.wrong() = copy(
        feedback = ActivityFeedback.TRY_AGAIN,
        mistakesOnQuestion = mistakesOnQuestion + 1,
        totalMistakes = totalMistakes + 1
    )

    private fun ActivityState.withEmptySlots(): ActivityState {
        val question = question
        if (question.type.usesOptions) return copy(slots = emptyList())
        val size = LiteracyRules.solvePieces(question.answer, question.pieces)?.size ?: question.pieces.size
        return copy(slots = List(size) { null })
    }
}
