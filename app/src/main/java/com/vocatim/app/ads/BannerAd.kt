package com.vocatim.app.ads

import android.os.Handler
import android.os.Looper
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.vocatim.app.BuildConfig

private const val BANNER_RETRY_DELAY_MS = 30_000L

/**
 * App-level ad strip. Must stay composed across navigation: tearing the
 * AdView down on every screen change is what made the banner appear once
 * and then vanish.
 *
 * [hidden] collapses the strip (record screen) without destroying the view.
 */
@Composable
fun MonetizedBanner(
    hidden: Boolean = false,
) {
    val activity = LocalContext.current as ComponentActivity
    val viewModel: AdsViewModel = hiltViewModel(activity)
    val showAds by viewModel.showAds.collectAsStateWithLifecycle()
    val adsReady by viewModel.adsReady.collectAsStateWithLifecycle()
    if (!showAds) return

    if (adsReady) {
        // The app draws edge to edge, and Scaffold does not inset a bottomBar
        // for you: without this the strip sits under the gesture bar or the
        // back/home/recents buttons. A partly covered ad is also an accidental
        // click waiting to happen, which AdMob does not allow.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (hidden) Modifier.height(0.dp).clipToBounds()
                    else Modifier.navigationBarsPadding()
                )
        ) {
            BannerAd(
                modifier = Modifier.fillMaxWidth(),
                diagnostics = viewModel.diagnostics,
            )
        }
    }
}

/**
 * One AdView for the life of this composition. Configure + load only in
 * [AndroidView]'s factory — setting adUnitId twice crashes, and destroy()
 * on a remembered instance leaves a dead view on the next entry.
 */
@Composable
private fun BannerAd(
    diagnostics: AdsDiagnostics,
    modifier: Modifier = Modifier,
) {
    val widthDp = LocalContext.current.resources.configuration.screenWidthDp

    AndroidView(
        factory = { context ->
            AdView(context).apply {
                adUnitId = BuildConfig.ADMOB_BANNER_UNIT_ID
                setAdSize(
                    AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                        context,
                        widthDp,
                    )
                )
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                )
                val retryHandler = Handler(Looper.getMainLooper())
                val retryLoad = Runnable { loadAd(AdRequest.Builder().build()) }
                setTag(retryHandler)
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        diagnostics.bannerLoaded.value = true
                        retryHandler.removeCallbacks(retryLoad)
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        android.util.Log.w("BannerAd", "load failed: " + error.message)
                        diagnostics.bannerLoaded.value = false
                        diagnostics.lastBannerError.value =
                            "code " + error.code + ": " + error.message
                        retryHandler.removeCallbacks(retryLoad)
                        retryHandler.postDelayed(retryLoad, BANNER_RETRY_DELAY_MS)
                    }
                }
                loadAd(AdRequest.Builder().build())
            }
        },
        modifier = modifier.fillMaxWidth().heightIn(min = 50.dp),
        onRelease = { view ->
            (view.tag as? Handler)?.removeCallbacksAndMessages(null)
            view.destroy()
        },
    )
}
