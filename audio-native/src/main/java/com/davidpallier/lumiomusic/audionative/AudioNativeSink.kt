package com.davidpallier.lumiomusic.audionative

/**
 * Phase 8 feasibility spike: best-effort AAudio exclusive-mode output path (see CLAUDE.md's
 * "Hi-res audio plan"). Pure Kotlin, no Media3 dependency - a Media3 `AudioSink` adapter that
 * wraps this for real playback would live in `:app/playback` and is not implemented yet; that is
 * intentionally deferred until this spike confirms exclusive mode is worth pursuing on real
 * hardware (this class exists to make that call, not to be production-wired on its own).
 *
 * [open] always attempts exclusive mode first and transparently retries in shared mode if that
 * fails (see `aaudio_sink.cpp`). Never assume exclusive mode was granted just because [open]
 * returned true - many devices silently downgrade to shared instead of failing outright, so
 * always check [isExclusive] afterwards and fall back to the standard Media3
 * `DefaultAudioSink` path whenever it's false.
 */
class AudioNativeSink {
    private var handle: Long = 0

    val isOpen: Boolean get() = handle != 0L

    /** Returns true if a stream was opened at all (exclusive or shared - check [isExclusive]). */
    fun open(sampleRateHz: Int, channelCount: Int, encoding: Encoding): Boolean {
        check(!isOpen) { "AudioNativeSink is already open" }
        if (!NativeLibraryLoader.ensureLoaded()) return false
        handle = nativeOpen(sampleRateHz, channelCount, encoding.aaudioFormat)
        return isOpen
    }

    val isExclusive: Boolean
        get() = isOpen && nativeIsExclusive(handle)

    /** Blocking write of PCM frames (not bytes). Returns frames written, or a negative AAudio error code. */
    fun write(buffer: ByteArray, numFrames: Int, timeoutNanos: Long = 0L): Int {
        check(isOpen) { "AudioNativeSink is not open" }
        return nativeWrite(handle, buffer, numFrames, timeoutNanos)
    }

    fun close() {
        if (isOpen) {
            nativeClose(handle)
            handle = 0
        }
    }

    /** Mirrors AAudio's aaudio_format_t for the PCM encodings this sink supports. */
    enum class Encoding(internal val aaudioFormat: Int) {
        PCM_16BIT(1),
        PCM_FLOAT(2)
    }

    private external fun nativeOpen(sampleRateHz: Int, channelCount: Int, format: Int): Long
    private external fun nativeIsExclusive(handle: Long): Boolean
    private external fun nativeWrite(handle: Long, buffer: ByteArray, numFrames: Int, timeoutNanos: Long): Int
    private external fun nativeClose(handle: Long)
}
