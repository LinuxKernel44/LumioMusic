package com.davidpallier.lumiomusic.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun `parses a single timestamped line`() {
        val lines = LrcParser.parse("[00:12.34]Hello world")
        assertEquals(1, lines.size)
        assertEquals(12_340L, lines[0].timestampMs)
        assertEquals("Hello world", lines[0].text)
    }

    @Test
    fun `parses multiple lines in order regardless of input order`() {
        val lrc = """
            [00:10.00]Second
            [00:05.00]First
            [00:20.00]Third
        """.trimIndent()
        val lines = LrcParser.parse(lrc)
        assertEquals(listOf("First", "Second", "Third"), lines.map { it.text })
        assertEquals(listOf(5_000L, 10_000L, 20_000L), lines.map { it.timestampMs })
    }

    @Test
    fun `handles minutes over 59`() {
        val lines = LrcParser.parse("[75:00.00]Long track")
        assertEquals(75L * 60_000, lines[0].timestampMs)
    }

    @Test
    fun `handles two and three digit fractional seconds`() {
        val twoDigit = LrcParser.parse("[00:01.50]Two digit")
        assertEquals(1_500L, twoDigit[0].timestampMs)

        val threeDigit = LrcParser.parse("[00:01.500]Three digit")
        assertEquals(1_500L, threeDigit[0].timestampMs)
    }

    @Test
    fun `expands repeated timestamps on one line into separate entries`() {
        val lines = LrcParser.parse("[00:10.00][00:40.00]Chorus")
        assertEquals(2, lines.size)
        assertEquals(listOf(10_000L, 40_000L), lines.map { it.timestampMs })
        assertTrue(lines.all { it.text == "Chorus" })
    }

    @Test
    fun `ignores metadata lines without a timestamp`() {
        val lrc = """
            [ar:Some Artist]
            [ti:Some Title]
            [00:01.00]Actual lyric
        """.trimIndent()
        val lines = LrcParser.parse(lrc)
        assertEquals(1, lines.size)
        assertEquals("Actual lyric", lines[0].text)
    }

    @Test
    fun `returns empty list for blank input`() {
        assertTrue(LrcParser.parse("").isEmpty())
    }
}
