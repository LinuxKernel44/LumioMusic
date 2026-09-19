package com.davidpallier.lumiomusic.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackUiState(
    val hasMedia: Boolean = false,
    val mediaId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val artworkUri: Uri? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

/**
 * App-wide connection to [PlaybackService] via a [MediaController], mirroring player state into
 * a [StateFlow] so Compose screens (mini-player, and the future now-playing/lyrics screen) never
 * touch the controller or [androidx.media3.exoplayer.ExoPlayer] directly.
 */
@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            syncState()
        }
    }

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener(
            {
                controller = future.get().also { it.addListener(listener) }
                syncState()
            },
            MoreExecutors.directExecutor()
        )

        // Player.Listener only fires on discontinuities/state changes, not continuously while
        // playing, so poll position on a short tick to keep a progress bar smooth.
        scope.launch {
            while (isActive) {
                delay(500)
                if (controller?.isPlaying == true) syncState()
            }
        }
    }

    fun playQueue(mediaIds: List<String>, startIndex: Int) {
        val items = mediaIds.map { MediaItem.Builder().setMediaId(it).build() }
        controller?.apply {
            setMediaItems(items, startIndex, 0L)
            prepare()
            play()
        }
    }

    fun togglePlayPause() {
        controller?.apply { if (isPlaying) pause() else play() }
    }

    fun seekToNext() {
        controller?.seekToNextMediaItem()
    }

    fun seekToPrevious() {
        controller?.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        syncState()
    }

    private fun syncState() {
        val c = controller ?: return
        val metadata = c.currentMediaItem?.mediaMetadata
        _state.value = PlaybackUiState(
            hasMedia = c.mediaItemCount > 0,
            mediaId = c.currentMediaItem?.mediaId,
            title = metadata?.title?.toString(),
            artist = metadata?.artist?.toString(),
            artworkUri = metadata?.artworkUri,
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.coerceAtLeast(0)
        )
    }
}
