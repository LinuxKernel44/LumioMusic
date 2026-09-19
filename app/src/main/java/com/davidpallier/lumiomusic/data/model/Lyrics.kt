package com.davidpallier.lumiomusic.data.model

import com.davidpallier.lumiomusic.data.tags.SyncedLyricsLine

/** Unified lyrics result, regardless of whether it came from an embedded tag or a network fetch. */
sealed class Lyrics {
    data class Plain(val text: String) : Lyrics()
    data class Synced(val lines: List<SyncedLyricsLine>) : Lyrics()
}
