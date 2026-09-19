package com.davidpallier.lumiomusic.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per track (see [TrackEntity.id]), caching the result of walking the network lyrics
 * provider chain so it's only ever hit once. [source] == [SOURCE_NONE] is a cached negative
 * result (no provider had lyrics for this track) - it still prevents re-hitting the network.
 */
@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
    @PrimaryKey val trackId: Long,
    val source: String,
    val plainText: String?,
    /** JSON-encoded List<[com.davidpallier.lumiomusic.data.tags.SyncedLyricsLine]>, if synced. */
    val syncedJson: String?,
    val fetchedAt: Long
) {
    companion object {
        const val SOURCE_LRCLIB = "LRCLIB"
        const val SOURCE_LYRICS_OVH = "LYRICS_OVH"
        const val SOURCE_GENIUS = "GENIUS"
        const val SOURCE_NONE = "NONE"
    }
}
