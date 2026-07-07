package com.vocatim.app.ui.common

import java.text.DateFormat
import java.util.Date
import java.util.Locale

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
