package com.livrovivo.app.core.ai

import com.livrovivo.app.core.ai.JsonUtils.array
import com.livrovivo.app.core.ai.JsonUtils.asObjectOrNull
import com.livrovivo.app.core.ai.JsonUtils.string
import com.livrovivo.app.core.ai.JsonUtils.strings
import com.livrovivo.app.domain.model.Chapter
import com.livrovivo.app.domain.model.Choice
import com.livrovivo.app.domain.model.Virtue
import kotlinx.serialization.json.JsonObject
import java.text.Normalizer

/**
 * Valida e corrige a resposta do modelo para que o leitor nunca quebre:
 * escolhas sempre presentes antes do final, final sempre sem escolhas, narração fiel ao texto.
 */
object ChapterSanitizer {

    private val TAG_REGEX = Regex("\\[[a-zA-Z][a-zA-Z \\-]{0,24}]")

    fun toChapter(
        json: JsonObject,
        index: Int,
        isFinal: Boolean,
        fallbackChoices: () -> List<Choice>
    ): Chapter {
        val content = cleanContent(json.string("content").orEmpty())
        if (content.split(Regex("\\s+")).size < 25) {
            throw AiException(AiException.Kind.PARSE, "Capítulo curto demais")
        }

        val choices = if (isFinal) {
            emptyList()
        } else {
            val parsed = json.array("choices")
                ?.mapNotNull { element ->
                    val obj = element.asObjectOrNull() ?: return@mapNotNull null
                    val text = cleanChoice(obj.string("text").orEmpty())
                    if (text.isBlank()) null else Choice(text, index + 1, Virtue.fromCode(obj.string("virtue")))
                }
                ?.distinctBy { it.text.lowercase() }
                .orEmpty()
                .take(2)
            if (parsed.size >= 2) parsed else (parsed + fallbackChoices()).distinctBy { it.text.lowercase() }.take(2)
                .map { it.copy(targetChapterIndex = index + 1) }
        }

        val narration = json.string("narration")?.let { validNarrationOrNull(content, it) }
        val newWords = json.array("newWords")?.strings().orEmpty()
            .map { it.trim().trim('.', ',', '!', '?') }
            .filter { it.length in 3..24 && content.contains(it, ignoreCase = true) }
            .distinctBy { it.lowercase() }
            .take(3)

        return Chapter(
            index = index,
            content = content,
            choices = choices,
            isEnding = isFinal,
            sceneImagePrompt = json.string("illustrationPrompt")?.trim()?.takeIf { it.length > 10 }?.take(900),
            narrationScript = narration,
            newWords = newWords,
            mood = json.string("mood")?.lowercase()?.takeIf { it in StoryPrompts.MOODS }
        )
    }

    fun cleanContent(raw: String): String = raw
        .replace("\r\n", "\n")
        .replace(Regex("^\\s*(cap[ií]tulo\\s+\\d+[:.\\-–—]?\\s*)", RegexOption.IGNORE_CASE), "")
        .replace(TAG_REGEX, "")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex(" *\\n *"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    private fun cleanChoice(raw: String): String = raw
        .replace(TAG_REGEX, "")
        .trim()
        .trim('"', '\'', '“', '”', '-', '•', ' ')
        .replace(Regex("\\s+"), " ")
        .removeSuffix(".")
        .take(90)

    /** Remove marcações [emoção] para vozes que não as entendem. */
    fun stripAudioTags(text: String): String = text
        .replace(TAG_REGEX, "")
        .replace(Regex("[ \\t]{2,}"), " ")
        .replace(Regex(" +([,.!?;:])"), "$1")
        .trim()

    /**
     * A narração só é aceita se, sem as marcações, for praticamente idêntica ao texto exibido
     * (assim o destaque de leitura acompanha a voz).
     */
    fun validNarrationOrNull(content: String, narration: String): String? {
        if (!TAG_REGEX.containsMatchIn(narration)) return null
        val a = normalizeWords(content)
        val b = normalizeWords(stripAudioTags(narration))
        if (a.isEmpty() || b.isEmpty()) return null
        val lengthRatio = b.size.toDouble() / a.size
        if (lengthRatio !in 0.95..1.05) return null
        val common = a.zip(b).count { (x, y) -> x == y }
        return if (common.toDouble() / a.size >= 0.9) narration.trim() else null
    }

    private fun normalizeWords(text: String): List<String> {
        val noAccents = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noAccents.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
    }
}
