package com.vocatim.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

/** Single DataStore instance shared by all preference classes. */
internal val Context.prefsDataStore by preferencesDataStore(name = "vocatim_prefs")

/**
 * Secrets live in their own file so Android Auto Backup can leave them out:
 * the BYOK API key and the purchase entitlement must never be copied off the
 * device. Auto Backup excludes whole files, not individual keys, so this
 * split is what makes the exclusion in `backup_rules.xml` possible.
 */
internal val Context.secretsDataStore by preferencesDataStore(
    name = "vocatim_secrets",
    produceMigrations = { context -> listOf(LegacySecretsMigration(context)) },
)

/** Moves secrets written before the split out of the backed-up prefs file. */
private class LegacySecretsMigration(
    private val context: Context,
) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean =
        currentData[MIGRATED_KEY] != true

    override suspend fun migrate(currentData: Preferences): Preferences {
        val legacy = context.prefsDataStore.data.first()
        return currentData.toMutablePreferences().apply {
            STRING_KEYS.forEach { name ->
                val key = stringPreferencesKey(name)
                legacy[key]?.let { this[key] = it }
            }
            BOOLEAN_KEYS.forEach { name ->
                val key = booleanPreferencesKey(name)
                legacy[key]?.let { this[key] = it }
            }
            this[MIGRATED_KEY] = true
        }.toPreferences()
    }

    override suspend fun cleanUp() {
        context.prefsDataStore.edit { prefs ->
            STRING_KEYS.forEach { prefs.remove(stringPreferencesKey(it)) }
            BOOLEAN_KEYS.forEach { prefs.remove(booleanPreferencesKey(it)) }
        }
    }

    private companion object {
        val MIGRATED_KEY = booleanPreferencesKey("secrets_migrated")
        val STRING_KEYS = listOf("cloud_ai_base_url", "cloud_ai_api_key", "cloud_ai_model")
        val BOOLEAN_KEYS = listOf("entitlement_pro", "entitlement_dev_pro")
    }
}
