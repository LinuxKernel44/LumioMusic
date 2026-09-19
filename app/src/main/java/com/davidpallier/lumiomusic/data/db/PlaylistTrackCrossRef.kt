package com.davidpallier.lumiomusic.data.db

import androidx.room.Entity
import androidx.room.Index

/** Join row ordering [trackId] within [playlistId] by [position] (0-based, gaps allowed). */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    indices = [Index("trackId")]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: Long,
    val position: Int
)
