package com.davidpallier.lumiomusic.playback.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class OutputMode { BIT_PERFECT, SYSTEM_MIXER }

/** Why the app is using the regular Android mixer instead of a bit-perfect USB path. */
enum class FallbackReason {
    NO_USB_DAC,
    OS_TOO_OLD,
    DISABLED_IN_SETTINGS,
    DEVICE_UNSUPPORTED,
    SAMPLE_RATE_UNSUPPORTED,
    CHANNELS_UNSUPPORTED,
    ENCODING_UNSUPPORTED,
    LOSSY_SOURCE,
    SOURCE_FORMAT_UNSUPPORTED,
    DAC_REJECTED
}

/** What the audio sink is actually doing for the stream currently playing. */
data class AudioOutputStatus(
    val mode: OutputMode? = null,
    val sampleRate: Int = 0,
    val bitDepth: Int = 0,
    val isFloat: Boolean = false,
    val fallbackReason: FallbackReason? = null
)

@Singleton
class AudioOutputStatusStore @Inject constructor() {
    private val _status = MutableStateFlow(AudioOutputStatus())
    val status: StateFlow<AudioOutputStatus> = _status.asStateFlow()

    fun publish(status: AudioOutputStatus) {
        _status.value = status
    }
}
