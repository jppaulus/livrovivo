package com.livrovivo.app.core.ai

import java.text.Normalizer

object StoryVocabulary {
    private val meanings = mapOf(
        "coragem" to "Coragem é tentar fazer algo, mesmo quando sentimos um pouquinho de medo. Podemos pedir ajuda.",
        "empatia" to "Empatia é tentar entender como outra pessoa se sente.",
        "curiosidade" to "Curiosidade é a vontade de descobrir e fazer perguntas.",
        "cooperacao" to "Cooperação é ajudar uns aos outros para fazer algo juntos.",
        "criatividade" to "Criatividade é imaginar ideias e jeitos diferentes de fazer as coisas.",
        "constelacao" to "Uma constelação é um grupo de estrelas que imaginamos formando um desenho no céu.",
        "lanterna" to "Uma lanterna é uma luz que podemos carregar para iluminar um lugar.",
        "luar" to "Luar é a luz da Lua que vemos durante a noite.",
        "trilha" to "Uma trilha é um caminho, como os caminhos pequenos de um parque.",
        "semente" to "Uma semente pode dar origem a uma nova planta.",
        "acolher" to "Acolher é receber alguém com atenção e carinho.",
        "gentileza" to "Gentileza é tratar as pessoas com cuidado e respeito."
    )

    fun normalize(text: String): String = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")

    fun explanation(word: String, context: String): String {
        meanings[normalize(word.trim())]?.let { return "$word. $it" }
        val sentence = context.split(Regex("(?<=[.!?])\\s+|\\n+")).firstOrNull {
            normalize(it).contains(normalize(word))
        }?.trim()
        return if (sentence != null) "$word. Vamos ouvir como essa palavra aparece na história: $sentence"
        else "$word. Que tal perguntar a quem está lendo com você o que essa palavra quer dizer?"
    }
}
