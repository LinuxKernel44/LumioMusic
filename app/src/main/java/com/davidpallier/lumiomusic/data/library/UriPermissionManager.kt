package com.davidpallier.lumiomusic.data.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val LIBRARY_ROOT_URI_KEY = stringPreferencesKey("library_root_uri")

@Singleton
class UriPermissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    val rootUri: Flow<Uri?> = dataStore.data.map { prefs ->
        prefs[LIBRARY_ROOT_URI_KEY]?.let { Uri.parse(it) }
    }

    suspend fun persistTreeUri(treeUri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        dataStore.edit { prefs -> prefs[LIBRARY_ROOT_URI_KEY] = treeUri.toString() }
    }

    suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(LIBRARY_ROOT_URI_KEY) }
    }
}
