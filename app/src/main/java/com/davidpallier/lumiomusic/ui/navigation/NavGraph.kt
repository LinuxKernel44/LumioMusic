package com.davidpallier.lumiomusic.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.davidpallier.lumiomusic.ui.library.AlbumDetailScreen
import com.davidpallier.lumiomusic.ui.library.ArtistDetailScreen
import com.davidpallier.lumiomusic.ui.library.LibraryHostScreen
import com.davidpallier.lumiomusic.ui.library.PlaylistDetailScreen
import com.davidpallier.lumiomusic.ui.lyrics.LyricsScreen
import com.davidpallier.lumiomusic.ui.nowplaying.MiniPlayerBar
import com.davidpallier.lumiomusic.ui.setup.FolderPickerScreen
import com.davidpallier.lumiomusic.ui.setup.ScanProgressScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument

private object Routes {
    const val ROOT = "root"
    const val PICKER = "picker"
    const val SCANNING = "scanning"
    const val LIBRARY = "library"
    const val ALBUM_DETAIL = "albumDetail/{album}"
    const val ARTIST_DETAIL = "artistDetail/{artist}"
    const val PLAYLIST_DETAIL = "playlistDetail/{playlistId}"
    const val NOW_PLAYING = "nowPlaying"

    fun albumDetail(album: String) = "albumDetail/${Uri.encode(album)}"
    fun artistDetail(artist: String) = "artistDetail/${Uri.encode(artist)}"
    fun playlistDetail(playlistId: Long) = "playlistDetail/$playlistId"
}

@Composable
fun LumioNavHost(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showMiniPlayer = currentRoute != null &&
        currentRoute != Routes.ROOT &&
        currentRoute != Routes.PICKER &&
        currentRoute != Routes.SCANNING &&
        currentRoute != Routes.NOW_PLAYING

    Scaffold(
        bottomBar = {
            if (showMiniPlayer) {
                MiniPlayerBar(onClick = { navController.navigate(Routes.NOW_PLAYING) })
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.ROOT,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Routes.ROOT) {
                RootRedirect(navController)
            }
            composable(Routes.PICKER) {
                FolderPickerScreen(onFolderPicked = {
                    navController.navigate(Routes.SCANNING) {
                        popUpTo(Routes.PICKER) { inclusive = true }
                    }
                })
            }
            composable(Routes.SCANNING) {
                ScanProgressScreen(onScanFinished = {
                    navController.navigate(Routes.LIBRARY) {
                        popUpTo(Routes.SCANNING) { inclusive = true }
                    }
                })
            }
            composable(Routes.LIBRARY) {
                LibraryHostScreen(
                    onOpenAlbum = { album -> navController.navigate(Routes.albumDetail(album)) },
                    onOpenArtist = { artist -> navController.navigate(Routes.artistDetail(artist)) },
                    onOpenPlaylist = { playlistId -> navController.navigate(Routes.playlistDetail(playlistId)) }
                )
            }
            composable(Routes.ALBUM_DETAIL) {
                AlbumDetailScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Routes.ARTIST_DETAIL) {
                ArtistDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onOpenAlbum = { album -> navController.navigate(Routes.albumDetail(album)) }
                )
            }
            composable(
                Routes.PLAYLIST_DETAIL,
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
            ) {
                PlaylistDetailScreen(onNavigateBack = { navController.popBackStack() })
            }
            composable(Routes.NOW_PLAYING) {
                LyricsScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun RootRedirect(navController: NavHostController, viewModel: RootViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state) {
        when (state) {
            is RootState.NoRoot -> navController.navigate(Routes.PICKER) {
                popUpTo(Routes.ROOT) { inclusive = true }
            }
            is RootState.HasRoot -> navController.navigate(Routes.LIBRARY) {
                popUpTo(Routes.ROOT) { inclusive = true }
            }
            RootState.Loading -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
