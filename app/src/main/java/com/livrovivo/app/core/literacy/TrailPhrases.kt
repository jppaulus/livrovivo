package com.livrovivo.app.core.literacy

/**
 * Frases fixas que as telas da trilha falam. `ferramentas/gerar_audios.py` grava cada uma com a voz do Gemini
 * (lista `FRASES_FIXAS`, conferida por teste): se mudar um texto aqui, mude lá também e grave de novo.
 */
object TrailPhrases {
    const val CORRECT = "Muito bem!"
    const val TRY_AGAIN = "Tente de novo!"
    const val BOOK_NOTICE = "Você ganhou um livro novo para ler sozinho!"
    const val ASK_ADULT = "Esta fase faz parte da assinatura. Chame um adulto para ver com você!"
    const val PHASE_LOCKED = "Termine a fase de antes para abrir esta."
    const val BOOK_FINISHED = "Parabéns! Você leu o livro todo! Quer ler de novo?"

    fun result(stars: Int): String = when (stars) {
        3 -> "Incrível! Você ganhou 3 estrelas!"
        2 -> "Muito bem! Você ganhou 2 estrelas!"
        1 -> "Boa! Você ganhou 1 estrela!"
        else -> "Você treinou bastante! Vamos tentar de novo?"
    }

    fun moduleLocked(title: String): String = "Termine as fases de antes para abrir $title."

    /** Todas as frases que não dependem da trilha (as de módulo trancado usam os títulos do `trilha.json`). */
    val FIXED: List<String> = listOf(CORRECT, TRY_AGAIN) + (3 downTo 0).map(::result) +
        listOf(BOOK_NOTICE, ASK_ADULT, PHASE_LOCKED, BOOK_FINISHED)
}
