package com.davidpallier.lumiomusic.data.library

import android.content.Context
import android.net.Uri
import com.davidpallier.lumiomusic.data.db.TrackDao
import com.davidpallier.lumiomusic.data.db.TrackEntity
import com.davidpallier.lumiomusic.data.tags.EmbeddedLyrics
import com.davidpallier.lumiomusic.data.tags.TagReaderFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

data class ScanResult(val total: Int, val added: Int, val updated: Int)

class LibraryScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackDao: TrackDao
) {
    suspend fun scan(
        rootTreeUri: Uri,
        onProgress: suspend (scanned: Int, total: Int) -> Unit = { _, _ -> }
    ): ScanResult = withContext(Dispatchers.IO) {
        val files = SafFileWalker.walk(context, rootTreeUri)
        val seenUris = ArrayList<String>(files.size)
        var added = 0
        var updated = 0

        files.forEachIndexed { index, file ->
            val uriString = file.uri.toString()
            seenUris += uriString

            val existing = trackDao.getByUri(uriString)
            val unchanged = existing != null &&
                existing.lastModified == file.lastModified &&
                existing.sizeBytes == file.size
            if (!unchanged) {
                val parsed = TagReaderFactory.read(context, file.uri)
                val coverPath = parsed?.coverArt?.let { saveCoverArt(uriString, it) } ?: existing?.coverArtPath

                val syncedJson = (parsed?.embeddedLyrics as? EmbeddedLyrics.Synced)
                    ?.let { runCatching { Json.encodeToString(it.lines) }.getOrNull() }
                val plainLyrics = (parsed?.embeddedLyrics as? EmbeddedLyrics.Plain)?.text

                val entity = TrackEntity(
                    id = existing?.id ?: 0,
                    uriString = uriString,
                    documentId = file.documentId,
                    displayName = file.displayName,
                    title = parsed?.title?.takeUnless { it.isBlank() }
                        ?: file.displayName.substringBeforeLast('.'),
                    artist = parsed?.artist?.takeUnless { it.isBlank() } ?: UNKNOWN_ARTIST,
                    album = parsed?.album?.takeUnless { it.isBlank() } ?: UNKNOWN_ALBUM,
                    albumArtist = parsed?.albumArtist,
                    trackNumber = parsed?.trackNumber,
                    discNumber = parsed?.discNumber,
                    year = parsed?.year,
                    durationMs = parsed?.durationMs ?: 0L,
                    mimeType = file.mimeType,
                    sizeBytes = file.size,
                    lastModified = file.lastModified,
                    sampleRateHz = parsed?.sampleRateHz,
                    bitrateBps = parsed?.bitrateBps,
                    channelCount = parsed?.channelCount,
                    coverArtPath = coverPath,
                    embeddedLyricsPlain = plainLyrics,
                    embeddedLyricsSyncedJson = syncedJson
                )

                if (existing != null) {
                    trackDao.update(entity)
                    updated++
                } else {
                    trackDao.insert(entity)
                    added++
                }
            }
            onProgress(index + 1, files.size)
        }

        trackDao.deleteMissing(seenUris)
        ScanResult(total = files.size, added = added, updated = updated)
    }

    private fun saveCoverArt(uriString: String, bytes: ByteArray): String {
        val dir = File(context.cacheDir, "art").apply { mkdirs() }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(uriString.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val file = File(dir, "$hash.jpg")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    private companion object {
        const val UNKNOWN_ARTIST = "Unknown Artist"
        const val UNKNOWN_ALBUM = "Unknown Album"
    }
}
