package com.davidpallier.lumiomusic.data.model

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val trackCount: Int
)
