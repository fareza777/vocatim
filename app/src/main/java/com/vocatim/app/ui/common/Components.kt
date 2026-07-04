package com.vocatim.app.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/** Small rounded status/metadata chip used across list items and headers. */
@Composable
fun Pill(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    background: Color = MaterialTheme.colorScheme.surfaceVariant,
    dot: Boolean = false,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Compact waveform strip for list cards and previews. */
@Composable
fun MiniWaveform(
    peaks: FloatArray?,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 28.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    idleColor: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val bars = peaks ?: FloatArray(24) { 0.15f + (it % 5) * 0.05f }
        val max = bars.maxOrNull()?.takeIf { it > 0f } ?: 1f
        val barWidth = size.width / bars.size
        val center = size.height / 2
        bars.forEachIndexed { i, raw ->
            val amp = (raw / max).coerceIn(0.06f, 1f)
            val h = amp * size.height
            val x = i * barWidth + barWidth / 2
            drawLine(
                color = if (peaks != null) color else idleColor,
                start = Offset(x, center - h / 2),
                end = Offset(x, center + h / 2),
                strokeWidth = barWidth * 0.5f,
                cap = StrokeCap.Round,
            )
        }
    }
}
