package com.davidpallier.lumiomusic.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.davidpallier.lumiomusic.R

/**
 * Phase 1 placeholder: confirms the scanned library landed in Room. Replaced by the real
 * albums/artists/tracks browse UI in Phase 3.
 */
@Composable
fun LibraryPlaceholderScreen(viewModel: LibraryPlaceholderViewModel = hiltViewModel()) {
    val count by viewModel.trackCount.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.library_track_count, count), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.padding(top = 16.dp))
        Button(onClick = viewModel::rescan) {
            Text(stringResource(R.string.library_rescan_button))
        }
    }
}
