package com.davidpallier.lumiomusic.ui.nowplaying

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidpallier.lumiomusic.playback.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class PlaybackViewModel @Inject constructor(
    private val playbackController: PlaybackController
) : ViewModel() {

    val state = playbackController.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), playbackController.state.value)

    fun togglePlayPause() = playbackController.togglePlayPause()
    fun seekToNext() = playbackController.seekToNext()
    fun seekToPrevious() = playbackController.seekToPrevious()
    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)
}
