package com.davidpallier.lumiomusic.data.model

/** One row per distinct artist, aggregated from [com.davidpallier.lumiomusic.data.db.TrackEntity]. */
data class ArtistSummary(
    val artist: String,
    val albumCount: Int,
    val trackCount: Int
)
