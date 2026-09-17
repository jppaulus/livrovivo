package com.livrovivo.app.core.audio

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavWriter {
    /** Cabeçalho RIFF/WAVE para PCM 16-bit little-endian. */
    fun header(pcmBytes: Int, sampleRate: Int, channels: Int = 1, bitsPerSample: Int = 16): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        return ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + pcmBytes)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1) // PCM
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmBytes)
        }.array()
    }

    fun write(file: File, pcm: ByteArray, sampleRate: Int) {
        FileOutputStream(file).use { out ->
            out.write(header(pcm.size, sampleRate))
            out.write(pcm)
        }
    }
}

/**
 * Converte PCM 16-bit mono em AAC (.m4a) para o áudio ocupar ~10x menos espaço no aparelho.
 * Se o codificador não estiver disponível, quem chama deve salvar em WAV.
 */
object AacEncoder {
    fun encode(pcm: ByteArray, sampleRate: Int, output: File, bitRate: Int = 64_000) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        var trackIndex = -1
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            var inputOffset = 0
            var inputDone = false
            var outputDone = false
            var samplesQueued = 0L

            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val buffer = codec.getInputBuffer(inIndex)!!
                        buffer.clear()
                        val chunk = minOf(buffer.remaining() and 0x7FFFFFFE, pcm.size - inputOffset)
                        val presentationUs = samplesQueued * 1_000_000L / sampleRate
                        if (chunk <= 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, presentationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buffer.put(pcm, inputOffset, chunk)
                            codec.queueInputBuffer(inIndex, 0, chunk, presentationUs, 0)
                            inputOffset += chunk
                            samplesQueued += chunk / 2
                        }
                    }
                }

                when (val outIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outIndex >= 0) {
                        val encoded = codec.getOutputBuffer(outIndex)!!
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (info.size > 0 && !isConfig && muxerStarted) {
                            encoded.position(info.offset)
                            encoded.limit(info.offset + info.size)
                            muxer.writeSampleData(trackIndex, encoded, info)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            try {
                codec.stop()
            } catch (_: Exception) {
            }
            codec.release()
            try {
                if (muxerStarted) muxer.stop()
            } catch (_: Exception) {
            }
            muxer.release()
        }
    }
}
