package com.davidpallier.lumiomusic.playback.audio.flac

/** MSB-first bit reader over `data[start, end)`. */
class FlacBitReader(private val data: ByteArray, start: Int, private val end: Int) {
    private var bitPosition: Long = start.toLong() * 8

    val bytePosition: Int get() = ((bitPosition + 7) ushr 3).toInt()

    fun readBits(count: Int): Long {
        var remaining = count
        var result = 0L
        while (remaining > 0) {
            val byteIndex = (bitPosition ushr 3).toInt()
            if (byteIndex >= end) throw FlacFormatException("Unexpected end of FLAC frame")
            val bitOffset = (bitPosition and 7).toInt()
            val available = 8 - bitOffset
            val take = if (available < remaining) available else remaining
            val bits = ((data[byteIndex].toInt() and 0xFF) ushr (available - take)) and ((1 shl take) - 1)
            result = (result shl take) or bits.toLong()
            bitPosition += take
            remaining -= take
        }
        return result
    }

    fun readSigned(count: Int): Long {
        if (count == 0) return 0
        val value = readBits(count)
        return (value shl (64 - count)) shr (64 - count)
    }

    /** Counts zero bits up to the next one bit (consuming it): the quotient of a Rice code. */
    fun readUnary(): Long {
        var count = 0L
        while (true) {
            val byteIndex = (bitPosition ushr 3).toInt()
            if (byteIndex >= end) throw FlacFormatException("Unexpected end of FLAC frame")
            val bitOffset = (bitPosition and 7).toInt()
            val rest = ((data[byteIndex].toInt() and 0xFF) shl bitOffset) and 0xFF
            if (rest == 0) {
                count += 8 - bitOffset
                bitPosition += 8 - bitOffset
            } else {
                val zeros = Integer.numberOfLeadingZeros(rest) - 24
                bitPosition += zeros + 1
                return count + zeros
            }
        }
    }

    fun alignToByte() {
        bitPosition = (bitPosition + 7) and 7L.inv()
    }
}
