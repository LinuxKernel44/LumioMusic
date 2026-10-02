package com.davidpallier.lumiomusic.playback.audio.flac

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A pure-Kotlin FLAC frame decoder. It exists because the platform FLAC codec narrows 24-bit
 * audio to 16 bits (or, with a float request, is unreliable on some devices): this one returns the
 * exact integer samples at the stream's native depth, which is what a bit-perfect output needs.
 *
 * Decoding is lossless by construction; CRCs are not verified (a corrupt frame decodes to garbage
 * or throws [FlacFormatException]).
 */
class FlacFrameDecoder(val streamInfo: FlacStreamInfo) {

    private val channels = streamInfo.channels
    private val outputBits = streamInfo.outputBits
    private val bytesPerSample = outputBits / 8

    private var channelSamples = Array(channels) { LongArray(streamInfo.maxBlockSize) }
    private var residualScratch = LongArray(0)

    /** Interleaved little-endian PCM of everything decoded since the last [resetOutput]. */
    var output: ByteBuffer = ByteBuffer.allocate(streamInfo.maxBlockSize * channels * bytesPerSample * 2)
        .order(ByteOrder.LITTLE_ENDIAN)
        private set

    fun resetOutput() {
        output.clear()
    }

    /** Decodes every complete frame in `data[offset, offset+length)`; returns the frame count. */
    fun decode(data: ByteArray, offset: Int, length: Int): Int {
        val end = offset + length
        var position = offset
        var frames = 0
        while (position + 2 <= end && isFrameSync(data, position)) {
            position += decodeFrame(data, position, end)
            frames++
        }
        if (frames == 0 && length > 0) throw FlacFormatException("No FLAC frame sync found")
        return frames
    }

    private fun isFrameSync(data: ByteArray, position: Int) =
        (data[position].toInt() and 0xFF) == 0xFF && (data[position + 1].toInt() and 0xFE) == 0xF8

    /** Decodes one frame starting at [start]; returns the number of bytes it occupied. */
    private fun decodeFrame(data: ByteArray, start: Int, end: Int): Int {
        val reader = FlacBitReader(data, start, end)
        if (reader.readBits(14) != 0x3FFEL) throw FlacFormatException("Bad frame sync")
        reader.readBits(1) // reserved
        reader.readBits(1) // blocking strategy
        val blockSizeCode = reader.readBits(4).toInt()
        val sampleRateCode = reader.readBits(4).toInt()
        val channelAssignment = reader.readBits(4).toInt()
        val sampleSizeCode = reader.readBits(3).toInt()
        reader.readBits(1) // reserved

        skipUtf8Number(reader)
        val blockSize = when (blockSizeCode) {
            0 -> throw FlacFormatException("Reserved block size")
            1 -> 192
            in 2..5 -> 576 shl (blockSizeCode - 2)
            6 -> reader.readBits(8).toInt() + 1
            7 -> reader.readBits(16).toInt() + 1
            else -> 256 shl (blockSizeCode - 8)
        }
        when (sampleRateCode) {
            12 -> reader.readBits(8)
            13, 14 -> reader.readBits(16)
        }
        reader.readBits(8) // header CRC-8

        val frameBits = when (sampleSizeCode) {
            0 -> streamInfo.bitsPerSample
            1 -> 8
            2 -> 12
            4 -> 16
            5 -> 20
            6 -> 24
            7 -> 32
            else -> throw FlacFormatException("Reserved sample size")
        }
        val frameChannels = when (channelAssignment) {
            in 0..7 -> channelAssignment + 1
            8, 9, 10 -> 2
            else -> throw FlacFormatException("Reserved channel assignment")
        }
        if (frameChannels != channels) throw FlacFormatException("Channel count changed mid-stream")
        if (blockSize > channelSamples[0].size) {
            channelSamples = Array(channels) { LongArray(blockSize) }
        }

        for (channel in 0 until channels) {
            val sideChannel = (channelAssignment == 8 && channel == 1) ||
                (channelAssignment == 9 && channel == 0) ||
                (channelAssignment == 10 && channel == 1)
            decodeSubframe(reader, frameBits + if (sideChannel) 1 else 0, blockSize, channelSamples[channel])
        }
        decorrelate(channelAssignment, blockSize)

        reader.alignToByte()
        reader.readBits(16) // frame CRC-16
        writePcm(blockSize, frameBits)
        return reader.bytePosition - start
    }

    private fun skipUtf8Number(reader: FlacBitReader) {
        val first = reader.readBits(8).toInt()
        val extraBytes = when {
            first and 0x80 == 0 -> 0
            first and 0xE0 == 0xC0 -> 1
            first and 0xF0 == 0xE0 -> 2
            first and 0xF8 == 0xF0 -> 3
            first and 0xFC == 0xF8 -> 4
            first and 0xFE == 0xFC -> 5
            first == 0xFE -> 6
            else -> throw FlacFormatException("Bad UTF-8 frame number")
        }
        repeat(extraBytes) { reader.readBits(8) }
    }

    private fun decodeSubframe(reader: FlacBitReader, bitsPerSample: Int, blockSize: Int, out: LongArray) {
        if (reader.readBits(1) != 0L) throw FlacFormatException("Bad subframe header")
        val type = reader.readBits(6).toInt()
        var wasted = 0
        if (reader.readBits(1) == 1L) wasted = reader.readUnary().toInt() + 1
        val bits = bitsPerSample - wasted
        if (bits <= 0 || bits > 33) throw FlacFormatException("Bad sample width")

        when {
            type == 0 -> {
                val value = reader.readSigned(bits)
                java.util.Arrays.fill(out, 0, blockSize, value)
            }
            type == 1 -> for (i in 0 until blockSize) out[i] = reader.readSigned(bits)
            type in 8..15 -> {
                val order = type and 7
                if (order > 4) throw FlacFormatException("Bad fixed predictor order")
                decodeFixed(reader, bits, blockSize, order, out)
            }
            type >= 32 -> decodeLpc(reader, bits, blockSize, (type and 31) + 1, out)
            else -> throw FlacFormatException("Reserved subframe type")
        }
        if (wasted > 0) for (i in 0 until blockSize) out[i] = out[i] shl wasted
    }

    private fun decodeFixed(reader: FlacBitReader, bits: Int, blockSize: Int, order: Int, out: LongArray) {
        for (i in 0 until order) out[i] = reader.readSigned(bits)
        decodeResidual(reader, blockSize, order, out)
        for (i in order until blockSize) {
            val residual = out[i]
            out[i] = when (order) {
                0 -> residual
                1 -> residual + out[i - 1]
                2 -> residual + 2 * out[i - 1] - out[i - 2]
                3 -> residual + 3 * out[i - 1] - 3 * out[i - 2] + out[i - 3]
                else -> residual + 4 * out[i - 1] - 6 * out[i - 2] + 4 * out[i - 3] - out[i - 4]
            }
        }
    }

    private fun decodeLpc(reader: FlacBitReader, bits: Int, blockSize: Int, order: Int, out: LongArray) {
        for (i in 0 until order) out[i] = reader.readSigned(bits)
        val precision = reader.readBits(4).toInt() + 1
        if (precision == 16) throw FlacFormatException("Bad LPC precision")
        val shift = reader.readSigned(5).toInt()
        if (shift < 0) throw FlacFormatException("Negative LPC shift")
        val coefficients = LongArray(order) { reader.readSigned(precision) }
        decodeResidual(reader, blockSize, order, out)
        for (i in order until blockSize) {
            var sum = 0L
            for (j in 0 until order) sum += coefficients[j] * out[i - 1 - j]
            out[i] += sum shr shift
        }
    }

    /** Reads the Rice-coded residual into `out[predictorOrder until blockSize]`. */
    private fun decodeResidual(reader: FlacBitReader, blockSize: Int, predictorOrder: Int, out: LongArray) {
        val method = reader.readBits(2).toInt()
        if (method > 1) throw FlacFormatException("Bad residual coding method")
        val parameterBits = if (method == 0) 4 else 5
        val escape = if (method == 0) 15 else 31
        val partitionOrder = reader.readBits(4).toInt()
        val partitions = 1 shl partitionOrder
        if (blockSize shr partitionOrder < predictorOrder) throw FlacFormatException("Bad partition order")

        var index = predictorOrder
        for (partition in 0 until partitions) {
            val parameter = reader.readBits(parameterBits).toInt()
            val count = (blockSize shr partitionOrder) - if (partition == 0) predictorOrder else 0
            if (parameter == escape) {
                val rawBits = reader.readBits(5).toInt()
                repeat(count) { out[index++] = reader.readSigned(rawBits) }
            } else {
                repeat(count) {
                    val quotient = reader.readUnary()
                    val remainder = if (parameter == 0) 0L else reader.readBits(parameter)
                    val folded = (quotient shl parameter) or remainder
                    out[index++] = (folded ushr 1) xor -(folded and 1)
                }
            }
        }
    }

    private fun decorrelate(channelAssignment: Int, blockSize: Int) {
        val c0 = channelSamples[0]
        val c1 = if (channels > 1) channelSamples[1] else c0
        when (channelAssignment) {
            8 -> for (i in 0 until blockSize) c1[i] = c0[i] - c1[i] // left, side -> right
            9 -> for (i in 0 until blockSize) c0[i] = c0[i] + c1[i] // side, right -> left
            10 -> for (i in 0 until blockSize) {
                val side = c1[i]
                val mid = (c0[i] shl 1) or (side and 1)
                c0[i] = (mid + side) shr 1
                c1[i] = (mid - side) shr 1
            }
        }
    }

    private fun writePcm(blockSize: Int, frameBits: Int) {
        val needed = blockSize * channels * bytesPerSample
        if (output.remaining() < needed) {
            val grown = ByteBuffer.allocate(maxOf(output.capacity() * 2, output.position() + needed))
                .order(ByteOrder.LITTLE_ENDIAN)
            output.flip()
            grown.put(output)
            output = grown
        }
        val shift = outputBits - frameBits // widening (8/12/20-bit sources); 0 for native widths
        for (i in 0 until blockSize) {
            for (channel in 0 until channels) {
                val sample = if (shift >= 0) channelSamples[channel][i] shl shift else channelSamples[channel][i] shr -shift
                when (outputBits) {
                    16 -> output.putShort(sample.toShort())
                    24 -> {
                        val v = sample.toInt()
                        output.put(v.toByte()).put((v shr 8).toByte()).put((v shr 16).toByte())
                    }
                    else -> output.putInt(sample.toInt())
                }
            }
        }
    }
}
