package com.davidpallier.lumiomusic.data.tags

import kotlinx.serialization.Serializable

@Serializable
data class SyncedLyricsLine(val timestampMs: Long, val text: String)

sealed class EmbeddedLyrics {
    data class Plain(val text: String) : EmbeddedLyrics()
    data class Synced(val lines: List<SyncedLyricsLine>) : EmbeddedLyrics()
}
