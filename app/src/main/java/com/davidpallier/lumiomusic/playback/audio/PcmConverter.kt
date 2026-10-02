package com.davidpallier.lumiomusic.playback.audio

import android.media.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Converts little-endian PCM between the encodings the bit-perfect sink handles. Every sample is
 * widened to the full-scale int32 domain first, so a 16/24-bit source (or the float32 a decoder
 * produces from it) becomes the exact same integer in the DAC's width - nothing is dithered or
 * rounded unless [volume] is not 1.
 */
object PcmConverter {
    const val ENCODING_16 = AudioFormat.ENCODING_PCM_16BIT
    const val ENCODING_24 = AudioFormat.ENCODING_PCM_24BIT_PACKED
    const val ENCODING_32 = AudioFormat.ENCODING_PCM_32BIT
    const val ENCODING_FLOAT = AudioFormat.ENCODING_PCM_FLOAT

    fun bytesPerSample(encoding: Int): Int = when (encoding) {
        ENCODING_16 -> 2
        ENCODING_24 -> 3
        ENCODING_32, ENCODING_FLOAT -> 4
        else -> throw IllegalArgumentException("Unsupported PCM encoding $encoding")
    }

    fun outputBytes(inputBytes: Int, inputEncoding: Int, outputEncoding: Int): Int =
        inputBytes / bytesPerSample(inputEncoding) * bytesPerSample(outputEncoding)

    /** Consumes all complete samples of [input] and appends the converted samples to [output]. */
    fun convert(input: ByteBuffer, inputEncoding: Int, output: ByteBuffer, outputEncoding: Int, volume: Float) {
        val src = input.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        output.order(ByteOrder.LITTLE_ENDIAN)
        val samples = src.remaining() / bytesPerSample(inputEncoding)
        val applyVolume = volume != 1f
        repeat(samples) {
            var value = readFullScale(src, inputEncoding)
            if (applyVolume) value = Math.round(value * volume.toDouble())
            writeFromFullScale(output, outputEncoding, value)
        }
        input.position(input.position() + samples * bytesPerSample(inputEncoding))
    }

    private fun readFullScale(src: ByteBuffer, encoding: Int): Long = when (encoding) {
        ENCODING_16 -> src.short.toLong() shl 16
        ENCODING_24 -> {
            val b0 = src.get().toInt() and 0xFF
            val b1 = src.get().toInt() and 0xFF
            val b2 = src.get().toInt()
            ((b2 shl 16) or (b1 shl 8) or b0).toLong() shl 8
        }
        ENCODING_32 -> src.int.toLong()
        ENCODING_FLOAT -> {
            val f = src.float.toDouble().coerceIn(-1.0, 1.0)
            Math.round(f * 2147483648.0).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
        }
        else -> throw IllegalArgumentException("Unsupported PCM encoding $encoding")
    }

    private fun writeFromFullScale(dst: ByteBuffer, encoding: Int, value: Long) {
        when (encoding) {
            ENCODING_32 -> dst.putInt(value.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt())
            ENCODING_24 -> {
                val v = ((value + 128) shr 8).coerceIn(-8388608L, 8388607L).toInt()
                dst.put(v.toByte()).put((v shr 8).toByte()).put((v shr 16).toByte())
            }
            ENCODING_16 -> dst.putShort(((value + 32768) shr 16).coerceIn(-32768L, 32767L).toInt().toShort())
            else -> throw IllegalArgumentException("Unsupported PCM encoding $encoding")
        }
    }
}
