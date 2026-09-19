package com.davidpallier.lumiomusic.data.lyrics

import com.davidpallier.lumiomusic.data.db.LyricsCacheEntity
import com.davidpallier.lumiomusic.data.model.Lyrics
import com.davidpallier.lumiomusic.data.network.LyricsOvhApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LyricsOvhLyricsProvider @Inject constructor(
    private val api: LyricsOvhApi
) : LyricsProvider {

    override val source: String = LyricsCacheEntity.SOURCE_LYRICS_OVH

    override suspend fun fetch(artist: String, title: String, album: String?, durationMs: Long): Lyrics? =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.getLyrics(artist, title)
                val lyrics = response.body().takeIf { response.isSuccessful }?.lyrics
                lyrics?.takeUnless { it.isBlank() }?.let { Lyrics.Plain(it.trim()) }
            }.getOrNull()
        }
}
