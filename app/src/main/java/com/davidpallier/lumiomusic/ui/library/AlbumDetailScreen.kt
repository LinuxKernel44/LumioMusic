package com.davidpallier.lumiomusic.ui.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.davidpallier.lumiomusic.R
import com.davidpallier.lumiomusic.data.db.TrackEntity
import com.davidpallier.lumiomusic.ui.common.AddToPlaylistDialog
import com.davidpallier.lumiomusic.ui.common.TrackListItem
import com.davidpallier.lumiomusic.ui.common.withFabClearance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(onNavigateBack: () -> Unit, viewModel: AlbumDetailViewModel = hiltViewModel()) {
    val tracks by viewModel.tracks.collectAsState()
    var pendingAddToPlaylistTrack by remember { mutableStateOf<TrackEntity?>(null) }

    pendingAddToPlaylistTrack?.let { track ->
        val playlists by viewModel.playlists.collectAsState()
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { pendingAddToPlaylistTrack = null },
            onSelectPlaylist = { playlistId -> viewModel.addTrackToPlaylist(track, playlistId) },
            onCreatePlaylist = { name -> viewModel.createPlaylistWithTrack(name, track) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(viewModel.album, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.mini_player_play)) },
                icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                onClick = viewModel::playAll
            )
        }
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = withFabClearance(innerPadding)) {
            items(tracks, key = { it.id }) { track ->
                TrackListItem(
                    track = track,
                    onClick = { viewModel.play(track) },
                    onAddToPlaylist = { pendingAddToPlaylistTrack = track }
                )
            }
        }
    }
}
