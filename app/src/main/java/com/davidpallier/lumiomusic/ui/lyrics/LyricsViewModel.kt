package com.davidpallier.lumiomusic.ui.lyrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidpallier.lumiomusic.data.db.TrackDao
import com.davidpallier.lumiomusic.data.lyrics.LyricsRepository
import com.davidpallier.lumiomusic.data.model.Lyrics
import com.davidpallier.lumiomusic.data.tags.SyncedLyricsLine
import com.davidpallier.lumiomusic.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LyricsUiState {
    data object Loading : LyricsUiState
    data object NotFound : LyricsUiState
    data class Plain(val text: String) : LyricsUiState
    data class Synced(val lines: List<SyncedLyricsLine>) : LyricsUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LyricsViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val trackDao: TrackDao,
    private val lyricsRepository: LyricsRepository
) : ViewModel() {

    val playbackState = playbackController.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), playbackController.state.value)

    private val _lyrics = MutableStateFlow<LyricsUiState>(LyricsUiState.Loading)
    val lyrics: StateFlow<LyricsUiState> = _lyrics.asStateFlow()

    init {
        viewModelScope.launch {
            playbackController.state.map { it.mediaId }.distinctUntilChanged().collectLatest { mediaId ->
                _lyrics.value = LyricsUiState.Loading
                val track = mediaId?.toLongOrNull()?.let { trackDao.getById(it) }
                if (track == null) {
                    _lyrics.value = LyricsUiState.NotFound
                    return@collectLatest
                }
                _lyrics.value = when (val result = lyricsRepository.getLyrics(track)) {
                    null -> LyricsUiState.NotFound
                    is Lyrics.Plain -> LyricsUiState.Plain(result.text)
                    is Lyrics.Synced -> LyricsUiState.Synced(result.lines)
                }
            }
        }
    }

    fun togglePlayPause() = playbackController.togglePlayPause()
    fun seekToNext() = playbackController.seekToNext()
    fun seekToPrevious() = playbackController.seekToPrevious()
    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)
}
