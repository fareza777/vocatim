package com.vocatim.app.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vocatim.app.R
import kotlin.math.roundToInt

/** One spotlight stop: which registered target to highlight and what to say. */
data class TourStep(val targetKey: String, val title: String, val body: String)

/** Registers this element as a spotlight target under [key]. */
fun Modifier.tourTarget(targets: MutableMap<String, Rect>, key: String): Modifier =
    onGloballyPositioned { targets[key] = it.boundsInRoot() }

/**
 * Full-screen scrim with a punched-out, pulsing hole over the current step's
 * target and an explanation card on the emptier half of the screen. Tapping
 * anywhere advances the tour.
 */
@Composable
fun TourOverlay(
    steps: List<TourStep>,
    stepIndex: Int,
    targets: Map<String, Rect>,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val step = steps.getOrNull(stepIndex) ?: return
    val target = targets[step.targetKey] ?: return
    val density = LocalDensity.current

    val pulse by rememberInfiniteTransition(label = "tourPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
        label = "tourPulseValue",
    )

    val holePadding = with(density) { 8.dp.toPx() }
    val hole = Rect(
        left = target.left - holePadding,
        top = target.top - holePadding,
        right = target.right + holePadding,
        bottom = target.bottom + holePadding,
    )
    val corner = minOf(hole.width, hole.height) / 2

    var rootHeightPx by remember { mutableIntStateOf(0) }
    var cardHeightPx by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { rootHeightPx = it.height }
            // Consume every tap; a tap anywhere moves the tour forward.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onNext,
            ),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                // Offscreen compositing so BlendMode.Clear punches a real hole.
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
        ) {
            drawRect(Color.Black.copy(alpha = 0.72f))
            drawRoundRect(
                color = Color.Transparent,
                topLeft = Offset(hole.left, hole.top),
                size = Size(hole.width, hole.height),
                cornerRadius = CornerRadius(corner),
                blendMode = BlendMode.Clear,
            )
        }
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Expanding ring draws the eye to the highlighted control.
            val grow = 10.dp.toPx() * pulse
            drawRoundRect(
                color = Color.White.copy(alpha = (1f - pulse) * 0.8f),
                topLeft = Offset(hole.left - grow, hole.top - grow),
                size = Size(hole.width + grow * 2, hole.height + grow * 2),
                cornerRadius = CornerRadius(corner + grow),
                style = Stroke(width = 2.dp.toPx()),
            )
        }

        // Card below the hole when the target sits in the top half, above it
        // otherwise. Height is measured, so the position settles immediately.
        val marginPx = with(density) { 20.dp.toPx() }
        val cardY = if (hole.center.y < rootHeightPx / 2f) {
            (hole.bottom + marginPx).roundToInt()
        } else {
            (hole.top - marginPx - cardHeightPx).roundToInt().coerceAtLeast(0)
        }
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, cardY) }
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .onSizeChanged { cardHeightPx = it.height },
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(step.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    step.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${stepIndex + 1}/${steps.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onSkip) {
                        Text(stringResource(R.string.tour_skip))
                    }
                    Button(onClick = onNext) {
                        Text(
                            stringResource(
                                if (stepIndex == steps.size - 1) R.string.tour_done
                                else R.string.tour_next
                            )
                        )
                    }
                }
            }
        }
    }
}
