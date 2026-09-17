package com.livrovivo.app.core.audio

/**
 * Divide a página em partes para a narração começar rápido: a primeira parte é curta (gera em
 * poucos segundos e já toca) e as seguintes são maiores, sempre cortando em parágrafo ou frase.
 */
object NarrationChunker {

    /** Uma parte da narração: o trecho exibido na tela e o texto (com marcações) enviado ao motor. */
    data class Chunk(val displayRange: IntRange, val speakText: String)

    private val PARAGRAPH_BREAK = Regex("\\n[ \\t]*\\n+")

    fun chunk(text: String, script: String?, firstMaxChars: Int = 260, maxChars: Int = 520): List<Chunk> {
        val paragraphs = paragraphRanges(text)
        if (paragraphs.isEmpty()) return emptyList()

        // O roteiro com marcações de emoção tem os mesmos parágrafos do texto exibido.
        val scriptParagraphs = script
            ?.split(PARAGRAPH_BREAK)
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.takeIf { it.size == paragraphs.size }

        val pieces = mutableListOf<Pair<IntRange, String>>()
        paragraphs.forEachIndexed { index, range ->
            val spoken = scriptParagraphs?.get(index) ?: text.substring(range.first, range.last + 1)
            if (range.count() <= maxChars) {
                pieces += range to spoken
            } else {
                // Parágrafo muito longo: corta em frases (usa o texto exibido para falar também,
                // porque não há como alinhar as marcações dentro do parágrafo).
                splitLongParagraph(text, range, maxChars).forEach { pieces += it to text.substring(it.first, it.last + 1) }
            }
        }

        val chunks = mutableListOf<Chunk>()
        var currentRange: IntRange? = null
        var currentSpeak = StringBuilder()
        for ((range, spoken) in pieces) {
            val limit = if (chunks.isEmpty()) firstMaxChars else maxChars
            val fits = currentRange != null && (range.last - currentRange.first + 1) <= limit
            if (currentRange == null) {
                currentRange = range
                currentSpeak = StringBuilder(spoken)
            } else if (fits) {
                currentRange = currentRange.first..range.last
                currentSpeak.append("\n\n").append(spoken)
            } else {
                chunks += Chunk(currentRange, currentSpeak.toString())
                currentRange = range
                currentSpeak = StringBuilder(spoken)
            }
        }
        currentRange?.let { chunks += Chunk(it, currentSpeak.toString()) }
        return chunks
    }

    private fun paragraphRanges(text: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var start = 0
        PARAGRAPH_BREAK.findAll(text).forEach { match ->
            addTrimmed(text, start, match.range.first, ranges)
            start = match.range.last + 1
        }
        addTrimmed(text, start, text.length, ranges)
        return ranges
    }

    private fun splitLongParagraph(text: String, range: IntRange, maxChars: Int): List<IntRange> {
        val sentences = NarrationTimeline.build(text.substring(range.first, range.last + 1)).sentences
            .map { (it.first + range.first)..(it.last + range.first) }
        if (sentences.isEmpty()) return listOf(range)
        val result = mutableListOf<IntRange>()
        var current: IntRange? = null
        for (sentence in sentences) {
            current = when {
                current == null -> sentence
                sentence.last - current.first + 1 <= maxChars -> current.first..sentence.last
                else -> {
                    result += current
                    sentence
                }
            }
        }
        current?.let { result += it }
        return result
    }

    private fun addTrimmed(text: String, from: Int, to: Int, out: MutableList<IntRange>) {
        var s = from
        var e = to
        while (s < e && text[s].isWhitespace()) s++
        while (e > s && text[e - 1].isWhitespace()) e--
        if (e > s) out.add(s until e)
    }
}

/**
 * Divide o texto em frases e estima em que frase a narração está, para destacar a leitura
 * (estilo karaokê) com qualquer motor de voz.
 */
class NarrationTimeline private constructor(val sentences: List<IntRange>, private val cumulativeWeights: DoubleArray) {

    /** Índice da frase correspondente a uma fração [0, 1] do áudio. */
    fun sentenceAt(fraction: Float): Int {
        if (sentences.isEmpty()) return -1
        val total = cumulativeWeights.last()
        if (total <= 0.0) return 0
        val target = fraction.coerceIn(0f, 1f) * total
        val idx = cumulativeWeights.indexOfFirst { it >= target }
        return if (idx < 0) sentences.lastIndex else idx
    }

    companion object {
        private val SENTENCE_END = Regex("[.!?…]+[\"'”»)]*\\s+|\\n+")

        fun build(text: String): NarrationTimeline {
            val ranges = mutableListOf<IntRange>()
            var start = 0
            SENTENCE_END.findAll(text).forEach { match ->
                val end = match.range.first + match.value.trimEnd().length
                addRange(text, start, end, ranges)
                start = match.range.last + 1
            }
            addRange(text, start, text.length, ranges)

            val weights = DoubleArray(ranges.size)
            var acc = 0.0
            ranges.forEachIndexed { i, range ->
                val sentence = text.substring(range.first, range.last + 1)
                // Letras custam tempo; pontuação indica pausas extras na fala.
                val letters = sentence.count { it.isLetterOrDigit() }
                val pauses = sentence.count { it == ',' || it == ';' || it == ':' || it == '—' } * 3 +
                    sentence.count { it == '.' || it == '!' || it == '?' || it == '…' } * 6
                acc += letters + pauses + 8
                weights[i] = acc
            }
            return NarrationTimeline(ranges, weights)
        }

        private fun addRange(text: String, from: Int, to: Int, out: MutableList<IntRange>) {
            var s = from
            var e = to
            while (s < e && text[s].isWhitespace()) s++
            while (e > s && text[e - 1].isWhitespace()) e--
            if (e > s && text.substring(s, e).any { it.isLetterOrDigit() }) out.add(s until e)
        }
    }
}
