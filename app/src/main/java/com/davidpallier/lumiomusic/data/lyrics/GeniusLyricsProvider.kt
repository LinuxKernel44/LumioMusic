package com.davidpallier.lumiomusic.data.lyrics

import com.davidpallier.lumiomusic.data.db.LyricsCacheEntity
import com.davidpallier.lumiomusic.data.model.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import javax.inject.Inject

@Serializable
private data class GeniusSearchResponse(val response: GeniusSearchBody? = null)

@Serializable
private data class GeniusSearchBody(val sections: List<GeniusSearchSection> = emptyList())

@Serializable
private data class GeniusSearchSection(val type: String? = null, val hits: List<GeniusSearchHit> = emptyList())

@Serializable
private data class GeniusSearchHit(val type: String? = null, val result: GeniusSearchResult? = null)

@Serializable
private data class GeniusSearchResult(val url: String? = null)

/**
 * Last-resort, unofficial genius.com page scrape - there is no free official lyrics-text API.
 * Gated behind [LyricsFeatureFlags.GENIUS_ENABLED] (off by default): personal use only, see
 * CLAUDE.md. Never write results back into the user's files; only [LyricsRepository]'s Room
 * cache stores what this returns.
 */
class GeniusLyricsProvider @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json
) : LyricsProvider {

    override val source: String = LyricsCacheEntity.SOURCE_GENIUS

    override suspend fun fetch(artist: String, title: String, album: String?, durationMs: Long): Lyrics? =
        withContext(Dispatchers.IO) {
            if (!LyricsFeatureFlags.GENIUS_ENABLED) return@withContext null
            runCatching {
                val songUrl = findSongUrl("$artist $title") ?: return@withContext null
                scrapeLyrics(songUrl)
            }.getOrNull()
        }

    private fun findSongUrl(query: String): String? {
        val url = "https://genius.com/api/search/multi".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .build()
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body.string()
            val parsed = json.decodeFromString<GeniusSearchResponse>(body)
            val hits = parsed.response?.sections.orEmpty().flatMap { it.hits }
            return hits.firstOrNull { it.type == "song" }?.result?.url
        }
    }

    private fun scrapeLyrics(songUrl: String): Lyrics.Plain? {
        val document = Jsoup.connect(songUrl).userAgent(USER_AGENT).timeout(15_000).get()
        val containers = document.select("div[data-lyrics-container=true]")
        if (containers.isEmpty()) return null
        val text = containers.joinToString("\n\n") { renderLyricsContainer(it) }.trim()
        return text.takeUnless { it.isBlank() }?.let { Lyrics.Plain(it) }
    }

    /** Genius renders line breaks as `<br>` inside the container; a plain `.text()` would lose them. */
    private fun renderLyricsContainer(container: Element): String {
        val builder = StringBuilder()
        fun walk(node: Node) {
            when (node) {
                is TextNode -> builder.append(node.text())
                is Element -> when (node.tagName()) {
                    "br" -> builder.append('\n')
                    else -> node.childNodes().forEach(::walk)
                }
                else -> Unit
            }
        }
        container.childNodes().forEach(::walk)
        return builder.toString()
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
    }
}
