package com.vocatim.app.data.billing

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.vocatim.app.data.prefs.prefsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Local free-tier bookkeeping: total transcribed audio time, plus the
 * cached lifetime entitlement so the app stays unlocked offline.
 */
class QuotaStore(private val context: Context) {

    val usedMs: Flow<Long> = context.prefsDataStore.data.map { it[USED_MS_KEY] ?: 0L }

    /** Cached Play entitlement; refreshed whenever Billing connects. */
    val isProCached: Flow<Boolean> = context.prefsDataStore.data.map { it[PRO_KEY] ?: false }

    suspend fun addUsage(ms: Long) {
        if (ms <= 0) return
        context.prefsDataStore.edit { prefs ->
            prefs[USED_MS_KEY] = (prefs[USED_MS_KEY] ?: 0L) + ms
        }
    }

    suspend fun setPro(pro: Boolean) {
        context.prefsDataStore.edit { it[PRO_KEY] = pro }
    }

    suspend fun currentUsedMs(): Long = usedMs.first()

    suspend fun currentIsPro(): Boolean = isProCached.first()

    companion object {
        const val FREE_LIMIT_MS = 30L * 60 * 1000

        private val USED_MS_KEY = longPreferencesKey("quota_used_ms")
        private val PRO_KEY = booleanPreferencesKey("entitlement_pro")
    }
}
