package com.davidpallier.lumiomusic.ui.library

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidpallier.lumiomusic.data.db.TrackDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    trackDao: TrackDao
) : ViewModel() {

    val artist: String = Uri.decode(checkNotNull(savedStateHandle["artist"]))

    val albums = trackDao.observeAlbumsForArtist(artist)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
