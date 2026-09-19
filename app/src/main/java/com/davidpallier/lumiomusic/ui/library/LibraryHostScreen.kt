package com.davidpallier.lumiomusic.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.davidpallier.lumiomusic.R
import com.davidpallier.lumiomusic.data.db.TrackEntity
import com.davidpallier.lumiomusic.ui.common.AddToPlaylistDialog
import com.davidpallier.lumiomusic.ui.common.AlbumGridItem
import com.davidpallier.lumiomusic.ui.common.TrackListItem

private enum class LibraryTab { TRACKS, ALBUMS, ARTISTS, PLAYLISTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryHostScreen(
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    var selectedTab by rememberSaveable { mutableStateOf(LibraryTab.TRACKS) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var pendingAddToPlaylistTrack by remember { mutableStateOf<TrackEntity?>(null) }
    val searchQuery by viewModel.searchQuery.collectAsState()

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
            if (searchActive) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = viewModel::setSearchQuery,
                            placeholder = { Text(stringResource(R.string.library_search_hint)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            searchActive = false
                            viewModel.setSearchQuery("")
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.library_search_close_action))
                        }
                    }
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.library_search_action))
                        }
                        IconButton(onClick = viewModel::rescan) {
                            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.library_rescan_action))
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (!searchActive) {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == LibraryTab.TRACKS,
                        onClick = { selectedTab = LibraryTab.TRACKS },
                        icon = { Icon(Icons.Filled.MusicNote, contentDescription = null) },
                        label = { Text(stringResource(R.string.library_tab_tracks)) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == LibraryTab.ALBUMS,
                        onClick = { selectedTab = LibraryTab.ALBUMS },
                        icon = { Icon(Icons.Filled.Album, contentDescription = null) },
                        label = { Text(stringResource(R.string.library_tab_albums)) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == LibraryTab.ARTISTS,
                        onClick = { selectedTab = LibraryTab.ARTISTS },
                        icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        label = { Text(stringResource(R.string.library_tab_artists)) }
                    )
                    NavigationBarItem(
                        selected = selectedTab == LibraryTab.PLAYLISTS,
                        onClick = { selectedTab = LibraryTab.PLAYLISTS },
                        icon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null) },
                        label = { Text(stringResource(R.string.library_tab_playlists)) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (!searchActive && selectedTab == LibraryTab.PLAYLISTS) {
                var showCreateDialog by remember { mutableStateOf(false) }
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.new_playlist_action))
                }
                if (showCreateDialog) {
                    NewPlaylistDialog(
                        onDismiss = { showCreateDialog = false },
                        onCreate = { name ->
                            viewModel.createPlaylist(name)
                            showCreateDialog = false
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (searchActive) {
                val results by viewModel.searchResults.collectAsState()
                TrackList(
                    tracks = results,
                    onTrackClick = { viewModel.play(it, results) },
                    onAddToPlaylist = { pendingAddToPlaylistTrack = it },
                    emptyMessage = stringResource(
                        if (searchQuery.isBlank()) R.string.library_search_hint else R.string.search_empty
                    )
                )
            } else {
                when (selectedTab) {
                    LibraryTab.TRACKS -> {
                        val tracks by viewModel.tracks.collectAsState()
                        TrackList(
                            tracks = tracks,
                            onTrackClick = { viewModel.play(it, tracks) },
                            onAddToPlaylist = { pendingAddToPlaylistTrack = it }
                        )
                    }
                    LibraryTab.ALBUMS -> {
                        val albums by viewModel.albums.collectAsState()
                        if (albums.isEmpty()) {
                            EmptyState(stringResource(R.string.albums_empty))
                        } else {
                            LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize()) {
                                items(albums, key = { it.album }) { album ->
                                    AlbumGridItem(album = album, onClick = { onOpenAlbum(album.album) })
                                }
                            }
                        }
                    }
                    LibraryTab.ARTISTS -> {
                        val artists by viewModel.artists.collectAsState()
                        if (artists.isEmpty()) {
                            EmptyState(stringResource(R.string.artists_empty))
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(artists, key = { it.artist }) { artist ->
                                    ListItem(
                                        headlineContent = { Text(artist.artist) },
                                        supportingContent = {
                                            Text(
                                                stringResource(
                                                    R.string.artist_album_count,
                                                    artist.albumCount,
                                                    artist.trackCount
                                                )
                                            )
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenArtist(artist.artist) }
                                    )
                                }
                            }
                        }
                    }
                    LibraryTab.PLAYLISTS -> {
                        val playlists by viewModel.playlists.collectAsState()
                        if (playlists.isEmpty()) {
                            EmptyState(stringResource(R.string.playlists_empty))
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(playlists, key = { it.id }) { playlist ->
                                    ListItem(
                                        headlineContent = { Text(playlist.name) },
                                        supportingContent = {
                                            Text(stringResource(R.string.album_track_count, playlist.trackCount))
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onOpenPlaylist(playlist.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<TrackEntity>,
    onTrackClick: (TrackEntity) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit,
    emptyMessage: String = stringResource(R.string.library_empty)
) {
    if (tracks.isEmpty()) {
        EmptyState(emptyMessage)
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(tracks, key = { it.id }) { track ->
            TrackListItem(
                track = track,
                onClick = { onTrackClick(track) },
                onAddToPlaylist = { onAddToPlaylist(track) }
            )
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NewPlaylistDialog(onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_playlist_action)) },
        text = {
            TextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(stringResource(R.string.add_to_playlist_new_name)) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim()) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.add_to_playlist_create))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}
