package com.davidpallier.lumiomusic.ui.setup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.davidpallier.lumiomusic.data.library.ScanWorker
import com.davidpallier.lumiomusic.data.library.UriPermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FolderPickerViewModel @Inject constructor(
    private val uriPermissionManager: UriPermissionManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    fun onFolderPicked(uri: Uri) {
        viewModelScope.launch {
            uriPermissionManager.persistTreeUri(uri)
            val request = OneTimeWorkRequestBuilder<ScanWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ScanWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
