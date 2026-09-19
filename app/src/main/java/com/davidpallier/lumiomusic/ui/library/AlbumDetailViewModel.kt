package com.davidpallier.lumiomusic.ui.library

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidpallier.lumiomusic.data.db.TrackDao
import com.davidpallier.lumiomusic.data.db.TrackEntity
import com.davidpallier.lumiomusic.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    trackDao: TrackDao,
    private val playbackController: PlaybackController
) : ViewModel() {

    val album: String = Uri.decode(checkNotNull(savedStateHandle["album"]))

    val tracks = trackDao.observeTracksForAlbum(album)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun play(track: TrackEntity) {
        val queue = tracks.value
        val startIndex = queue.indexOf(track).coerceAtLeast(0)
        playbackController.playQueue(queue.map { it.id.toString() }, startIndex)
    }

    fun playAll() {
        val queue = tracks.value
        if (queue.isEmpty()) return
        playbackController.playQueue(queue.map { it.id.toString() }, 0)
    }
}
