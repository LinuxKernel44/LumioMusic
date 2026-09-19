package com.davidpallier.lumiomusic.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.navigation.compose.rememberNavController
import com.davidpallier.lumiomusic.ui.library.LibraryPlaceholderScreen
import com.davidpallier.lumiomusic.ui.setup.FolderPickerScreen
import com.davidpallier.lumiomusic.ui.setup.ScanProgressScreen

private object Routes {
    const val ROOT = "root"
    const val PICKER = "picker"
    const val SCANNING = "scanning"
    const val LIBRARY = "library"
}

@Composable
fun LumioNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.ROOT) {
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
            LibraryPlaceholderScreen()
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
