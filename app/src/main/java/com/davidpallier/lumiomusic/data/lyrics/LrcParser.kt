package com.davidpallier.lumiomusic.data.lyrics

import com.davidpallier.lumiomusic.data.tags.SyncedLyricsLine

/** Parses standard `[mm:ss.xx]text` LRC lyrics, as returned by LRCLIB's `syncedLyrics` field. */
object LrcParser {

    private val TIMESTAMP_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:\.(\d{1,3}))?]""")

    fun parse(lrc: String): List<SyncedLyricsLine> {
        val lines = mutableListOf<SyncedLyricsLine>()
        for (rawLine in lrc.lineSequence()) {
            val matches = TIMESTAMP_REGEX.findAll(rawLine).toList()
            if (matches.isEmpty()) continue
            val text = rawLine.substring(matches.last().range.last + 1).trim()
            for (match in matches) {
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val fraction = match.groupValues[3]
                val millis = when {
                    fraction.isEmpty() -> 0L
                    fraction.length == 1 -> fraction.toLong() * 100
                    fraction.length == 2 -> fraction.toLong() * 10
                    else -> fraction.take(3).toLong()
                }
                val timestampMs = (minutes * 60_000) + (seconds * 1000) + millis
                lines += SyncedLyricsLine(timestampMs, text)
            }
        }
        return lines.sortedBy { it.timestampMs }
    }
}
