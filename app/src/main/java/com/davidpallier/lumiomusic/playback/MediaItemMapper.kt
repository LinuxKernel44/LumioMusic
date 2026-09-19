package com.davidpallier.lumiomusic.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.davidpallier.lumiomusic.data.db.TrackEntity
import com.davidpallier.lumiomusic.data.model.AlbumSummary
import com.davidpallier.lumiomusic.data.model.ArtistSummary
import java.io.File

/**
 * Converts between Room [TrackEntity] rows and Media3 [MediaItem]s.
 *
 * The track's Room [TrackEntity.id] is used as the stable Media3 media ID, so a controller can
 * request playback with only that ID set (see [MediaItem.Builder.setMediaId] callers) and
 * [PlaybackService] resolves it back to a full, playable [MediaItem] (with its SAF content URI)
 * via [PlaybackService.resolveMediaItem].
 */
object MediaItemMapper {

    fun TrackEntity.toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setAlbumArtist(albumArtist)
            .setTrackNumber(trackNumber)
            .setDiscNumber(discNumber)
            .setRecordingYear(year)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .apply {
                coverArtPath?.let { setArtworkUri(Uri.fromFile(File(it))) }
            }
            .build()

        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uriString)
            .setMimeType(mimeType)
            .setMediaMetadata(metadata)
            .build()
    }

    /** A lightweight, browse-only reference: mediaId set, no local configuration (URI) yet. */
    fun TrackEntity.toBrowsableMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .apply {
                coverArtPath?.let { setArtworkUri(Uri.fromFile(File(it))) }
            }
            .build()

        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setMediaMetadata(metadata)
            .build()
    }

    /** Browsable "album" folder node, used both under the Albums root and under an artist. */
    fun AlbumSummary.toBrowsableFolder(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(album)
            .setArtist(albumArtist)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
            .apply {
                coverArtPath?.let { setArtworkUri(Uri.fromFile(File(it))) }
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(albumBrowseId(album))
            .setMediaMetadata(metadata)
            .build()
    }

    /** Browsable "artist" folder node, listed under the Artists root. */
    fun ArtistSummary.toBrowsableFolder(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(artist)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
            .build()
        return MediaItem.Builder()
            .setMediaId(artistBrowseId(artist))
            .setMediaMetadata(metadata)
            .build()
    }

    const val ALBUM_PREFIX = "album:"
    const val ARTIST_PREFIX = "artist:"

    fun albumBrowseId(album: String) = "$ALBUM_PREFIX$album"
    fun artistBrowseId(artist: String) = "$ARTIST_PREFIX$artist"
}
