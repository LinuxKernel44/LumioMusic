package com.davidpallier.lumiomusic.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.LibraryResult
import com.davidpallier.lumiomusic.MainActivity
import com.davidpallier.lumiomusic.R
import com.davidpallier.lumiomusic.data.db.TrackDao
import com.davidpallier.lumiomusic.data.playlist.QueueStateStore
import com.davidpallier.lumiomusic.playback.MediaItemMapper.albumBrowseId
import com.davidpallier.lumiomusic.playback.MediaItemMapper.artistBrowseId
import com.davidpallier.lumiomusic.playback.MediaItemMapper.toBrowsableFolder
import com.davidpallier.lumiomusic.playback.MediaItemMapper.toBrowsableMediaItem
import com.davidpallier.lumiomusic.playback.MediaItemMapper.toMediaItem
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The single playback entry point: hosts the process-lifetime [ExoPlayer] (injected, see
 * [com.davidpallier.lumiomusic.di.PlaybackModule]) behind a [MediaSession], exposes an
 * Albums/Artists/All-tracks browse tree for [MediaSession.ControllerInfo]s (including an
 * Android Auto head unit - see `automotive_app_desc.xml`), and resolves mediaId-only
 * [MediaItem]s added to the player back to a playable item carrying the track's SAF content URI
 * (see [resolveMediaItem]) - controllers only ever need to know a track's Room ID.
 */
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject lateinit var player: ExoPlayer
    @Inject lateinit var trackDao: TrackDao
    @Inject lateinit var queueStateStore: QueueStateStore

    private lateinit var mediaSession: MediaLibrarySession
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        val sessionActivityIntent = Intent(this, MainActivity::class.java)
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this).build()
        notificationProvider.setSmallIcon(R.drawable.ic_notification)
        setMediaNotificationProvider(notificationProvider)

        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_IS_PLAYING_CHANGED
                    )
                ) {
                    persistQueueState()
                }
            }
        })
    }

    private fun persistQueueState() {
        val trackIds = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).mediaId.toLongOrNull() }
        if (trackIds.isEmpty()) return
        val currentIndex = player.currentMediaItemIndex
        val positionMs = player.currentPosition
        serviceScope.launch { queueStateStore.save(trackIds, currentIndex, positionMs) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession =
        mediaSession

    override fun onDestroy() {
        mediaSession.release()
        player.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // No queued items left to resume into: let the service (and its foreground
        // notification) go away when the user swipes the app away, matching most music players.
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    private suspend fun resolveMediaItem(requested: MediaItem): MediaItem {
        val id = requested.mediaId.toLongOrNull() ?: return requested
        val track = trackDao.getById(id) ?: return requested
        return track.toMediaItem()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootMetadata = MediaMetadata.Builder()
                .setTitle("LumioMusic")
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                .build()
            val rootItem = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(rootMetadata)
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceScope.future {
            val items: List<MediaItem>? = when {
                parentId == ROOT_ID -> listOf(albumsFolderItem(), artistsFolderItem(), tracksFolderItem())
                parentId == ALBUMS_ID -> trackDao.getAlbumsOnce().map { it.toBrowsableFolder() }
                parentId == ARTISTS_ID -> trackDao.getArtistsOnce().map { it.toBrowsableFolder() }
                parentId == TRACKS_ID -> trackDao.getAllOnce().map { it.toBrowsableMediaItem() }
                parentId.startsWith(MediaItemMapper.ALBUM_PREFIX) ->
                    trackDao.getTracksForAlbumOnce(parentId.removePrefix(MediaItemMapper.ALBUM_PREFIX)).map { it.toBrowsableMediaItem() }
                parentId.startsWith(MediaItemMapper.ARTIST_PREFIX) ->
                    trackDao.getAlbumsForArtistOnce(parentId.removePrefix(MediaItemMapper.ARTIST_PREFIX)).map { it.toBrowsableFolder() }
                else -> null
            }
            if (items == null) {
                LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            } else {
                LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> = serviceScope.future {
            val id = mediaId.toLongOrNull()
            val track = id?.let { trackDao.getById(it) }
            if (track == null) {
                LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
            } else {
                LibraryResult.ofItem(track.toMediaItem(), null)
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> = serviceScope.future {
            mediaItems.map { resolveMediaItem(it) }.toMutableList()
        }

        private fun albumsFolderItem(): MediaItem = MediaItem.Builder()
            .setMediaId(ALBUMS_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(getString(R.string.browse_albums))
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS)
                    .build()
            )
            .build()

        private fun artistsFolderItem(): MediaItem = MediaItem.Builder()
            .setMediaId(ARTISTS_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(getString(R.string.browse_artists))
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS)
                    .build()
            )
            .build()

        private fun tracksFolderItem(): MediaItem = MediaItem.Builder()
            .setMediaId(TRACKS_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(getString(R.string.browse_all_tracks))
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build()
            )
            .build()

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = serviceScope.future {
            val persisted = queueStateStore.load()
            val items = persisted?.trackIds.orEmpty().mapNotNull { trackDao.getById(it)?.toMediaItem() }
            if (items.isEmpty()) {
                MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
            } else {
                val startIndex = persisted!!.currentIndex.coerceIn(0, items.size - 1)
                MediaSession.MediaItemsWithStartPosition(items, startIndex, persisted.positionMs)
            }
        }
    }

    private companion object {
        const val ROOT_ID = "root"
        const val ALBUMS_ID = "albums"
        const val ARTISTS_ID = "artists"
        const val TRACKS_ID = "tracks"
    }
}
