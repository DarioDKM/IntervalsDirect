package com.radfahrzeugs.intervalsdirect.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "intervals_connect_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        val ATHLETE_ID_KEY = stringPreferencesKey("athlete_id")
        val API_KEY_KEY = stringPreferencesKey("api_key")
        val AUTO_SYNC_KEY = booleanPreferencesKey("auto_sync_enabled")
        val SYNC_INTERVAL_KEY = androidx.datastore.preferences.core.intPreferencesKey("sync_interval_hours")
        val LAST_SYNC_KEY = longPreferencesKey("last_sync_timestamp")

        val AUTO_BACKUP_KEY = booleanPreferencesKey("auto_backup_enabled")
        val BACKUP_FOLDER_URI_KEY = stringPreferencesKey("backup_folder_uri")

        const val DEFAULT_ATHLETE_ID = ""
        const val DEFAULT_API_KEY = ""
        const val DEFAULT_SYNC_INTERVAL_HOURS = 3
    }

    val athleteIdFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[ATHLETE_ID_KEY] ?: DEFAULT_ATHLETE_ID
    }

    val apiKeyFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[API_KEY_KEY] ?: DEFAULT_API_KEY
    }

    val autoSyncFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_SYNC_KEY] ?: true
    }

    val syncIntervalFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[SYNC_INTERVAL_KEY] ?: DEFAULT_SYNC_INTERVAL_HOURS
    }

    val lastSyncFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[LAST_SYNC_KEY] ?: 0L
    }

    val autoBackupFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[AUTO_BACKUP_KEY] ?: false
    }

    val backupFolderUriFlow: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[BACKUP_FOLDER_URI_KEY]
    }

    suspend fun saveSettings(
        athleteId: String,
        apiKey: String,
        autoSync: Boolean,
        syncIntervalHours: Int = 3,
        autoBackup: Boolean = false,
        backupFolderUri: String? = null
    ) {
        context.dataStore.edit { prefs ->
            prefs[ATHLETE_ID_KEY] = athleteId
            prefs[API_KEY_KEY] = apiKey
            prefs[AUTO_SYNC_KEY] = autoSync
            prefs[SYNC_INTERVAL_KEY] = syncIntervalHours
            prefs[AUTO_BACKUP_KEY] = autoBackup
            if (backupFolderUri != null) {
                prefs[BACKUP_FOLDER_URI_KEY] = backupFolderUri
            } else if (!autoBackup) {
                // Keep existing URI unless explicitly changed
            }
        }
    }

    suspend fun saveBackupFolder(uriString: String) {
        context.dataStore.edit { prefs ->
            prefs[BACKUP_FOLDER_URI_KEY] = uriString
            prefs[AUTO_BACKUP_KEY] = true
        }
    }

    suspend fun updateLastSync(timestamp: Long) {
        context.dataStore.edit { prefs ->
            prefs[LAST_SYNC_KEY] = timestamp
        }
    }
}
