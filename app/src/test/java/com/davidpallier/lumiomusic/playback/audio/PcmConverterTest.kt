package com.davidpallier.lumiomusic.playback.audio

import android.media.AudioFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class PcmConverterTest {
    private val e16 = AudioFormat.ENCODING_PCM_16BIT
    private val e24 = AudioFormat.ENCODING_PCM_24BIT_PACKED
    private val e32 = AudioFormat.ENCODING_PCM_32BIT
    private val eFloat = AudioFormat.ENCODING_PCM_FLOAT

    private fun bytes(vararg values: Int): ByteBuffer =
        ByteBuffer.allocate(values.size).also { b -> values.forEach { b.put(it.toByte()) }; b.flip() }

    private fun run(input: ByteBuffer, inEnc: Int, outEnc: Int, volume: Float = 1f): ByteArray {
        val out = ByteBuffer.allocate(PcmConverter.outputBytes(input.remaining(), inEnc, outEnc))
        PcmConverter.convert(input, inEnc, out, outEnc, volume)
        return out.array()
    }

    @Test fun `16 to 24 keeps the value and pads the low byte`() {
        // 0x1234 and -2 (0xFFFE) little endian
        val out = run(bytes(0x34, 0x12, 0xFE, 0xFF), e16, e24)
        assertArrayEquals(byteArrayOf(0x00, 0x34, 0x12, 0x00, 0xFE.toByte(), 0xFF.toByte()), out)
    }

    @Test fun `16 to 32 shifts left by 16`() {
        val out = run(bytes(0x34, 0x12), e16, e32)
        assertEquals(0x12340000, ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).int)
    }

    @Test fun `24 to 32 shifts left by 8 and keeps the sign`() {
        // -1 as 24-bit is FF FF FF
        val out = run(bytes(0xFF, 0xFF, 0xFF), e24, e32)
        assertEquals(-256, ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).int)
    }

    @Test fun `float that came from 16-bit audio converts back to the identical 16-bit integers`() {
        val samples = intArrayOf(0, 1, -1, 12345, -12345, 32767, -32768)
        val f = ByteBuffer.allocate(samples.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { f.putFloat(it / 32768f) }
        f.flip()
        val out = ByteBuffer.wrap(run(f, eFloat, e16)).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { assertEquals(it, out.short.toInt()) }
    }

    @Test fun `float that came from 24-bit audio converts back to the identical 24-bit integers`() {
        val samples = intArrayOf(0, 1, -1, 8388607, -8388608, 4000001, -123457)
        val f = ByteBuffer.allocate(samples.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { f.putFloat(it / 8388608f) }
        f.flip()
        val out = ByteBuffer.wrap(run(f, eFloat, e24))
        samples.forEach {
            val b0 = out.get().toInt() and 0xFF
            val b1 = out.get().toInt() and 0xFF
            val b2 = out.get().toInt()
            assertEquals(it, (b2 shl 16) or (b1 shl 8) or b0)
        }
    }

    @Test fun `out of range float is clamped instead of wrapping`() {
        val f = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putFloat(2.0f).putFloat(-2.0f).flip()
        val out = ByteBuffer.wrap(run(f as ByteBuffer, eFloat, e32)).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(Int.MAX_VALUE, out.int)
        assertEquals(Int.MIN_VALUE, out.int)
    }

    @Test fun `volume below one scales the signal`() {
        val out = run(bytes(0x00, 0x40), e16, e16, volume = 0.5f) // 16384 -> 8192
        assertEquals(8192, ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).short.toInt())
    }

    @Test fun `input is fully consumed and a trailing partial sample is left alone`() {
        val input = bytes(0x01, 0x00, 0x02) // one 16-bit sample plus one stray byte
        run(input, e16, e16)
        assertEquals(1, input.remaining())
    }
}
