package com.davidpallier.lumiomusic.data.lyrics

import com.davidpallier.lumiomusic.data.db.LyricsCacheEntity
import com.davidpallier.lumiomusic.data.model.Lyrics
import com.davidpallier.lumiomusic.data.network.LrclibApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class LrclibLyricsProvider @Inject constructor(
    private val api: LrclibApi
) : LyricsProvider {

    override val source: String = LyricsCacheEntity.SOURCE_LRCLIB

    override suspend fun fetch(artist: String, title: String, album: String?, durationMs: Long): Lyrics? =
        withContext(Dispatchers.IO) {
            runCatching {
                val response = api.getLyrics(
                    artistName = artist,
                    trackName = title,
                    albumName = album,
                    durationSeconds = (durationMs / 1000).toInt().takeIf { it > 0 }
                )
                val body = response.body().takeIf { response.isSuccessful } ?: return@withContext null
                when {
                    !body.syncedLyrics.isNullOrBlank() -> Lyrics.Synced(LrcParser.parse(body.syncedLyrics))
                    !body.plainLyrics.isNullOrBlank() -> Lyrics.Plain(body.plainLyrics)
                    else -> null
                }
            }.getOrNull()
        }
}
