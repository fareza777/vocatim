package com.vocatim.app.ads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vocatim.app.data.billing.AdFreeStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Small shared UI facade for ad state.
 *
 * Screens should not each reimplement the paid-entitlement check. Keeping the
 * rule here also means a purchase hides banners on every screen as soon as
 * Play Billing updates the cached entitlement.
 */
@HiltViewModel
class AdsViewModel @Inject constructor(
    adFreeStore: AdFreeStore,
    adsController: AdsController,
    billingManager: com.vocatim.app.data.billing.BillingManager,
    val diagnostics: AdsDiagnostics,
) : ViewModel() {

    /** True when a banner may be requested and displayed. */
    val showAds: StateFlow<Boolean> = adFreeStore.isAdFree
        .map { !it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** True once consent has been resolved and the Mobile Ads SDK is ready. */
    val adsReady: StateFlow<Boolean> = adsController.ready

    val removeAdsPrice: StateFlow<String?> = billingManager.formattedPrice
}
