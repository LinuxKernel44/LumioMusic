package com.davidpallier.lumiomusic.playback.audio.flac

class FlacFormatException(message: String) : Exception(message)

/** The FLAC STREAMINFO metadata block: the fixed properties of a stream. */
class FlacStreamInfo(
    val minBlockSize: Int,
    val maxBlockSize: Int,
    val sampleRate: Int,
    val channels: Int,
    val bitsPerSample: Int,
    val totalSamples: Long,
    val md5: ByteArray
) {
    /** Width of the integer PCM this decoder emits: 16, 24 or 32 bits per sample. */
    val outputBits: Int get() = when {
        bitsPerSample <= 16 -> 16
        bitsPerSample <= 24 -> 24
        else -> 32
    }

    companion object {
        private const val STREAMINFO_BYTES = 34

        /**
         * Accepts what Media3's FlacExtractor puts in `Format.initializationData[0]` (the `fLaC`
         * marker, a 4-byte block header, then STREAMINFO) or a bare 34-byte STREAMINFO.
         */
        fun parse(data: ByteArray): FlacStreamInfo {
            val offset = when {
                data.size >= 8 + STREAMINFO_BYTES && data[0] == 'f'.code.toByte() && data[1] == 'L'.code.toByte() &&
                    data[2] == 'a'.code.toByte() && data[3] == 'C'.code.toByte() -> 8
                data.size == STREAMINFO_BYTES -> 0
                else -> throw FlacFormatException("Not a FLAC STREAMINFO block (${data.size} bytes)")
            }
            val reader = FlacBitReader(data, offset, data.size)
            val minBlock = reader.readBits(16).toInt()
            val maxBlock = reader.readBits(16).toInt()
            reader.readBits(24) // min frame size
            reader.readBits(24) // max frame size
            val sampleRate = reader.readBits(20).toInt()
            val channels = reader.readBits(3).toInt() + 1
            val bps = reader.readBits(5).toInt() + 1
            val total = reader.readBits(36)
            val md5 = ByteArray(16) { reader.readBits(8).toByte() }
            if (sampleRate <= 0 || minBlock < 16 || maxBlock < minBlock) {
                throw FlacFormatException("Invalid STREAMINFO")
            }
            return FlacStreamInfo(minBlock, maxBlock, sampleRate, channels, bps, total, md5)
        }
    }
}
