package com.davidpallier.lumiomusic.data.lyrics

/**
 * [GeniusLyricsProvider] unofficially scrapes genius.com - there is no free official lyrics-text
 * API. This is a ToS gray area kept strictly to personal use (see CLAUDE.md); disabled by
 * default, flip this to opt in locally. This app must never be published to the Play Store
 * while this is reachable.
 */
object LyricsFeatureFlags {
    const val GENIUS_ENABLED = false
}
