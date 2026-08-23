package com.vocatim.app.ads

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Debug window into the ads pipeline. Every stage that can silently kill ads
 * (entitlement, consent, SDK start, banner load, interstitial load/show)
 * records its last outcome here, and the debug screen renders it — so
 * "no ads" stops being a guessing game.
 */
class AdsDiagnostics {

    /** Non-null once start() has read the entitlement; true = ads blocked. */
    val blockedByEntitlement = MutableStateFlow<Boolean?>(null)

    /** True once start() got past the entitlement check and began consent. */
    val controllerStarted = MutableStateFlow(false)

    /** null = consent not resolved yet this session. */
    val consentCanRequestAds = MutableStateFlow<Boolean?>(null)
    val lastConsentError = MutableStateFlow<String?>(null)

    val bannerLoaded = MutableStateFlow(false)
    val lastBannerError = MutableStateFlow<String?>(null)

    val interstitialLoaded = MutableStateFlow(false)
    val lastInterstitialError = MutableStateFlow<String?>(null)

    /** Why the last showInterstitial() call did or did not show. */
    val lastShowDecision = MutableStateFlow<String?>(null)
}
