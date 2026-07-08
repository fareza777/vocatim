package com.vocatim.app.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vocatim.app.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Friendly display name for a whisper model id — no code-y ids in the UI. */
@Composable
fun modelDisplayName(id: String): String = when (id) {
    "tiny" -> stringResource(R.string.model_name_tiny)
    "base" -> stringResource(R.string.model_name_base)
    "small-q5_1" -> stringResource(R.string.model_name_small_q5)
    "small" -> stringResource(R.string.model_name_small)
    "large-v3-turbo-q5_0" -> stringResource(R.string.model_name_turbo)
    else -> id
}

fun formatClock(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = totalSeconds % 3600 / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun formatDate(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(epochMs))

/**
 * Turns a transcript title into a filename document providers accept.
 * Auto-generated titles carry ellipses, quotes, slashes, etc. — several
 * providers (notably MIUI's) reject those and the SAF dialog silently fails.
 */
fun exportFileName(title: String, extension: String): String {
    val safe = title
        .replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ")
        .replace("…", "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(60)
        .trim('.', ' ')
        .ifBlank { "vocatim" }
    return "$safe.$extension"
}
