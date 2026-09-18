package com.livrovivo.app.presentation.home

import com.livrovivo.app.domain.model.AdventureMemory
import com.livrovivo.app.domain.model.MagicalCompanion
import com.livrovivo.app.domain.model.Virtue

/**
 * O que o companheiro diz na tela inicial. Quando a criança viveu uma aventura há pouco,
 * ele lembra de uma escolha dela; senão, fica a saudação da hora do dia.
 *
 * Nada aqui cobra a criança: se ela ficou dias sem abrir o app, o companheiro simplesmente
 * cumprimenta — nunca diz que sentiu falta, que ficou triste ou que ela sumiu.
 */
object CompanionGreeting {

    /** Depois disso a lembrança já não é "de ontem", e a saudação volta a ser a da hora do dia. */
    const val MEMORY_WINDOW_MS = 3 * 24 * 60 * 60 * 1000L

    private enum class Moment(val hello: String, val plain: String, val invitation: String) {
        MORNING(
            hello = "Bom dia!",
            plain = "Bom dia! Que tal uma aventura para começar o dia com um sorriso?",
            invitation = "Que tal uma aventura para começar o dia com um sorriso?"
        ),
        AFTERNOON(
            hello = "Boa tarde!",
            plain = "Boa tarde! Estou com a imaginação a mil. Vamos criar uma história?",
            invitation = "Vamos criar mais uma história?"
        ),
        NIGHT(
            hello = "Boa noite!",
            plain = "Boa noite! Eu já estou de pijama. Que tal uma história calminha antes de dormir?",
            invitation = "Que tal uma história calminha antes de dormir?"
        );

        companion object {
            fun of(hour: Int): Moment = when (hour) {
                in 5..11 -> MORNING
                in 12..17 -> AFTERNOON
                else -> NIGHT
            }
        }
    }

    fun forHome(hour: Int, companion: MagicalCompanion, memory: AdventureMemory?, now: Long): String {
        val moment = Moment.of(hour)
        val choice = memory?.highlightAsClause()
        if (memory == null || choice == null || now - memory.updatedAt > MEMORY_WINDOW_MS) {
            return moment.plain
        }

        // Só quem estava na aventura diz "eu lembro"; um companheiro novo ouviu falar dela.
        val recall = if (memory.companionId == companion.id) {
            "Ainda lembro quando você escolheu $choice."
        } else {
            "${MagicalCompanion.findById(memory.companionId).name} me contou que você escolheu $choice."
        }
        return "${moment.hello} $recall ${compliment(memory.highlightVirtue)} ${moment.invitation}"
    }

    /** Elogios sem concordância de gênero, para servirem a qualquer criança. */
    private fun compliment(virtue: Virtue?): String = when (virtue) {
        Virtue.CORAGEM -> "Que coragem!"
        Virtue.EMPATIA -> "Que coração bondoso!"
        Virtue.CRIATIVIDADE -> "Que ideia genial!"
        Virtue.CURIOSIDADE -> "Que curiosidade boa!"
        Virtue.CALMA -> "Que calma de gente sábia!"
        Virtue.COOPERACAO -> "Juntos somos mais fortes!"
        null -> "Foi demais!"
    }
}
