package com.davidpallier.lumiomusic.data.tags

data class ParsedTrackMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val durationMs: Long,
    val sampleRateHz: Int?,
    val bitrateBps: Int?,
    val channelCount: Int?,
    val embeddedLyrics: EmbeddedLyrics?,
    val coverArt: ByteArray?
)
