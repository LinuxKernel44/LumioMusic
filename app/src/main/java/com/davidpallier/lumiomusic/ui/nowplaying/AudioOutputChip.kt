package com.davidpallier.lumiomusic.ui.nowplaying

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.davidpallier.lumiomusic.R
import com.davidpallier.lumiomusic.playback.audio.FallbackReason
import com.davidpallier.lumiomusic.playback.audio.OutputMode

/** One-line "what is the audio actually doing" indicator; tap for the details and the switch. */
@Composable
fun AudioOutputChip(modifier: Modifier = Modifier, viewModel: AudioOutputViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val status = state.status
    val mode = status.mode ?: return
    var showDetails by remember { mutableStateOf(false) }

    val format = formatDescription(status.bitDepth, status.isFloat, status.sampleRate)
    val label = if (mode == OutputMode.BIT_PERFECT) {
        stringResource(R.string.audio_output_usb_bit_perfect, format)
    } else {
        stringResource(R.string.audio_output_system_mixer, format)
    }
    Text(
        text = label,
        color = Color.White.copy(alpha = if (mode == OutputMode.BIT_PERFECT) 0.95f else 0.6f),
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .clickable { showDetails = true }
            .padding(horizontal = 24.dp, vertical = 6.dp)
    )

    if (showDetails) AudioOutputDialog(state, viewModel::setBitPerfectEnabled) { showDetails = false }
}

@Composable
private fun AudioOutputDialog(
    state: AudioOutputUiState,
    onBitPerfectChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val status = state.status
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.audio_output_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(formatDescription(status.bitDepth, status.isFloat, status.sampleRate), style = MaterialTheme.typography.bodyLarge)
                Text(
                    state.usbDacName?.let { stringResource(R.string.audio_output_dac_name, it) }
                        ?: stringResource(R.string.audio_output_no_dac),
                    style = MaterialTheme.typography.bodyMedium
                )
                val reason = if (!state.osSupportsBitPerfect) FallbackReason.OS_TOO_OLD else status.fallbackReason
                reason?.let { r -> reasonText(r)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) } }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(stringResource(R.string.audio_output_bit_perfect_switch), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.audio_output_bit_perfect_summary), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = state.bitPerfectEnabled && state.osSupportsBitPerfect,
                        onCheckedChange = onBitPerfectChange,
                        enabled = state.osSupportsBitPerfect
                    )
                }
                Text(stringResource(R.string.audio_output_volume_warning), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.audio_output_close)) } }
    )
}

@Composable
private fun reasonText(reason: FallbackReason): String? = when (reason) {
    FallbackReason.NO_USB_DAC -> null // already stated as "No USB DAC connected"
    FallbackReason.OS_TOO_OLD -> stringResource(R.string.audio_output_reason_os_too_old)
    FallbackReason.DISABLED_IN_SETTINGS -> stringResource(R.string.audio_output_reason_disabled)
    FallbackReason.DEVICE_UNSUPPORTED -> stringResource(R.string.audio_output_reason_device_unsupported)
    FallbackReason.SAMPLE_RATE_UNSUPPORTED -> stringResource(R.string.audio_output_reason_sample_rate)
    FallbackReason.CHANNELS_UNSUPPORTED -> stringResource(R.string.audio_output_reason_channels)
    FallbackReason.ENCODING_UNSUPPORTED -> stringResource(R.string.audio_output_reason_encoding)
    FallbackReason.LOSSY_SOURCE -> stringResource(R.string.audio_output_reason_lossy)
    FallbackReason.SOURCE_FORMAT_UNSUPPORTED -> stringResource(R.string.audio_output_reason_source)
    FallbackReason.DAC_REJECTED -> stringResource(R.string.audio_output_reason_rejected)
}

@Composable
private fun formatDescription(bits: Int, isFloat: Boolean, sampleRate: Int): String {
    val rate = formatKhz(sampleRate)
    return if (isFloat) stringResource(R.string.audio_output_format_float, bits, rate)
    else stringResource(R.string.audio_output_format, bits, rate)
}

/** 44100 -> "44.1 kHz", 96000 -> "96 kHz". */
internal fun formatKhz(sampleRate: Int): String {
    val khz = sampleRate / 1000.0
    return if (sampleRate % 1000 == 0) "${khz.toInt()} kHz" else "${"%.1f".format(java.util.Locale.ROOT, khz)} kHz"
}
