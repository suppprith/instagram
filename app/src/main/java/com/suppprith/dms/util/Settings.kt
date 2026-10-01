package com.suppprith.dms.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** App settings and small bits of state. Pass state lives in its own store (lock/PassRepository). */
data class AppSettings(
    /** False only for the placeholder before the store has been read. */
    val loaded: Boolean = false,
    val firstRunDone: Boolean = false,
    val notifyAsked: Boolean = false,
    val notificationsEnabled: Boolean = false,
    val lockEnabled: Boolean = false,
    val lastBadge: Int = 0,
    val latestVersion: String? = null,
    val latestUrl: String? = null,
    val lastUpdateCheck: Long = 0,
    val dismissedUpdate: String? = null,
    val lastPatchFetch: Long = 0,
)

class Settings(context: Context, scope: CoroutineScope) {
    private val store = context.applicationContext.settingsStore

    private object Keys {
        val firstRunDone = booleanPreferencesKey("first_run_done")
        val notifyAsked = booleanPreferencesKey("notify_asked")
        val notificationsEnabled = booleanPreferencesKey("notifications_enabled")
        val lockEnabled = booleanPreferencesKey("lock_enabled")
        val lastBadge = intPreferencesKey("last_badge")
        val latestVersion = stringPreferencesKey("latest_version")
        val latestUrl = stringPreferencesKey("latest_url")
        val lastUpdateCheck = longPreferencesKey("last_update_check")
        val dismissedUpdate = stringPreferencesKey("dismissed_update")
        val lastPatchFetch = longPreferencesKey("last_patch_fetch")
    }

    val updates: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            loaded = true,
            firstRunDone = p[Keys.firstRunDone] ?: false,
            notifyAsked = p[Keys.notifyAsked] ?: false,
            notificationsEnabled = p[Keys.notificationsEnabled] ?: false,
            lockEnabled = p[Keys.lockEnabled] ?: false,
            lastBadge = p[Keys.lastBadge] ?: 0,
            latestVersion = p[Keys.latestVersion],
            latestUrl = p[Keys.latestUrl],
            lastUpdateCheck = p[Keys.lastUpdateCheck] ?: 0,
            dismissedUpdate = p[Keys.dismissedUpdate],
            lastPatchFetch = p[Keys.lastPatchFetch] ?: 0,
        )
    }

    val state: StateFlow<AppSettings> = updates.stateIn(scope, SharingStarted.Eagerly, AppSettings())

    suspend fun current(): AppSettings = updates.first()

    suspend fun setFirstRunDone() = store.edit { it[Keys.firstRunDone] = true }

    suspend fun setNotifications(enabled: Boolean) = store.edit {
        it[Keys.notifyAsked] = true
        it[Keys.notificationsEnabled] = enabled
    }

    suspend fun setLockEnabled(enabled: Boolean) = store.edit { it[Keys.lockEnabled] = enabled }

    suspend fun setLastBadge(count: Int) = store.edit { it[Keys.lastBadge] = count }

    suspend fun setLatestRelease(version: String?, url: String?, checkedAt: Long) = store.edit {
        if (version != null) it[Keys.latestVersion] = version else it.remove(Keys.latestVersion)
        if (url != null) it[Keys.latestUrl] = url else it.remove(Keys.latestUrl)
        it[Keys.lastUpdateCheck] = checkedAt
    }

    suspend fun dismissUpdate(version: String) = store.edit { it[Keys.dismissedUpdate] = version }

    suspend fun setLastPatchFetch(at: Long) = store.edit { it[Keys.lastPatchFetch] = at }
}
