package com.davidpallier.lumiomusic.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.davidpallier.lumiomusic.R
import com.davidpallier.lumiomusic.ui.common.TrackListItem
import com.davidpallier.lumiomusic.ui.common.withFabClearance

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(onNavigateBack: () -> Unit, viewModel: PlaylistDetailViewModel = hiltViewModel()) {
    val name by viewModel.playlistName.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    var menuOpen by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.rename_playlist_action)) },
            text = { TextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = { viewModel.rename(newName); showRenameDialog = false }
                ) { Text(stringResource(R.string.rename_playlist_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_playlist_action)) },
            text = { Text(stringResource(R.string.delete_playlist_confirm_message, name)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete(onDeleted = onNavigateBack)
                }) { Text(stringResource(R.string.delete_playlist_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.navigate_back))
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.playlist_more_options))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.rename_playlist_action)) },
                                onClick = { menuOpen = false; showRenameDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_playlist_action)) },
                                onClick = { menuOpen = false; showDeleteDialog = true }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (tracks.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.mini_player_play)) },
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    onClick = viewModel::playAll
                )
            }
        }
    ) { innerPadding ->
        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.playlist_empty), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = withFabClearance(innerPadding)) {
                items(tracks, key = { it.id }) { track ->
                    TrackListItem(
                        track = track,
                        onClick = { viewModel.play(track) },
                        onRemoveFromPlaylist = { viewModel.removeTrack(track) }
                    )
                }
            }
        }
    }
}
