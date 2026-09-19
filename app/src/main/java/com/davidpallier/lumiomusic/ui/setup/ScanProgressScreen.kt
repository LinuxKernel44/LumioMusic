package com.davidpallier.lumiomusic.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.davidpallier.lumiomusic.data.library.ScanWorker

@Composable
fun ScanProgressScreen(onScanFinished: () -> Unit) {
    val context = LocalContext.current
    val workManager = remember(context) { WorkManager.getInstance(context) }
    val workInfos by workManager
        .getWorkInfosForUniqueWorkFlow(ScanWorker.UNIQUE_WORK_NAME)
        .collectAsState(initial = emptyList())

    val current = workInfos.firstOrNull()
    val scanned = current?.progress?.getInt(ScanWorker.KEY_SCANNED, 0) ?: 0
    val total = current?.progress?.getInt(ScanWorker.KEY_TOTAL, 0) ?: 0

    LaunchedEffect(current?.state) {
        if (current?.state == WorkInfo.State.SUCCEEDED || current?.state == WorkInfo.State.FAILED) {
            onScanFinished()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Analyse de ta bibliothèque…", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.padding(top = 16.dp))
        if (total > 0) {
            LinearProgressIndicator(progress = { scanned.toFloat() / total.toFloat() })
            Spacer(Modifier.padding(top = 8.dp))
            Text("$scanned / $total")
        } else {
            CircularProgressIndicator()
        }
    }
}
