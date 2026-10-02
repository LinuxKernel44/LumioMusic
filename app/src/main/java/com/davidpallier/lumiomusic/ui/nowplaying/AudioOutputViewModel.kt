package com.davidpallier.lumiomusic.ui.nowplaying

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.davidpallier.lumiomusic.playback.audio.AudioOutputStatus
import com.davidpallier.lumiomusic.playback.audio.AudioOutputStatusStore
import com.davidpallier.lumiomusic.playback.audio.HiResPreferences
import com.davidpallier.lumiomusic.playback.audio.UsbDacMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AudioOutputUiState(
    val usbDacName: String? = null,
    val status: AudioOutputStatus = AudioOutputStatus(),
    val bitPerfectEnabled: Boolean = true,
    val osSupportsBitPerfect: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
)

@HiltViewModel
class AudioOutputViewModel @Inject constructor(
    usbDacMonitor: UsbDacMonitor,
    statusStore: AudioOutputStatusStore,
    private val preferences: HiResPreferences
) : ViewModel() {

    val state: StateFlow<AudioOutputUiState> = combine(
        usbDacMonitor.device,
        statusStore.status,
        preferences.bitPerfectEnabledFlow
    ) { device, status, enabled ->
        AudioOutputUiState(
            usbDacName = device?.let { it.productName?.toString()?.takeIf(String::isNotBlank) ?: "USB audio" },
            status = status,
            bitPerfectEnabled = enabled
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AudioOutputUiState())

    fun setBitPerfectEnabled(enabled: Boolean) = preferences.setBitPerfectEnabled(enabled)
}
