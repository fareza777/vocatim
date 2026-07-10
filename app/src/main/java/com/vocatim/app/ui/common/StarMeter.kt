package com.vocatim.app.ui.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * "Speed ★★★★☆" — the one-glance rating used in model pickers so nobody
 * has to decode parameter counts or quantization suffixes.
 */
@Composable
fun StarMeter(label: String, stars: Int) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                    append("★".repeat(stars.coerceIn(0, 5)))
                }
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.outlineVariant)) {
                    append("★".repeat(5 - stars.coerceIn(0, 5)))
                }
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
