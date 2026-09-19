package com.davidpallier.lumiomusic.data.network

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

@Serializable
data class LyricsOvhResponse(val lyrics: String? = null)

/** https://api.lyrics.ovh - free, no API key, plain text only. */
interface LyricsOvhApi {
    @GET("v1/{artist}/{title}")
    suspend fun getLyrics(
        @Path("artist") artist: String,
        @Path("title") title: String
    ): Response<LyricsOvhResponse>
}
