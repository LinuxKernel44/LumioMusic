@file:Suppress("DEPRECATION") // MetadataRetriever is deprecated in 1.10.1; pinned there because 1.11.1 removed it outright (see project memory).

package com.davidpallier.lumiomusic.data.tags

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.extractor.metadata.flac.PictureFrame
import androidx.media3.extractor.metadata.id3.ApicFrame
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withTimeout

/**
 * Reads title/artist/album/cover/lyrics from an audio file via Media3's MetadataRetriever,
 * which already parses ID3v2 (MP3), Vorbis comments (FLAC/Ogg) and MP4 atoms without any
 * custom byte-level tag parsing. The one gap — Media3's Id3Decoder has no dedicated decoder
 * for USLT/SYLT lyrics frames, exposing them only as a raw BinaryFrame — is filled by
 * [Id3UsltSyltDecoder] using the same already-extracted frame bytes (verified against real
 * fixture files during the Phase 1 spike).
 */
object TagReaderFactory {

    suspend fun read(context: Context, uri: Uri): ParsedTrackMetadata? {
        val mediaItem = MediaItem.fromUri(uri)
        val retriever = MetadataRetriever.Builder(context, mediaItem).build()
        try {
            val (trackGroups, durationUs) = try {
                withTimeout(15_000) {
                    val groups = retriever.retrieveTrackGroups().await()
                    val duration = runCatching { retriever.retrieveDurationUs().await() }.getOrDefault(0L)
                    groups to duration
                }
            } catch (e: Exception) {
                return null
            }

            var title: String? = null
            var artist: String? = null
            var album: String? = null
            var albumArtist: String? = null
            var trackNumber: Int? = null
            var discNumber: Int? = null
            var year: Int? = null
            var lyricsPlain: String? = null
            var syncedLines: List<SyncedLyricsLine>? = null
            var coverArt: ByteArray? = null
            var sampleRateHz: Int? = null
            var bitrateBps: Int? = null
            var channelCount: Int? = null

            for (i in 0 until trackGroups.length) {
                val group = trackGroups.get(i)
                for (j in 0 until group.length) {
                    val format = group.getFormat(j)
                    if (format.sampleRate > 0) sampleRateHz = format.sampleRate
                    if (format.bitrate > 0) bitrateBps = format.bitrate
                    if (format.channelCount > 0) channelCount = format.channelCount

                    val metadata: Metadata = format.metadata ?: continue
                    for (k in 0 until metadata.length()) {
                        when (val entry = metadata.get(k)) {
                            is TextInformationFrame -> {
                                val value = entry.values.firstOrNull()
                                when (entry.id) {
                                    "TIT2" -> title = value
                                    "TPE1" -> artist = value
                                    "TALB" -> album = value
                                    "TPE2" -> albumArtist = value
                                    "TRCK" -> trackNumber = value?.substringBefore('/')?.toIntOrNull()
                                    "TPOS" -> discNumber = value?.substringBefore('/')?.toIntOrNull()
                                    "TYER", "TDRC" -> year = value?.take(4)?.toIntOrNull()
                                }
                            }
                            is ApicFrame -> coverArt = entry.pictureData
                            is BinaryFrame -> when (entry.id) {
                                "USLT" -> Id3UsltSyltDecoder.decodeUslt(entry.data)?.let { lyricsPlain = it }
                                "SYLT" -> Id3UsltSyltDecoder.decodeSylt(entry.data)?.let { syncedLines = it }
                            }
                            is VorbisComment -> when (entry.key.uppercase()) {
                                "TITLE" -> title = entry.value
                                "ARTIST" -> artist = entry.value
                                "ALBUM" -> album = entry.value
                                "ALBUMARTIST", "ALBUM_ARTIST" -> albumArtist = entry.value
                                "TRACKNUMBER" -> trackNumber = entry.value.substringBefore('/').toIntOrNull()
                                "DISCNUMBER" -> discNumber = entry.value.substringBefore('/').toIntOrNull()
                                "DATE", "YEAR" -> year = entry.value.take(4).toIntOrNull()
                                "LYRICS", "UNSYNCEDLYRICS", "LYRICS_SYNCED" -> lyricsPlain = entry.value
                            }
                            is PictureFrame -> coverArt = entry.pictureData
                        }
                    }
                }
            }

            val embeddedLyrics = when {
                syncedLines != null -> EmbeddedLyrics.Synced(syncedLines)
                lyricsPlain != null -> EmbeddedLyrics.Plain(lyricsPlain)
                else -> null
            }

            return ParsedTrackMetadata(
                title = title,
                artist = artist,
                album = album,
                albumArtist = albumArtist,
                trackNumber = trackNumber,
                discNumber = discNumber,
                year = year,
                durationMs = durationUs / 1000,
                sampleRateHz = sampleRateHz,
                bitrateBps = bitrateBps,
                channelCount = channelCount,
                embeddedLyrics = embeddedLyrics,
                coverArt = coverArt
            )
        } finally {
            retriever.close()
        }
    }
}
