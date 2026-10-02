package com.davidpallier.lumiomusic.ui.library

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidpallier.lumiomusic.data.db.TrackDao
import com.davidpallier.lumiomusic.data.db.TrackEntity
import com.davidpallier.lumiomusic.data.playlist.PlaylistRepository
import com.davidpallier.lumiomusic.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    trackDao: TrackDao,
    private val playbackController: PlaybackController,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    val album: String = Uri.decode(checkNotNull(savedStateHandle["album"]))

    val tracks = trackDao.observeTracksForAlbum(album)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists = playlistRepository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addTrackToPlaylist(track: TrackEntity, playlistId: Long) {
        viewModelScope.launch { playlistRepository.addTrack(playlistId, track.id) }
    }

    fun createPlaylistWithTrack(name: String, track: TrackEntity) {
        viewModelScope.launch { playlistRepository.createPlaylistWithTrack(name, track.id) }
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
}
