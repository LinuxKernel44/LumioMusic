package com.davidpallier.lumiomusic.playback.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.media3.common.C
import androidx.media3.common.Format
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** A concrete bit-perfect route: which DAC, and the exact PCM format its mixer will accept. */
class BitPerfectPlan(
    val device: AudioDeviceInfo,
    val output: OutputFormat,
    val channelMask: Int,
    /** An [AudioMixerAttributes] (API 34); typed as Any so this class loads on API 31+. */
    internal val mixerAttributes: Any
)

sealed interface BitPerfectDecision {
    data class Use(val plan: BitPerfectPlan) : BitPerfectDecision
    data class Fallback(val reason: FallbackReason) : BitPerfectDecision
}

/**
 * Android 14+ ("bit-perfect mixer"): when a USB DAC is attached, ask the platform to bypass its
 * mixer/resampler for our stream by registering preferred [AudioMixerAttributes] with
 * `MIXER_BEHAVIOR_BIT_PERFECT`; an [android.media.AudioTrack] created with exactly that format
 * then reaches the DAC untouched. Everything here only *decides and registers* - it never starts
 * audio itself, and any refusal by the platform is reported so the caller can fall back to the
 * normal mixer.
 */
@Singleton
class BitPerfectOutputManager @Inject constructor(
    @ApplicationContext context: Context,
    private val usbDacMonitor: UsbDacMonitor,
    private val preferences: HiResPreferences
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    /** Same attributes the sink uses for its AudioTrack - the platform matches on these. */
    val audioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private var engagedDevice: AudioDeviceInfo? = null

    fun decide(format: Format): BitPerfectDecision {
        val device = usbDacMonitor.device.value ?: return BitPerfectDecision.Fallback(FallbackReason.NO_USB_DAC)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return BitPerfectDecision.Fallback(FallbackReason.OS_TOO_OLD)
        }
        if (!preferences.bitPerfectEnabled) {
            return BitPerfectDecision.Fallback(FallbackReason.DISABLED_IN_SETTINGS)
        }
        // Lossy sources carrying gapless trim info (MP3/AAC) gain nothing from bit-perfect and
        // need DefaultAudioSink's trimming to stay gapless.
        if (format.encoderDelay > 0 || format.encoderPadding > 0) {
            return BitPerfectDecision.Fallback(FallbackReason.LOSSY_SOURCE)
        }
        val minBits = when (format.pcmEncoding) {
            C.ENCODING_PCM_16BIT -> 16
            C.ENCODING_PCM_24BIT -> 24
            C.ENCODING_PCM_32BIT -> 32
            C.ENCODING_PCM_FLOAT -> 24
            else -> return BitPerfectDecision.Fallback(FallbackReason.SOURCE_FORMAT_UNSUPPORTED)
        }
        return plan(device, SourcePcm(format.sampleRate, format.channelCount, minBits))
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun plan(device: AudioDeviceInfo, source: SourcePcm): BitPerfectDecision {
        val supported = try {
            audioManager.getSupportedMixerAttributes(device)
                .filter { it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT }
        } catch (e: RuntimeException) {
            Log.w(TAG, "getSupportedMixerAttributes failed", e)
            emptyList()
        }
        if (supported.isEmpty()) return BitPerfectDecision.Fallback(FallbackReason.DEVICE_UNSUPPORTED)

        val candidates = supported.map {
            OutputFormat(it.format.sampleRate, it.format.channelCount, it.format.encoding)
        }
        return when (val result = BitPerfectPlanner.plan(source, candidates)) {
            is PlanResult.NoMatch -> BitPerfectDecision.Fallback(
                when (result.failure) {
                    PlanFailure.NO_MATCHING_SAMPLE_RATE -> FallbackReason.SAMPLE_RATE_UNSUPPORTED
                    PlanFailure.NO_MATCHING_CHANNELS -> FallbackReason.CHANNELS_UNSUPPORTED
                    PlanFailure.NO_WIDE_ENOUGH_ENCODING -> FallbackReason.ENCODING_UNSUPPORTED
                }
            )
            is PlanResult.Match -> {
                val chosen = supported[candidates.indexOf(result.format)]
                BitPerfectDecision.Use(
                    BitPerfectPlan(device, result.format, chosen.format.channelMask, chosen)
                )
            }
        }
    }

    /** Registers the plan with the platform. Must happen before the matching AudioTrack exists. */
    fun engage(plan: BitPerfectPlan): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        return try {
            val ok = engageApi34(plan)
            if (ok) engagedDevice = plan.device
            Log.i(TAG, "setPreferredMixerAttributes(${plan.device.productName}, ${plan.output}) -> $ok")
            ok
        } catch (e: RuntimeException) {
            Log.w(TAG, "setPreferredMixerAttributes failed", e)
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun engageApi34(plan: BitPerfectPlan): Boolean =
        audioManager.setPreferredMixerAttributes(
            audioAttributes,
            plan.device,
            plan.mixerAttributes as AudioMixerAttributes
        )

    /** Hands the DAC back to the normal mixer (system sounds, other apps). */
    fun disengage() {
        val device = engagedDevice ?: return
        engagedDevice = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        try {
            disengageApi34(device)
            Log.i(TAG, "clearPreferredMixerAttributes(${device.productName})")
        } catch (e: RuntimeException) {
            Log.w(TAG, "clearPreferredMixerAttributes failed", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun disengageApi34(device: AudioDeviceInfo) {
        audioManager.clearPreferredMixerAttributes(audioAttributes, device)
    }

    private companion object {
        const val TAG = "LumioHiRes"
    }
}
