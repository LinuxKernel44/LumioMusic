package com.davidpallier.lumiomusic.ui.navigation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidpallier.lumiomusic.data.library.UriPermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface RootState {
    data object Loading : RootState
    data object NoRoot : RootState
    data class HasRoot(val uri: Uri) : RootState
}

@HiltViewModel
class RootViewModel @Inject constructor(
    uriPermissionManager: UriPermissionManager
) : ViewModel() {
    val state = uriPermissionManager.rootUri
        .map { uri -> if (uri == null) RootState.NoRoot else RootState.HasRoot(uri) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RootState.Loading)
}
