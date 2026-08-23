package com.vocatim.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.vocatim.app.BuildConfig
import com.vocatim.app.data.billing.AdFreeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "AdsController"

/**
 * Owns everything ad-related: consent, SDK start-up, and the interstitial.
 *
 * Two rules hold everywhere in here. Nothing loads or shows while the user is
 * ad-free — a paid user must not even cause an ad request. And the SDK is not
 * started until consent has been resolved, because starting it earlier is what
 * turns a consent bug into a privacy incident.
 *
 * Every silent bail-out reports its reason to [AdsDiagnostics]; without that,
 * "no ads" is undebuggable from the outside.
 */
class AdsController(
    private val context: Context,
    private val adFreeStore: AdFreeStore,
    private val diagnostics: AdsDiagnostics,
    private val scope: CoroutineScope,
) {

    private val started = AtomicBoolean(false)
    private val sdkStarted = AtomicBoolean(false)
    private var interstitial: InterstitialAd? = null
    private var loading = false
    private var lastShownAt = 0L

    private val _ready = MutableStateFlow(false)
    /** True once the SDK is initialised and may serve. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /**
     * Resolves consent, then starts the SDK. Safe to call repeatedly; only the
     * first call does work. Must be given an Activity because the consent form
     * is a dialog.
     */
    fun start(activity: Activity) {
        scope.launch {
            val adFree = adFreeStore.current()
            diagnostics.blockedByEntitlement.value = adFree
            if (adFree) return@launch
            if (!started.compareAndSet(false, true)) return@launch
            diagnostics.controllerStarted.value = true
            gatherConsent(activity)
        }
    }

    private fun gatherConsent(activity: Activity) {
        val params = ConsentRequestParameters.Builder()
            .apply {
                if (BuildConfig.DEBUG) {
                    // Without this the form never appears outside the EEA, so
                    // the consent path would go untested until release.
                    setConsentDebugSettings(
                        ConsentDebugSettings.Builder(activity)
                            .setDebugGeography(
                                ConsentDebugSettings.DebugGeography
                                    .DEBUG_GEOGRAPHY_EEA
                            )
                            .build()
                    )
                }
            }
            .build()

        val consentInformation: ConsentInformation =
            UserMessagingPlatform.getConsentInformation(activity)

        // Most Vocatim users are outside the EEA. If UMP already says we
        // may request ads, start the SDK without waiting on the form.
        if (consentInformation.canRequestAds()) {
            diagnostics.consentCanRequestAds.value = true
            initialize()
        }

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form: " + formError.message)
                        diagnostics.lastConsentError.value =
                            "form code " + formError.errorCode + ": " + formError.message
                    }
                    startSdkIfAllowed(consentInformation)
                }
            },
            { requestError ->
                Log.w(TAG, "Consent info update failed: " + requestError.message)
                diagnostics.lastConsentError.value =
                    "update code " + requestError.errorCode + ": " + requestError.message
                // A missing UMP message used to black-hole ads for the whole
                // session (Indonesia, no GDPR form). Serve ads unless the
                // user actually declined.
                startSdkIfAllowed(consentInformation, allowIfUnknown = true)
            },
        )
    }

    private fun startSdkIfAllowed(
        consentInformation: ConsentInformation,
        allowIfUnknown: Boolean = false,
    ) {
        diagnostics.consentCanRequestAds.value = consentInformation.canRequestAds()
        if (consentInformation.canRequestAds() || allowIfUnknown) initialize()
    }

    private fun initialize() {
        if (!sdkStarted.compareAndSet(false, true)) return
        MobileAds.initialize(context) {
            _ready.value = true
            preloadInterstitial()
        }
    }

    fun preloadInterstitial() {
        if (loading || interstitial != null) return
        scope.launch {
            if (adFreeStore.current() || !_ready.value) return@launch
            loading = true
            InterstitialAd.load(
                context,
                BuildConfig.ADMOB_INTERSTITIAL_UNIT_ID,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitial = ad
                        loading = false
                        diagnostics.interstitialLoaded.value = true
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w(TAG, "Interstitial load failed: " + error.message)
                        interstitial = null
                        loading = false
                        diagnostics.interstitialLoaded.value = false
                        diagnostics.lastInterstitialError.value =
                            "code " + error.code + ": " + error.message
                    }
                },
            )
        }
    }

    /**
     * Shows the interstitial if one is loaded, the user has not paid, and
     * enough time has passed since the last one.
     *
     * The interval matters: several queued transcriptions can finish seconds
     * apart, and a full-screen ad per finish would be both hostile and a
     * likely AdMob policy problem.
     */
    fun showInterstitial(activity: Activity, onDismissed: () -> Unit = {}) {
        scope.launch {
            val ad = interstitial
            val adFree = adFreeStore.current()
            val now = android.os.SystemClock.elapsedRealtime()
            if (ad == null || adFree || now - lastShownAt < MIN_INTERVAL_MS) {
                diagnostics.lastShowDecision.value = "skipped: " + when {
                    ad == null -> "no ad loaded"
                    adFree -> "device ad-free"
                    else -> "cooldown (<3 min)"
                }
                onDismissed()
                preloadInterstitial()
                return@launch
            }
            diagnostics.lastShowDecision.value = "shown"
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    interstitial = null
                    lastShownAt = android.os.SystemClock.elapsedRealtime()
                    preloadInterstitial()
                    onDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    Log.w(TAG, "Interstitial show failed: " + error.message)
                    interstitial = null
                    diagnostics.interstitialLoaded.value = false
                    diagnostics.lastInterstitialError.value =
                        "show code " + error.code + ": " + error.message
                    preloadInterstitial()
                    onDismissed()
                }
            }
            ad.show(activity)
        }
    }

    private companion object {
        const val MIN_INTERVAL_MS = 3 * 60 * 1000L
    }
}
