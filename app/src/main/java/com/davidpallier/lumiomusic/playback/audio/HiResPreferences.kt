package com.davidpallier.lumiomusic.playback.audio

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val BIT_PERFECT_USB_KEY = booleanPreferencesKey("bit_perfect_usb_enabled")

/**
 * User switch for the USB bit-perfect path. The audio thread reads [bitPerfectEnabled]
 * synchronously, so the latest stored value is mirrored into a volatile field.
 */
@Singleton
class HiResPreferences @Inject constructor(private val dataStore: DataStore<Preferences>) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    var bitPerfectEnabled: Boolean = true
        private set

    val bitPerfectEnabledFlow: Flow<Boolean> = dataStore.data.map { it[BIT_PERFECT_USB_KEY] ?: true }

    init {
        scope.launch { bitPerfectEnabledFlow.collect { bitPerfectEnabled = it } }
    }

    fun setBitPerfectEnabled(enabled: Boolean) {
        bitPerfectEnabled = enabled
        scope.launch { dataStore.edit { it[BIT_PERFECT_USB_KEY] = enabled } }
    }
}
