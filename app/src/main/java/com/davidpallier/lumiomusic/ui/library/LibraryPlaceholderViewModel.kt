package com.davidpallier.lumiomusic.ui.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.davidpallier.lumiomusic.data.db.TrackDao
import com.davidpallier.lumiomusic.data.library.ScanWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class LibraryPlaceholderViewModel @Inject constructor(
    trackDao: TrackDao,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val trackCount = trackDao.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun rescan() {
        val request = OneTimeWorkRequestBuilder<ScanWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ScanWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
