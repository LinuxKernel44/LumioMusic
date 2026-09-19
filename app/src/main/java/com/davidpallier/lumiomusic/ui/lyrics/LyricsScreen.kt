package com.davidpallier.lumiomusic.ui.lyrics

import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.davidpallier.lumiomusic.R
import com.davidpallier.lumiomusic.data.tags.SyncedLyricsLine
import com.davidpallier.lumiomusic.playback.PlaybackUiState

@Composable
fun LyricsScreen(onNavigateBack: () -> Unit, viewModel: LyricsViewModel = hiltViewModel()) {
    val playbackState by viewModel.playbackState.collectAsState()
    val lyricsState by viewModel.lyrics.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        BlurredArtworkBackground(artworkUri = playbackState.artworkUri?.toString())

        Column(modifier = Modifier.fillMaxSize()) {
            LyricsTopBar(onNavigateBack = onNavigateBack)

            Box(modifier = Modifier.weight(1f)) {
                when (val state = lyricsState) {
                    LyricsUiState.Loading -> CenteredMessage(stringResource(R.string.lyrics_loading))
                    LyricsUiState.NotFound -> CenteredMessage(stringResource(R.string.lyrics_not_found))
                    is LyricsUiState.Plain -> PlainLyricsView(state.text)
                    is LyricsUiState.Synced -> SyncedLyricsView(state.lines, playbackState.positionMs)
                }
            }

            NowPlayingHeader(playbackState)
            PlaybackControls(playbackState = playbackState, viewModel = viewModel)
        }
    }
}

@Composable
private fun BlurredArtworkBackground(artworkUri: String?) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (artworkUri != null) {
            AsyncImage(
                model = artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        renderEffect = RenderEffect
                            .createBlurEffect(90f, 90f, Shader.TileMode.CLAMP)
                            .asComposeRenderEffect()
                    }
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)))
    }
}

@Composable
private fun LyricsTopBar(onNavigateBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = stringResource(R.string.lyrics_collapse),
                tint = Color.White
            )
        }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PlainLyricsView(text: String) {
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp)) {
        item {
            Text(
                text = text,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 28.sp
            )
        }
    }
}

@Composable
private fun SyncedLyricsView(lines: List<SyncedLyricsLine>, positionMs: Long) {
    val currentIndex = remember(lines, positionMs) {
        lines.indexOfLast { it.timestampMs <= positionMs }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val halfViewport = maxHeight / 2

        LaunchedEffect(currentIndex) {
            listState.animateScrollToItem(index = currentIndex, scrollOffset = -halfViewport.value.toInt() * 3 + 90)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = halfViewport)
        ) {
            itemsIndexed(lines) { index, line ->
                val isCurrent = index == currentIndex
                Text(
                    text = line.text,
                    color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.4f),
                    fontSize = if (isCurrent) 24.sp else 19.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun NowPlayingHeader(state: PlaybackUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = state.artworkUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.15f))
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                state.title ?: "",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )
            Text(
                state.artist ?: "",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PlaybackControls(playbackState: PlaybackUiState, viewModel: LyricsViewModel) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        val duration = playbackState.durationMs.coerceAtLeast(1L)
        val sliderValue = if (isDragging) dragPositionMs else playbackState.positionMs.toFloat()

        Slider(
            value = sliderValue.coerceIn(0f, duration.toFloat()),
            valueRange = 0f..duration.toFloat(),
            onValueChange = {
                isDragging = true
                dragPositionMs = it
            },
            onValueChangeFinished = {
                viewModel.seekTo(dragPositionMs.toLong())
                isDragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = viewModel::seekToPrevious) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
            IconButton(onClick = viewModel::togglePlayPause, modifier = Modifier.padding(horizontal = 24.dp)) {
                Icon(
                    if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
            IconButton(onClick = viewModel::seekToNext) {
                Icon(Icons.Filled.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }
    }
}
