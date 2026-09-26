package com.livrovivo.app.core.audio

/**
 * Divide a página em partes para a narração começar rápido: a primeira parte é curta (gera em
 * poucos segundos e já toca) e as seguintes são maiores, sempre cortando em parágrafo ou frase.
 */
object NarrationChunker {

    /** Uma parte da narração: o trecho exibido na tela e o texto (com marcações) enviado ao motor. */
    data class Chunk(val displayRange: IntRange, val speakText: String)

    private val PARAGRAPH_BREAK = Regex("\\n[ \\t]*\\n+")

    fun chunk(text: String, script: String?, firstMaxChars: Int = 180, maxChars: Int = 520): List<Chunk> {
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
            val paragraphLimit = if (index == 0) firstMaxChars else maxChars
            if (range.count() <= paragraphLimit) {
                pieces += range to spoken
            } else {
                // Parágrafo muito longo: corta em frases (usa o texto exibido para falar também,
                // porque não há como alinhar as marcações dentro do parágrafo).
                splitLongParagraph(text, range, paragraphLimit).forEach { pieces += it to text.substring(it.first, it.last + 1) }
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
 * (estilo karaokê) com qualquer motor de voz. Quando há o áudio, o [SentenceAligner] acerta os pontos pelas
 * pausas da voz; sem ele, vale a estimativa pelo peso de cada frase.
 */
class NarrationTimeline private constructor(
    val sentences: List<IntRange>,
    /** Peso de cada frase, proporcional ao tempo de fala. */
    val weights: DoubleArray
) {
    private val cumulativeWeights = DoubleArray(weights.size).also { acc ->
        var sum = 0.0
        weights.forEachIndexed { i, weight -> sum += weight; acc[i] = sum }
    }

    val totalWeight: Double get() = cumulativeWeights.lastOrNull() ?: 0.0

    /** Índice da frase correspondente a uma fração [0, 1] do áudio. */
    fun sentenceAt(fraction: Float): Int {
        if (sentences.isEmpty()) return -1
        val total = totalWeight
        if (total <= 0.0) return 0
        val target = fraction.coerceIn(0f, 1f) * total
        val idx = cumulativeWeights.indexOfFirst { it >= target }
        return if (idx < 0) sentences.lastIndex else idx
    }

    companion object {
        private val SENTENCE_END = Regex("[.!?…]+[\"'”»)]*\\s+|\\n+")
        private val FINAL_PUNCTUATION = Regex("[.!?…]+")

        /**
         * Letras custam tempo; vírgulas e travessões, uma pausa curta; o fim da frase, uma pausa maior (as
         * reticências contam uma vez só: contadas ponto a ponto, "Blub..." pesava como uma frase inteira).
         * Ajustado em 25/09/2026 com as vozes da Azure: erro médio da estimativa caiu de 0,9 s para 0,34 s.
         */
        fun weightOf(sentence: String): Double {
            val letters = sentence.count { it.isLetterOrDigit() }
            val shortPauses = sentence.count { it == ',' || it == ';' || it == ':' || it == '—' }
            val endings = FINAL_PUNCTUATION.findAll(sentence).count()
            return (letters + shortPauses * 2 + endings * 6).toDouble().coerceAtLeast(1.0)
        }

        fun build(text: String): NarrationTimeline {
            val ranges = mutableListOf<IntRange>()
            var start = 0
            SENTENCE_END.findAll(text).forEach { match ->
                val end = match.range.first + match.value.trimEnd().length
                addRange(text, start, end, ranges)
                start = match.range.last + 1
            }
            addRange(text, start, text.length, ranges)

            val weights = DoubleArray(ranges.size) { i -> weightOf(text.substring(ranges[i].first, ranges[i].last + 1)) }
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
