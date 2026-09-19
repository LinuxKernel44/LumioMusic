package com.davidpallier.lumiomusic.data.lyrics

import com.davidpallier.lumiomusic.data.model.Lyrics

interface LyricsProvider {
    val source: String

    /** Returns null if this provider has nothing for the given track (not an error). */
    suspend fun fetch(artist: String, title: String, album: String?, durationMs: Long): Lyrics?
}
