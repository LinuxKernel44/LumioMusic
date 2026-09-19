package com.davidpallier.lumiomusic.ui.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidpallier.lumiomusic.data.db.TrackEntity
import com.davidpallier.lumiomusic.data.playlist.PlaylistRepository
import com.davidpallier.lumiomusic.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
    private val playbackController: PlaybackController
) : ViewModel() {

    val playlistId: Long = checkNotNull(savedStateHandle["playlistId"])

    private val _playlistName = MutableStateFlow("")
    val playlistName = _playlistName.asStateFlow()

    val tracks = playlistRepository.tracksFor(playlistId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            _playlistName.value = playlistRepository.getPlaylist(playlistId)?.name.orEmpty()
        }
    }

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

    fun removeTrack(track: TrackEntity) {
        viewModelScope.launch { playlistRepository.removeTrack(playlistId, track.id) }
    }
}
