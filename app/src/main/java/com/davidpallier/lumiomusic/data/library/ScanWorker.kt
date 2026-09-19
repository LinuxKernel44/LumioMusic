package com.davidpallier.lumiomusic.data.library

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class ScanWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val scanner: LibraryScanner,
    private val uriPermissionManager: UriPermissionManager
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val rootUri = uriPermissionManager.rootUri.first() ?: return Result.failure()
        return try {
            val result = scanner.scan(rootUri) { scanned, total ->
                setProgress(workDataOf(KEY_SCANNED to scanned, KEY_TOTAL to total))
            }
            Result.success(
                workDataOf(
                    KEY_TOTAL to result.total,
                    KEY_ADDED to result.added,
                    KEY_UPDATED to result.updated
                )
            )
        } catch (e: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "library_scan"
        const val KEY_SCANNED = "scanned"
        const val KEY_TOTAL = "total"
        const val KEY_ADDED = "added"
        const val KEY_UPDATED = "updated"
    }
}
