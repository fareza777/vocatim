package com.vocatim.app.data.billing

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.vocatim.app.data.prefs.secretsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Whether this device has paid to remove ads.
 *
 * Every feature is free now; the only thing money buys is an ad-free app.
 * Two products grant it: the current [BillingManager.PRODUCT_REMOVE_ADS], and
 * the retired [BillingManager.PRODUCT_LIFETIME] that early users bought when
 * the app sold features instead. Anyone holding the old one keeps everything
 * they had and never sees an ad.
 *
 * Lives in the secrets DataStore so a restored Auto Backup can never hand a
 * paid state to a device that did not buy it.
 */
class AdFreeStore(private val context: Context) {

    val isAdFree: Flow<Boolean> = context.secretsDataStore.data.map {
        (it[AD_FREE_KEY] ?: false) ||
            (it[LEGACY_PRO_KEY] ?: false) ||
            (it[DEV_OVERRIDE_KEY] ?: false)
    }

    suspend fun current(): Boolean = isAdFree.first()

    /** Set from the current remove-ads product. */
    suspend fun setAdFree(value: Boolean) {
        context.secretsDataStore.edit { it[AD_FREE_KEY] = value }
    }

    /** Set from the retired lifetime product; kept separate so the two
     *  entitlements can be re-synced with Play independently. */
    suspend fun setLegacyLifetime(value: Boolean) {
        context.secretsDataStore.edit { it[LEGACY_PRO_KEY] = value }
    }

    /** Debug-only override; never called from release code. */
    suspend fun setDevOverride(enabled: Boolean) {
        context.secretsDataStore.edit { it[DEV_OVERRIDE_KEY] = enabled }
    }

    private companion object {
        val AD_FREE_KEY = booleanPreferencesKey("entitlement_remove_ads")
        // Same key the old paid tier used, so existing buyers are recognised
        // on upgrade without re-querying Play first.
        val LEGACY_PRO_KEY = booleanPreferencesKey("entitlement_pro")
        val DEV_OVERRIDE_KEY = booleanPreferencesKey("entitlement_dev_pro")
    }
}
