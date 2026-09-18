package com.livrovivo.app.presentation.bedtime

import com.livrovivo.app.domain.model.MagicalCompanion

/**
 * O que é dito e mostrado no ritual de dormir.
 *
 * Tudo sem concordância de gênero com a criança e sem culpa: o companheiro está com sono,
 * nunca triste nem pedindo para a criança ficar mais um pouco.
 */
object BedtimeScript {
    const val BREATHS = 3
    const val INHALE_MS = 4_000L
    const val EXHALE_MS = 5_000L

    /** Como cada companheiro solta o ar: a imagem que ajuda a criança a respirar devagar. */
    fun exhaleImage(companion: MagicalCompanion): String = when (companion.id) {
        "bento" -> "e solta o foguinho bem devagar, como um dragãozinho"
        "luna" -> "e solta bem devagar, leve como uma pena"
        "pipoca" -> "e solta bem devagar, como um balão murchando"
        "aurora" -> "e solta bem devagar, espalhando pó de estrela"
        else -> "e solta bem devagar"
    }

    /** Fala de abertura, com marcações de emoção para a narração. */
    fun introScript(companion: MagicalCompanion, childName: String): String =
        "[sighs] Aaaah… que aventura boa! [softly] Agora ${companion.name} está com soninho. " +
            "$childName, vamos respirar juntinhos, bem devagar? " +
            "Puxa o ar pelo nariz… ${exhaleImage(companion)}."

    fun goodnightScript(childName: String): String =
        "[whispers] Boa noite, $childName. Que bom viver essa aventura com você. " +
            "[softly] Sonhe com as estrelas… Até amanhã."

    const val INHALE_CAPTION = "Puxa o ar…"

    fun exhaleCaption(companion: MagicalCompanion): String = "…${exhaleImage(companion)}"
}
