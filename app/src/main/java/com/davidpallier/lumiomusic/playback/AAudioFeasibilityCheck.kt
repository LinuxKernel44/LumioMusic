package com.davidpallier.lumiomusic.playback

import android.util.Log
import com.davidpallier.lumiomusic.audionative.AudioNativeSink

private const val TAG = "LumioAAudioFeasibility"

/**
 * Phase 8 spike (see CLAUDE.md "Hi-res audio plan"): opens and immediately closes a throwaway
 * AAudio stream at CD quality to log whether this device's audio HAL actually grants exclusive
 * mode, rather than silently downgrading to shared - the thing Phase 8 exists to find out before
 * any real native `AudioSink` gets built on top of it. Runs once at process start, off the main
 * thread; never wired into actual playback. Check with:
 * `adb logcat -s LumioAAudioFeasibility`
 */
fun logAAudioExclusiveModeFeasibility() {
    val sink = AudioNativeSink()
    val opened = runCatching {
        sink.open(sampleRateHz = 44_100, channelCount = 2, encoding = AudioNativeSink.Encoding.PCM_16BIT)
    }.getOrElse { error ->
        Log.w(TAG, "Native sink unavailable: ${error.message}")
        return
    }

    if (!opened) {
        Log.i(TAG, "AAudio stream open failed in both exclusive and shared mode on this device")
        return
    }

    if (sink.isExclusive) {
        Log.i(TAG, "Exclusive AAudio mode GRANTED on this device - worth building the real sink")
    } else {
        Log.i(TAG, "Exclusive AAudio mode NOT granted (silently downgraded to shared) - the mandatory fallback matters here")
    }
    sink.close()
}
