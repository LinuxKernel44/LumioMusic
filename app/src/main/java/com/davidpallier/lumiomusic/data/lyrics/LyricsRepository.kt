package com.davidpallier.lumiomusic.data.lyrics

import com.davidpallier.lumiomusic.data.db.LyricsCacheDao
import com.davidpallier.lumiomusic.data.db.LyricsCacheEntity
import com.davidpallier.lumiomusic.data.db.TrackEntity
import com.davidpallier.lumiomusic.data.model.Lyrics
import com.davidpallier.lumiomusic.data.tags.SyncedLyricsLine
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implements the project's lyrics priority order (see CLAUDE.md): embedded tag first, then the
 * network provider chain (LRCLIB -> lyrics.ovh -> Genius, first hit wins), with fetched results
 * cached in Room so the network is only ever hit once per track. Never writes lyrics back into
 * the user's audio files.
 */
@Singleton
class LyricsRepository @Inject constructor(
    private val lyricsCacheDao: LyricsCacheDao,
    private val json: Json,
    private val lrclibProvider: LrclibLyricsProvider,
    private val lyricsOvhProvider: LyricsOvhLyricsProvider,
    private val geniusProvider: GeniusLyricsProvider
) {
    private val chain: List<LyricsProvider> = listOf(lrclibProvider, lyricsOvhProvider, geniusProvider)

    suspend fun getLyrics(track: TrackEntity): Lyrics? {
        embeddedLyricsOf(track)?.let { return it }

        val cached = lyricsCacheDao.get(track.id)
        if (cached != null) return cached.toLyrics()

        val fetched = fetchFromChain(track)
        lyricsCacheDao.upsert(fetched.toCacheEntity(track.id))
        return fetched.lyrics
    }

    private fun embeddedLyricsOf(track: TrackEntity): Lyrics? {
        track.embeddedLyricsSyncedJson?.let { syncedJson ->
            val lines = runCatching { json.decodeFromString<List<SyncedLyricsLine>>(syncedJson) }.getOrNull()
            if (!lines.isNullOrEmpty()) return Lyrics.Synced(lines)
        }
        track.embeddedLyricsPlain?.takeUnless { it.isBlank() }?.let { return Lyrics.Plain(it) }
        return null
    }

    private suspend fun fetchFromChain(track: TrackEntity): FetchOutcome {
        for (provider in chain) {
            if (provider === geniusProvider && !LyricsFeatureFlags.GENIUS_ENABLED) continue
            val lyrics = provider.fetch(track.artist, track.title, track.album, track.durationMs)
            if (lyrics != null) return FetchOutcome(lyrics, provider.source)
        }
        return FetchOutcome(null, LyricsCacheEntity.SOURCE_NONE)
    }

    private fun FetchOutcome.toCacheEntity(trackId: Long) = LyricsCacheEntity(
        trackId = trackId,
        source = source,
        plainText = (lyrics as? Lyrics.Plain)?.text,
        syncedJson = (lyrics as? Lyrics.Synced)?.let { runCatching { json.encodeToString(it.lines) }.getOrNull() },
        fetchedAt = System.currentTimeMillis()
    )

    private fun LyricsCacheEntity.toLyrics(): Lyrics? {
        val synced = syncedJson
        val plain = plainText
        return when {
            synced != null -> runCatching { json.decodeFromString<List<SyncedLyricsLine>>(synced) }
                .getOrNull()?.let { Lyrics.Synced(it) }
            plain != null -> Lyrics.Plain(plain)
            else -> null
        }
    }

    private data class FetchOutcome(val lyrics: Lyrics?, val source: String)
}
