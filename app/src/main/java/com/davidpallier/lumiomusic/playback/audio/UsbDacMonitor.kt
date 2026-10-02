package com.davidpallier.lumiomusic.playback.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Tracks the USB audio output (DAC / dongle / USB headset) currently attached, if any. */
@Singleton
class UsbDacMonitor @Inject constructor(@ApplicationContext context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)

    private val _device = MutableStateFlow(findUsbOutput())
    val device: StateFlow<AudioDeviceInfo?> = _device.asStateFlow()

    init {
        audioManager.registerAudioDeviceCallback(
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
            },
            Handler(Looper.getMainLooper())
        )
    }

    private fun refresh() {
        _device.value = findUsbOutput()
    }

    private fun findUsbOutput(): AudioDeviceInfo? =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }
}
