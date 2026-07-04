package com.vocatim.app.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocatim.app.R
import com.vocatim.app.data.db.TranscriptEntity
import com.vocatim.app.data.db.TranscriptStatus
import com.vocatim.app.ui.common.Pill
import com.vocatim.app.ui.common.formatClock
import com.vocatim.app.ui.common.formatDate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    onUpgrade: () -> Unit = {},
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val transcript by viewModel.transcript.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val editedText by viewModel.editedText.collectAsStateWithLifecycle()
    val exportEvent by viewModel.exportEvent.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val waveform by viewModel.waveform.collectAsStateWithLifecycle()
    val segments by viewModel.segments.collectAsStateWithLifecycle()
    val textScale by viewModel.textScale.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var renameDraft by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(exportEvent) {
        exportEvent?.let { event ->
            val message = when (event) {
                is ExportEvent.Success -> context.getString(R.string.export_success)
                is ExportEvent.Failure ->
                    context.getString(R.string.export_failed, event.message)
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.consumeExportEvent()
        }
    }

    val exportTxtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let(viewModel::exportTxt) }
    val exportSrtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-subrip")
    ) { uri -> uri?.let(viewModel::exportSrt) }
    val exportVttLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/vtt")
    ) { uri -> uri?.let(viewModel::exportVtt) }
    val exportMdLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri -> uri?.let { viewModel.exportMarkdown(it, withTimestamps = true) } }
    val exportPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let(viewModel::exportPdf) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        transcript?.title ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable {
                            renameDraft = transcript?.title ?: ""
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        val t = transcript ?: return@Scaffold
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            MetaRow(t)
            TagRow(current = t.tag, onSelect = viewModel::setTag)

            when (t.status) {
                TranscriptStatus.DONE -> {
                    if (t.audioPath != null) {
                        LaunchedEffect(t.audioPath) { viewModel.loadWaveform() }
                        PlayerCard(
                            state = playerState,
                            waveform = waveform,
                            durationMs = t.audioDurationMs,
                            onToggle = viewModel::togglePlayback,
                            onSeek = viewModel::seekToFraction,
                        )
                    }

                    var segmentMode by remember { mutableStateOf(false) }
                    ViewModeToggle(
                        segmentMode = segmentMode,
                        onChange = { mode ->
                            segmentMode = mode
                            if (mode) viewModel.loadSegments()
                        },
                    )

                    if (segmentMode) {
                        SegmentList(
                            segments = segments,
                            positionMs = playerState?.positionMs?.toLong() ?: -1L,
                            canSeek = t.audioPath != null,
                            textScale = textScale,
                            onSegmentClick = { viewModel.playFromMs(it) },
                            onCopySegment = { text ->
                                copyToClipboard(context, text)
                                Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                            },
                        )
                    } else {
                        OutlinedTextField(
                            value = editedText ?: t.text,
                            onValueChange = viewModel::onTextChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 240.dp),
                            placeholder = { Text(stringResource(R.string.detail_empty_text)) },
                            shape = MaterialTheme.shapes.large,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = MaterialTheme.typography.bodyLarge.fontSize * textScale,
                                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * textScale,
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            ),
                        )
                    }
                    if (editedText != null && editedText != t.text) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = viewModel::saveEdits) {
                                Text(stringResource(R.string.action_save))
                            }
                            OutlinedButton(onClick = viewModel::discardEdits) {
                                Text(stringResource(R.string.action_discard))
                            }
                        }
                    }
                    ActionGrid(
                        onCopy = {
                            copyToClipboard(context, viewModel.currentText())
                            Toast.makeText(
                                context,
                                context.getString(R.string.copied),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        onCopyTimestamps = {
                            viewModel.copyWithTimestampsAsync { text ->
                                copyToClipboard(context, text)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.copied),
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        onShare = { shareText(context, viewModel.currentText()) },
                        onExportTxt = { exportTxtLauncher.launch(t.title + ".txt") },
                        onExportSrt = { exportSrtLauncher.launch(t.title + ".srt") },
                        onExportVtt = { exportVttLauncher.launch(t.title + ".vtt") },
                        onExportMd = { exportMdLauncher.launch(t.title + ".md") },
                        onExportPdf = { exportPdfLauncher.launch(t.title + ".pdf") },
                    )
                    if (t.audioPath != null) {
                        TextButton(onClick = viewModel::deleteAudioOnly) {
                            Text(
                                stringResource(R.string.action_delete_audio),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.padding(bottom = 12.dp))
                }
                TranscriptStatus.FAILED -> {
                    StatusCard {
                        when (t.errorMessage) {
                            "QUOTA_EXCEEDED" -> {
                                Text(
                                    stringResource(R.string.detail_quota_exceeded),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Button(onClick = onUpgrade) {
                                    Text(stringResource(R.string.quota_banner_cta))
                                }
                                if (t.audioPath != null) {
                                    Text(
                                        stringResource(R.string.detail_quota_audio_kept),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            "CANCELLED" -> {
                                Text(
                                    stringResource(R.string.detail_cancelled),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Button(onClick = viewModel::retry, enabled = t.audioPath != null) {
                                    Text(stringResource(R.string.action_retry))
                                }
                            }
                            else -> {
                                Text(
                                    stringResource(R.string.detail_failed, t.errorMessage ?: "?"),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Button(onClick = viewModel::retry, enabled = t.audioPath != null) {
                                    Text(stringResource(R.string.action_retry))
                                }
                            }
                        }
                    }
                    if (t.text.isNotBlank()) {
                        Text(t.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                else -> {
                    val p = progress
                    StatusCard {
                        Pill(
                            when {
                                p?.converting == true -> stringResource(R.string.status_converting)
                                p != null -> stringResource(R.string.status_transcribing)
                                else -> stringResource(R.string.status_queued)
                            },
                            color = MaterialTheme.colorScheme.secondary,
                            background = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                            dot = true,
                        )
                        if (p != null && p.fraction > 0f) {
                            LinearProgressIndicator(
                                progress = { p.fraction },
                                modifier = Modifier.fillMaxWidth(),
                                trackColor = MaterialTheme.colorScheme.outlineVariant,
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                trackColor = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        p?.etaMs?.let { eta ->
                            Text(
                                stringResource(R.string.detail_eta, formatClock(eta)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = viewModel::cancel) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                    // Streaming partial text as chunks land.
                    if (t.text.isNotBlank()) {
                        Text(t.text, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    renameDraft?.let { draft ->
        AlertDialog(
            onDismissRequest = { renameDraft = null },
            title = { Text(stringResource(R.string.rename_title)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { renameDraft = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.rename(draft)
                    renameDraft = null
                }) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameDraft = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.delete(onDeleted = onBack)
                }) {
                    Text(
                        stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun PlayerCard(
    state: PlayerState?,
    waveform: FloatArray?,
    durationMs: Long,
    onToggle: () -> Unit,
    onSeek: (Float) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconButton(onClick = onToggle) {
                Icon(
                    if (state?.playing == true) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.player_toggle),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            PlaybackWaveform(
                waveform = waveform,
                progress = if (state != null && state.durationMs > 0) {
                    state.positionMs.toFloat() / state.durationMs
                } else 0f,
                onSeek = onSeek,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
            )
            Text(
                formatClock(state?.positionMs?.toLong() ?: durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaybackWaveform(
    waveform: FloatArray?,
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val played = MaterialTheme.colorScheme.primary
    val remaining = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                onSeek(offset.x / size.width)
            }
        }
    ) {
        val bars = waveform ?: FloatArray(64) { 0.4f }
        // Normalize so quiet recordings still show shape.
        val max = bars.maxOrNull()?.takeIf { it > 0f } ?: 1f
        val barWidth = size.width / bars.size
        val center = size.height / 2
        bars.forEachIndexed { i, raw ->
            val amp = (raw / max).coerceIn(0.08f, 1f)
            val h = amp * size.height
            val x = i * barWidth + barWidth / 2
            drawLine(
                color = if (i.toFloat() / bars.size <= progress) played else remaining,
                start = Offset(x, center - h / 2),
                end = Offset(x, center + h / 2),
                strokeWidth = barWidth * 0.55f,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun ViewModeToggle(segmentMode: Boolean, onChange: (Boolean) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = !segmentMode,
            onClick = { onChange(false) },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
        ) { Text(stringResource(R.string.detail_mode_text)) }
        SegmentedButton(
            selected = segmentMode,
            onClick = { onChange(true) },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
        ) { Text(stringResource(R.string.detail_mode_segments)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagRow(current: String?, onSelect: (String?) -> Unit) {
    val tags = listOf(null, "work", "study", "interview", "personal", "other")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tags.forEach { tag ->
            androidx.compose.material3.FilterChip(
                selected = current == tag,
                onClick = { onSelect(tag) },
                label = {
                    Text(
                        if (tag == null) stringResource(R.string.tag_none)
                        else tagLabel(tag)
                    )
                },
            )
        }
    }
}

@Composable
private fun tagLabel(tag: String): String = when (tag) {
    "work" -> stringResource(R.string.tag_work)
    "study" -> stringResource(R.string.tag_study)
    "interview" -> stringResource(R.string.tag_interview)
    "personal" -> stringResource(R.string.tag_personal)
    else -> stringResource(R.string.tag_other)
}

@Composable
private fun SegmentList(
    segments: List<com.vocatim.app.data.db.SegmentEntity>,
    positionMs: Long,
    canSeek: Boolean,
    textScale: Float,
    onSegmentClick: (Long) -> Unit,
    onCopySegment: (String) -> Unit,
) {
    val scrollState = rememberScrollState()
    val activeIndex = segments.indexOfFirst { positionMs in it.startMs until it.endMs }
    LaunchedEffect(activeIndex, segments.size) {
        if (activeIndex >= 0 && segments.isNotEmpty()) {
            scrollState.animateScrollTo((activeIndex * 72).coerceAtMost(scrollState.maxValue))
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        segments.forEachIndexed { index, segment ->
            val active = positionMs in segment.startMs until segment.endMs
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = canSeek) { onSegmentClick(segment.startMs) },
                shape = MaterialTheme.shapes.medium,
                color = if (active) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                border = if (active) {
                    androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    )
                } else null,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatClock(segment.startMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        segment.text.trim(),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = MaterialTheme.typography.bodyMedium.fontSize * textScale,
                            lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * textScale,
                            fontWeight = if (active) androidx.compose.ui.text.font.FontWeight.SemiBold
                            else androidx.compose.ui.text.font.FontWeight.Medium,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onCopySegment(segment.text.trim()) }) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.action_copy_segment),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun MetaRow(t: TranscriptEntity) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(formatDate(t.createdAt))
        if (t.audioDurationMs > 0) Pill(formatClock(t.audioDurationMs))
        Pill(t.modelId)
        if (t.language == "auto" && t.detectedLanguage != null) {
            Pill(
                java.util.Locale(t.detectedLanguage).let { locale ->
                    locale.getDisplayLanguage(locale)
                        .replaceFirstChar { it.uppercase() }
                },
                color = MaterialTheme.colorScheme.secondary,
                background = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
            )
        }
        if (t.status == TranscriptStatus.DONE && t.audioDurationMs > 0 && t.processingTimeMs > 0) {
            Pill(
                String.format(
                    Locale.US, "%.2fx", t.processingTimeMs.toFloat() / t.audioDurationMs
                )
            )
        }
    }
}

@Composable
private fun ActionGrid(
    onCopy: () -> Unit,
    onCopyTimestamps: () -> Unit,
    onShare: () -> Unit,
    onExportTxt: () -> Unit,
    onExportSrt: () -> Unit,
    onExportVtt: () -> Unit,
    onExportMd: () -> Unit,
    onExportPdf: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionButton(
            Icons.Default.ContentCopy, stringResource(R.string.action_copy), onCopy,
            Modifier.weight(1f),
        )
        ActionButton(
            Icons.Default.Subtitles, stringResource(R.string.action_copy_timestamps), onCopyTimestamps,
            Modifier.weight(1f),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionButton(
            Icons.Default.Share, stringResource(R.string.action_share), onShare,
            Modifier.weight(1f),
        )
        ActionButton(
            Icons.Default.Description, stringResource(R.string.action_export_txt), onExportTxt,
            Modifier.weight(1f),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionButton(
            Icons.Default.Subtitles, stringResource(R.string.action_export_srt), onExportSrt,
            Modifier.weight(1f),
        )
        ActionButton(
            Icons.Default.Subtitles, stringResource(R.string.action_export_vtt), onExportVtt,
            Modifier.weight(1f),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionButton(
            Icons.Default.Description, stringResource(R.string.action_export_md), onExportMd,
            Modifier.weight(1f),
        )
        ActionButton(
            Icons.Default.Description, stringResource(R.string.action_export_pdf), onExportPdf,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = null, modifier = Modifier.width(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, maxLines = 1)
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Vocatim", text))
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.action_share))
    )
}
