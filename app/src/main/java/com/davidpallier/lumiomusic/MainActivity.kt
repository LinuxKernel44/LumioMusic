package com.davidpallier.lumiomusic

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.davidpallier.lumiomusic.audionative.NativeLibraryLoader
import com.davidpallier.lumiomusic.ui.theme.LumioMusicTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LumioMusicTheme {
                Scaffold { innerPadding ->
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        PlaceholderHome()
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderHome() {
    val nativeStatus = remember {
        runCatching { NativeLibraryLoader.ping() }.getOrElse { "native FAILED: ${it.message}" }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("LumioMusic — scaffold Phase 0 OK\nnative: $nativeStatus")
    }
}
