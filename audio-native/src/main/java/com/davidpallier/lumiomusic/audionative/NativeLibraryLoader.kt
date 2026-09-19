package com.davidpallier.lumiomusic.audionative

/**
 * Loads the native lumionative library and exposes a ping used only to verify
 * the JNI/CMake toolchain during Phase 0. The real AAudio exclusive-mode
 * sink API lands in Phase 8.
 */
object NativeLibraryLoader {
    private var loaded = false

    @Synchronized
    fun ensureLoaded(): Boolean {
        if (!loaded) {
            loaded = try {
                System.loadLibrary("lumionative")
                true
            } catch (e: UnsatisfiedLinkError) {
                false
            }
        }
        return loaded
    }

    external fun nativePing(): String

    fun ping(): String {
        ensureLoaded()
        return nativePing()
    }
}
