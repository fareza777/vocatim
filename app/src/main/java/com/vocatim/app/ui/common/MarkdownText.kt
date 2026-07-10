package com.vocatim.app.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Lightweight Markdown renderer for LLM output (summaries, minutes): headings,
 * bullet/numbered lists, and inline bold/italic. Line-based on purpose — LLM
 * Markdown is simple and a full parser dependency isn't warranted.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        markdown.lines().forEach { raw ->
            val line = raw.trimEnd()
            val trimmed = line.trimStart()
            when {
                trimmed.isEmpty() -> Spacer(Modifier.height(6.dp))
                trimmed.startsWith("### ") -> Text(
                    inlineMarkdown(trimmed.removePrefix("### ")),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                trimmed.startsWith("## ") -> Text(
                    inlineMarkdown(trimmed.removePrefix("## ")),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
                trimmed.startsWith("# ") -> Text(
                    inlineMarkdown(trimmed.removePrefix("# ")),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 6.dp),
                )
                BULLET_PREFIXES.any { trimmed.startsWith(it) } -> BulletLine(
                    inlineMarkdown(trimmed.substring(2).trimStart()), style
                )
                NUMBERED.matches(trimmed) -> {
                    val dot = trimmed.indexOf('.')
                    BulletLine(
                        inlineMarkdown(trimmed.substring(dot + 1).trimStart()),
                        style,
                        marker = trimmed.substring(0, dot + 1),
                    )
                }
                else -> Text(inlineMarkdown(trimmed), style = style)
            }
        }
    }
}

@Composable
private fun BulletLine(text: AnnotatedString, style: TextStyle, marker: String = "•") {
    Row {
        Text(
            marker,
            style = style,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = style)
    }
}

/** Inline `**bold**` and `*italic*` spans. */
private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i]); i++
                }
            }
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }
            else -> {
                append(text[i]); i++
            }
        }
    }
}

private val BULLET_PREFIXES = listOf("- ", "* ", "• ")
private val NUMBERED = Regex("^\\d{1,2}\\.\\s.*")
