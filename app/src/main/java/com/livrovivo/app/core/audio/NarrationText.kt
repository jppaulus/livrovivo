package com.livrovivo.app.core.audio

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
