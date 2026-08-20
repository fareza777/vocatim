package com.vocatim.app.ads

import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.vocatim.app.BuildConfig

/**
 * Anchored adaptive banner.
 *
 * Renders nothing at all when [visible] is false — a paid user should not see
 * an empty reserved strip where an ad used to be, and should not cause an ad
 * request either. Offline the view simply never fills, which is why the height
 * is left to the ad view rather than reserved up front.
 */
@Composable
fun BannerAd(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    val context = LocalContext.current

    val adView = remember {
        AdView(context).apply {
            adUnitId = BuildConfig.ADMOB_BANNER_UNIT_ID
            setAdSize(
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                    context,
                    context.resources.configuration.screenWidthDp,
                )
            )
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            )
            loadAd(AdRequest.Builder().build())
        }
    }

    // AdView holds a WebView; leaking it leaks a renderer process.
    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }

    AndroidView(
        factory = { adView },
        modifier = modifier.fillMaxWidth(),
    )
}
