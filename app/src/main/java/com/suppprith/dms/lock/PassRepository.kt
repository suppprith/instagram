package com.suppprith.dms.lock

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import java.time.ZoneId

private val Context.lockStore: DataStore<Preferences> by preferencesDataStore(name = "lock")

data class LockData(val policy: PassPolicy = PassPolicy.Default, val pass: PassState = PassState(), val loaded: Boolean = false)

/** DataStore-backed pass state. All rules live in [PassState] and [PassPolicy]; this only stores them. */
class PassRepository(context: Context, scope: CoroutineScope) {
    private val store = context.applicationContext.lockStore

    private object Keys {
        val policy = stringPreferencesKey("policy")
        val dayKey = stringPreferencesKey("day_key")
        val used = intPreferencesKey("used")
        val endsAt = longPreferencesKey("ends_at")
        val startedAt = longPreferencesKey("started_at")
        val reasons = stringPreferencesKey("reasons")
    }

    private val flow = store.data.map { p ->
        LockData(
            policy = PassPolicy.decode(p[Keys.policy]),
            pass = PassState(
                dayKey = p[Keys.dayKey].orEmpty(),
                used = p[Keys.used] ?: 0,
                endsAt = p[Keys.endsAt],
                startedAt = p[Keys.startedAt],
            ),
            loaded = true,
        )
    }

    val data: StateFlow<LockData> = flow.stateIn(scope, SharingStarted.Eagerly, LockData())

    private fun zone(): ZoneId = ZoneId.systemDefault()

    /**
     * Current, normalized state for decisions on the accessibility thread. Reads the cached value;
     * only the very first call after process start may block briefly on disk.
     */
    fun snapshot(now: Long = System.currentTimeMillis()): LockData {
        val d = data.value.takeIf { it.loaded } ?: runBlocking { flow.first() }
        return d.copy(pass = d.pass.normalize(now, zone(), d.policy))
    }

    /** Starts the next pass. Returns the new state, or null when none are left or one is running. */
    suspend fun startPass(reason: PassReason, now: Long = System.currentTimeMillis()): PassState? {
        var started: PassState? = null
        store.edit { p ->
            val policy = PassPolicy.decode(p[Keys.policy])
            val current = read(p)
            val next = current.start(now, zone(), policy) ?: return@edit
            write(p, next)
            // Last 50 reasons, for the user's own reference. Never uploaded.
            val history = p[Keys.reasons].orEmpty().split(';').filter { it.isNotBlank() }
            p[Keys.reasons] = (history + "${next.dayKey}:${reason.name}").takeLast(50).joinToString(";")
            started = next
        }
        return started
    }

    suspend fun endPass() {
        store.edit { p -> write(p, read(p).end()) }
    }

    suspend fun setPolicy(policy: PassPolicy) {
        store.edit { it[Keys.policy] = policy.encode() }
    }

    private fun read(p: Preferences) = PassState(
        dayKey = p[Keys.dayKey].orEmpty(),
        used = p[Keys.used] ?: 0,
        endsAt = p[Keys.endsAt],
        startedAt = p[Keys.startedAt],
    )

    private fun write(p: MutablePreferences, s: PassState) {
        p[Keys.dayKey] = s.dayKey
        p[Keys.used] = s.used
        if (s.endsAt != null) p[Keys.endsAt] = s.endsAt else p.remove(Keys.endsAt)
        if (s.startedAt != null) p[Keys.startedAt] = s.startedAt else p.remove(Keys.startedAt)
    }
}
