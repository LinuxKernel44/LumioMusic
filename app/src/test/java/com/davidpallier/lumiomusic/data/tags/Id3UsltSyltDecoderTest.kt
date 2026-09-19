package com.davidpallier.lumiomusic.data.tags

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Id3UsltSyltDecoderTest {

    private fun uslt(encoding: Int, language: String, descriptor: String, text: String): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(encoding)
        out.write(language.toByteArray(Charsets.ISO_8859_1))
        out.write(descriptor.toByteArray(Charsets.UTF_8))
        out.write(0)
        out.write(text.toByteArray(Charsets.UTF_8))
        return out.toByteArray()
    }

    private fun sylt(lines: List<Pair<String, Long>>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(3) // UTF-8
        out.write("eng".toByteArray(Charsets.ISO_8859_1))
        out.write(2) // timestamp format: milliseconds
        out.write(1) // content type: lyrics
        out.write(0) // empty descriptor terminator
        for ((text, timestampMs) in lines) {
            out.write(text.toByteArray(Charsets.UTF_8))
            out.write(0)
            out.write((timestampMs shr 24).toInt() and 0xFF)
            out.write((timestampMs shr 16).toInt() and 0xFF)
            out.write((timestampMs shr 8).toInt() and 0xFF)
            out.write(timestampMs.toInt() and 0xFF)
        }
        return out.toByteArray()
    }

    @Test
    fun `decodes plain UTF-8 USLT body`() {
        val data = uslt(encoding = 3, language = "eng", descriptor = "", text = "Hello lyrics")
        assertEquals("Hello lyrics", Id3UsltSyltDecoder.decodeUslt(data))
    }

    @Test
    fun `decodes USLT with a non-empty descriptor`() {
        val data = uslt(encoding = 3, language = "eng", descriptor = "desc", text = "Body text")
        assertEquals("Body text", Id3UsltSyltDecoder.decodeUslt(data))
    }

    @Test
    fun `returns null for USLT data too short to contain a header`() {
        assertNull(Id3UsltSyltDecoder.decodeUslt(byteArrayOf(3, 'e'.code.toByte())))
    }

    @Test
    fun `decodes SYLT lines with correct timestamps and order`() {
        val data = sylt(listOf("Line one" to 1_000L, "Line two" to 2_500L))
        val lines = Id3UsltSyltDecoder.decodeSylt(data)
        requireNotNull(lines)
        assertEquals(2, lines.size)
        assertEquals("Line one", lines[0].text)
        assertEquals(1_000L, lines[0].timestampMs)
        assertEquals("Line two", lines[1].text)
        assertEquals(2_500L, lines[1].timestampMs)
    }

    @Test
    fun `skips blank SYLT lines`() {
        val data = sylt(listOf("" to 1_000L, "Real line" to 2_000L))
        val lines = Id3UsltSyltDecoder.decodeSylt(data)
        requireNotNull(lines)
        assertEquals(1, lines.size)
        assertEquals("Real line", lines[0].text)
    }

    @Test
    fun `returns null for truncated SYLT data`() {
        assertNull(Id3UsltSyltDecoder.decodeSylt(byteArrayOf(3, 0, 0, 0, 0)))
    }
}
