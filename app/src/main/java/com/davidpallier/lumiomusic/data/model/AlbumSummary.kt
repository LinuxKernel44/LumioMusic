package com.davidpallier.lumiomusic.data.model

/** One row per distinct album, aggregated from [com.davidpallier.lumiomusic.data.db.TrackEntity]. */
data class AlbumSummary(
    val album: String,
    val albumArtist: String?,
    val trackCount: Int,
    val coverArtPath: String?,
    val year: Int?
)
