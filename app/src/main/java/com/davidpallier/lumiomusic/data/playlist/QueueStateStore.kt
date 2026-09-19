package com.davidpallier.lumiomusic.data.playlist

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class PersistedQueue(val trackIds: List<Long>, val currentIndex: Int, val positionMs: Long)

private val QUEUE_TRACK_IDS_KEY = stringPreferencesKey("queue_track_ids")
private val QUEUE_INDEX_KEY = intPreferencesKey("queue_index")
private val QUEUE_POSITION_KEY = longPreferencesKey("queue_position_ms")

/**
 * Lets [com.davidpallier.lumiomusic.playback.PlaybackService] resume the last queue on a fresh
 * process start (see [androidx.media3.session.MediaSession.Callback.onPlaybackResumption]),
 * since the in-memory [androidx.media3.exoplayer.ExoPlayer] queue doesn't survive process death.
 */
@Singleton
class QueueStateStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    suspend fun save(trackIds: List<Long>, currentIndex: Int, positionMs: Long) {
        dataStore.edit { prefs ->
            prefs[QUEUE_TRACK_IDS_KEY] = trackIds.joinToString(",")
            prefs[QUEUE_INDEX_KEY] = currentIndex
            prefs[QUEUE_POSITION_KEY] = positionMs
        }
    }

    suspend fun load(): PersistedQueue? {
        val prefs = dataStore.data.first()
        val ids = prefs[QUEUE_TRACK_IDS_KEY]?.split(",")?.mapNotNull { it.toLongOrNull() }
        if (ids.isNullOrEmpty()) return null
        val index = (prefs[QUEUE_INDEX_KEY] ?: 0).coerceIn(0, ids.size - 1)
        val position = prefs[QUEUE_POSITION_KEY] ?: 0L
        return PersistedQueue(ids, index, position)
    }
}
