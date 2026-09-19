package com.davidpallier.lumiomusic.data.library

private val AUDIO_EXTENSIONS = setOf(
    "flac", "mp3", "m4a", "aac", "ogg", "opus", "wav"
)

object AudioFileFilter {
    fun matches(displayName: String, mimeType: String?): Boolean {
        if (mimeType != null && mimeType.startsWith("audio/")) return true
        val ext = displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        return ext in AUDIO_EXTENSIONS
    }
}
