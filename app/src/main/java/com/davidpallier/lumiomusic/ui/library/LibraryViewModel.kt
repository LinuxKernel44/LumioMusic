package com.davidpallier.lumiomusic.ui.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.davidpallier.lumiomusic.data.db.TrackDao
import com.davidpallier.lumiomusic.data.db.TrackEntity
import com.davidpallier.lumiomusic.data.library.ScanWorker
import com.davidpallier.lumiomusic.data.playlist.PlaylistRepository
import com.davidpallier.lumiomusic.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val trackDao: TrackDao,
    private val playbackController: PlaybackController,
    private val playlistRepository: PlaylistRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val trackCount = trackDao.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val tracks = trackDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val albums = trackDao.observeAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val artists = trackDao.observeArtists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists = playlistRepository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList()) else trackDao.searchTracks(query.trim())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun rescan() {
        val request = OneTimeWorkRequestBuilder<ScanWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ScanWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun play(track: TrackEntity, queue: List<TrackEntity>) {
        val startIndex = queue.indexOf(track).coerceAtLeast(0)
        playbackController.playQueue(queue.map { it.id.toString() }, startIndex)
    }

    fun addTrackToPlaylist(track: TrackEntity, playlistId: Long) {
        viewModelScope.launch { playlistRepository.addTrack(playlistId, track.id) }
    }

    fun createPlaylistWithTrack(name: String, track: TrackEntity) {
        viewModelScope.launch { playlistRepository.createPlaylistWithTrack(name, track.id) }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch { playlistRepository.createPlaylist(name) }
    }
}
