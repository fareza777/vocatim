package com.vocatim.app.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vocatim.app.R
import com.vocatim.app.ui.theme.BrandGradient
import kotlinx.coroutines.delay

/**
 * Branded cold-start splash: rippling rings, the mic mark springing in, and
 * the wordmark rising. Dark regardless of theme so it continues seamlessly
 * from the system splash window, then fades into the app.
 */
@Composable
fun AnimatedSplash(onFinished: () -> Unit) {
    val logoScale = remember { Animatable(0.4f) }
    val logoAlpha = remember { Animatable(0f) }
    val textRise = remember { Animatable(24f) }
    val textAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        logoAlpha.animateTo(1f, tween(250))
    }
    LaunchedEffect(Unit) {
        logoScale.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        )
    }
    LaunchedEffect(Unit) {
        delay(350)
        textAlpha.animateTo(1f, tween(450))
    }
    LaunchedEffect(Unit) {
        delay(350)
        textRise.animateTo(0f, tween(450))
        delay(900)
        onFinished()
    }

    // Sonar ripple expanding behind the logo.
    val ripple = rememberInfiniteTransition(label = "ripple")
    val rippleProgress by ripple.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "rippleProgress",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SPLASH_INK),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2 - 40)
            // Two staggered rings; each grows and fades out.
            for (phase in 0..1) {
                val p = ((rippleProgress + phase * 0.5f) % 1f)
                drawCircle(
                    color = RING_TEAL.copy(alpha = (1f - p) * 0.35f),
                    radius = 70.dp.toPx() + p * 130.dp.toPx(),
                    center = center,
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .scale(logoScale.value)
                    .alpha(logoAlpha.value)
                    .clip(CircleShape)
                    .background(BrandGradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(48.dp),
                )
            }
            Spacer(Modifier.height(28.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .alpha(textAlpha.value)
                    .offset(y = textRise.value.dp),
            ) {
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.home_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9AA0B4),
                )
            }
        }
    }
}

/** Matches windowSplashScreenBackground in themes.xml. */
private val SPLASH_INK = Color(0xFF0B0D14)
private val RING_TEAL = Color(0xFF2DD4BF)
