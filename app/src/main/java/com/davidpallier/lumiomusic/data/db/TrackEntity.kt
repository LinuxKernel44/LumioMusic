package com.davidpallier.lumiomusic.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    indices = [Index(value = ["uriString"], unique = true)]
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uriString: String,
    val documentId: String,
    val displayName: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val durationMs: Long,
    val mimeType: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val sampleRateHz: Int?,
    val bitrateBps: Int?,
    val channelCount: Int?,
    val coverArtPath: String?,
    /** Plain or LRC-formatted text embedded in the file (Vorbis LYRICS / ID3 USLT). Null if none. */
    val embeddedLyricsPlain: String?,
    /** JSON-encoded [com.davidpallier.lumiomusic.data.tags.SyncedLyricsLine] list from ID3 SYLT. Null if none. */
    val embeddedLyricsSyncedJson: String?
)
