package com.livrovivo.app.core.audio

import com.livrovivo.app.domain.model.Chapter
import com.livrovivo.app.domain.model.Choice
import java.text.Normalizer

/** Keeps spoken choices and their highlights aligned without changing the saved story. */
data class ChoiceNarration(
    val text: String,
    val script: String?,
    val choiceSentences: List<IntRange>
) {
    fun choiceAt(sentence: Int): Int = choiceSentences.indexOfFirst { sentence in it }

    companion object {
        fun build(chapter: Chapter, includeChoices: Boolean, choicesOnly: Boolean = false): ChoiceNarration {
            val choices = chapter.choices.takeIf { includeChoices && !chapter.isEnding }.orEmpty()
            val text = StringBuilder(if (choicesOnly) "" else chapter.content)
            val offsets = mutableListOf<IntRange>()
            if (choices.isNotEmpty()) {
                if (text.isNotEmpty()) text.append("\n\n")
                text.append("Agora é sua vez. Qual caminho você escolhe?")
                choices.forEachIndexed { index, choice ->
                    text.append("\n\n")
                    val start = text.length
                    text.append("Opção ${index + 1}: ${choice.text.trim()}")
                    if (text.last() !in ".!?…") text.append('.')
                    offsets += start until text.length
                }
            }
            val spoken = text.toString()
            val sentences = NarrationTimeline.build(spoken).sentences
            val ranges = offsets.map { offset ->
                val indices = sentences.indices.filter { sentences[it].first in offset }
                if (indices.isEmpty()) IntRange.EMPTY else indices.first()..indices.last()
            }
            val script = if (choicesOnly) null else chapter.narrationScript?.let {
                it + spoken.removePrefix(chapter.content)
            }
            return ChoiceNarration(spoken, script, ranges)
        }
    }
}

/** Familiar action symbols; unknown actions keep a neutral, numbered path. */
fun Choice.actionSymbol(index: Int): String {
    val normalized = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
    val symbols = listOf(
        "lanterna" to "🔦", "barco" to "⛵", "ponte" to "🌉", "porta" to "🚪",
        "mapa" to "🗺️", "pegada" to "🐾", "estrela" to "⭐", "flor" to "🌼",
        "arvore" to "🌳", "desenhar" to "🎨", "cantar" to "🎵", "ouvir" to "👂",
        "respirar" to "🌬️", "abracar" to "🫂", "ajuda" to "🤝", "juntos" to "🤝",
        "perguntar" to "💬", "conversar" to "💬", "procurar" to "🔍"
    )
    return symbols.firstOrNull { (word, _) -> normalized.contains(word) }?.second
        ?: if (index == 0) "1️⃣" else "2️⃣"
}
