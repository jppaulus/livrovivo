package com.livrovivo.app.core.audio

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * Gera os sons de fundo em WAV para ouvir no computador. Só roda quando pedido:
 * defina a variável AMBIENCE_WAV_DIR com a pasta de saída e rode esta classe de teste.
 * Cada arquivo tem duas voltas do loop, para dar para ouvir a emenda.
 */
class AmbienceSamplesExport {

    @Test
    fun `export ambience samples as wav`() {
        val dir = System.getenv("AMBIENCE_WAV_DIR")
        assumeTrue("defina AMBIENCE_WAV_DIR para gerar os arquivos", !dir.isNullOrBlank())
        val out = File(dir!!).apply { mkdirs() }
        Ambience.entries.forEach { kind ->
            val loop = AmbienceSynth.render(kind)
            writeWav(File(out, "${kind.ordinal + 1}-${kind.label}.wav"), loop + loop)
        }
    }

    private fun writeWav(file: File, pcm: ShortArray) {
        val rate = AmbienceSynth.SAMPLE_RATE
        val dataBytes = pcm.size * 2
        DataOutputStream(FileOutputStream(file).buffered()).use { out ->
            fun intLe(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            fun shortLe(v: Int) = out.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            out.writeBytes("RIFF"); intLe(36 + dataBytes); out.writeBytes("WAVE")
            out.writeBytes("fmt "); intLe(16); shortLe(1); shortLe(1); intLe(rate); intLe(rate * 2); shortLe(2); shortLe(16)
            out.writeBytes("data"); intLe(dataBytes)
            pcm.forEach { shortLe(it.toInt()) }
        }
    }
}
