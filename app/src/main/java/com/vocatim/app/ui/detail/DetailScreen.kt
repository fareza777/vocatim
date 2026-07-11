package com.vocatim.app.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocatim.app.R
import com.vocatim.app.data.db.TranscriptEntity
import com.vocatim.app.data.db.TranscriptStatus
import com.vocatim.app.data.model.ModelState
import com.vocatim.app.data.summary.SummaryModel
import com.vocatim.app.ui.common.Pill
import com.vocatim.app.ui.common.exportFileName
import com.vocatim.app.ui.common.formatClock
import com.vocatim.app.ui.common.formatDate
import java.util.Locale

/** Content choices for TXT/MD/PDF export. */
const val EXPORT_SOURCE_TRANSCRIPT = "transcript"
const val EXPORT_SOURCE_MINUTES = "minutes"
const val EXPORT_SOURCE_SUMMARY = "summary"

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
    var overflowOpen by remember { mutableStateOf(false) }
    val cloudConfiguredTop by viewModel.cloudConfigured.collectAsStateWithLifecycle()
    var keyPointsDialog by remember { mutableStateOf<List<String>?>(null) }
    var showMergeDialog by remember { mutableStateOf(false) }
    var showAskAiDialog by remember { mutableStateOf(false) }
    var showTranslateDialog by remember { mutableStateOf(false) }
    val isMinutesNote =
        transcript?.modelId == com.vocatim.app.service.SummaryService.MODEL_ID_MINUTES
    // Cloud AI (summaries + minutes) is a paid Pro feature.
    val isProTop by viewModel.isPro.collectAsStateWithLifecycle()
    val summaryModelStateTop by viewModel.summaryModelState.collectAsStateWithLifecycle()
    // Minutes run on cloud when configured, else on the local model.
    val minutesEngineReady = cloudConfiguredTop ||
        summaryModelStateTop is com.vocatim.app.data.model.ModelState.Downloaded
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

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

    // Which content the next TXT/MD/PDF export carries.
    var exportSource by remember { mutableStateOf(EXPORT_SOURCE_TRANSCRIPT) }
    val exportTxtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> uri?.let { viewModel.exportTxt(it, exportSource) } }
    val exportSrtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/x-subrip")
    ) { uri -> uri?.let(viewModel::exportSrt) }
    val exportVttLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/vtt")
    ) { uri -> uri?.let(viewModel::exportVtt) }
    val exportMdLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        uri?.let {
            viewModel.exportMarkdown(
                it,
                withTimestamps = exportSource == EXPORT_SOURCE_TRANSCRIPT,
                source = exportSource,
            )
        }
    }
    val exportPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri -> uri?.let { viewModel.exportPdf(it, exportSource) } }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
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
                    if (transcript?.status == TranscriptStatus.DONE) {
                        Box {
                            IconButton(onClick = { overflowOpen = true }) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.action_more),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            androidx.compose.material3.DropdownMenu(
                                expanded = overflowOpen,
                                onDismissRequest = { overflowOpen = false },
                            ) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_key_points)) },
                                    onClick = {
                                        overflowOpen = false
                                        keyPointsDialog = viewModel.keyPoints()
                                    },
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_add_calendar)) },
                                    onClick = {
                                        overflowOpen = false
                                        transcript?.let { addToCalendar(context, it) }
                                    },
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(
                                                if (transcript?.locked == true) R.string.action_unlock_note
                                                else R.string.action_lock_note
                                            )
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Lock, contentDescription = null)
                                    },
                                    onClick = {
                                        overflowOpen = false
                                        viewModel.setLocked(transcript?.locked != true)
                                    },
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_share_pdf)) },
                                    onClick = {
                                        overflowOpen = false
                                        // Same gate as PDF export: sharing IS
                                        // an export, just via a different door.
                                        if (!isProTop) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.export_pdf_pro),
                                                Toast.LENGTH_LONG,
                                            ).show()
                                            onUpgrade()
                                        } else {
                                            viewModel.sharePdf { uri ->
                                                shareStream(context, uri, "application/pdf")
                                            }
                                        }
                                    },
                                )
                                if (transcript?.audioPath != null) {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_share_audio)) },
                                        onClick = {
                                            overflowOpen = false
                                            viewModel.shareAudioUri()?.let {
                                                shareStream(context, it, "audio/*")
                                            }
                                        },
                                    )
                                }
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_merge)) },
                                    onClick = {
                                        overflowOpen = false
                                        showMergeDialog = true
                                    },
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_ask_ai)) },
                                    onClick = {
                                        overflowOpen = false
                                        when {
                                            !isProTop -> {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.cloud_need_pro),
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                                onUpgrade()
                                            }
                                            // Cloud when configured; otherwise
                                            // the local model answers on-device.
                                            cloudConfiguredTop || summaryModelStateTop
                                                is com.vocatim.app.data.model.ModelState.Downloaded ->
                                                showAskAiDialog = true
                                            else -> Toast.makeText(
                                                context,
                                                context.getString(R.string.minutes_need_byok),
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    },
                                )
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_translate)) },
                                    onClick = {
                                        overflowOpen = false
                                        when {
                                            !isProTop -> {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.cloud_need_pro),
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                                onUpgrade()
                                            }
                                            cloudConfiguredTop -> showTranslateDialog = true
                                            else -> Toast.makeText(
                                                context,
                                                context.getString(R.string.minutes_need_byok),
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    },
                                )
                                if (!isMinutesNote) androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_minutes)) },
                                    onClick = {
                                        overflowOpen = false
                                        when {
                                            !isProTop -> {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.cloud_need_pro),
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                                onUpgrade()
                                            }
                                            minutesEngineReady -> {
                                                viewModel.createMinutes()
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.minutes_started),
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                            else -> Toast.makeText(
                                                context,
                                                context.getString(R.string.minutes_need_engine),
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    },
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
        },
    ) { padding ->
        val t = transcript ?: return@Scaffold
        var unlocked by rememberSaveable(t.id) { mutableStateOf(false) }
        if (t.locked && !unlocked) {
            LockedNoteGate(
                modifier = Modifier.padding(padding),
                onUnlock = { promptUnlock(context) { unlocked = true } },
            )
            return@Scaffold
        }
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
            val folders by viewModel.folders.collectAsStateWithLifecycle()
            FolderRow(current = t.tag, folders = folders, onSelect = viewModel::setTag)

            when (t.status) {
                TranscriptStatus.DONE -> {
                    // A minutes note is AI output, not a recording: no summarize
                    // card, no timestamp exports, no segment view.
                    val isMinutes =
                        t.modelId == com.vocatim.app.service.SummaryService.MODEL_ID_MINUTES
                    val hasAudio = t.audioPath != null
                    var exportPickerFormat by remember { mutableStateOf<String?>(null) }

                    // One page of uniform collapsible cards, all starting
                    // closed: minutes -> AI summary -> transcript -> audio
                    // -> export.
                    if (!isMinutes) {
                        t.minutes?.let { minutesText ->
                            SectionCard(
                                title = stringResource(R.string.detail_minutes_section),
                                icon = Icons.Default.Description,
                                iconTint = MaterialTheme.colorScheme.tertiary,
                                badge = stringResource(R.string.card_badge_ai),
                            ) {
                                androidx.compose.foundation.text.selection.SelectionContainer {
                                    com.vocatim.app.ui.common.MarkdownText(minutesText)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(onClick = {
                                        copyToClipboard(context, minutesText)
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.copied),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }) { Text(stringResource(R.string.action_copy)) }
                                    OutlinedButton(onClick = { shareText(context, minutesText) }) {
                                        Text(stringResource(R.string.action_share))
                                    }
                                    OutlinedButton(onClick = {
                                        if (isProTop && minutesEngineReady) {
                                            viewModel.createMinutes()
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.minutes_started),
                                                Toast.LENGTH_LONG,
                                            ).show()
                                        }
                                    }) { Text(stringResource(R.string.summary_regenerate)) }
                                }
                            }
                        }
                        SectionCard(
                            title = stringResource(R.string.summary_title),
                            icon = Icons.Default.AutoAwesome,
                            badge = if (t.summary != null) {
                                stringResource(
                                    if (t.summarySource ==
                                        com.vocatim.app.service.SummaryService.SUMMARY_SOURCE_CLOUD
                                    ) R.string.summary_cloud_badge
                                    else R.string.summary_offline_badge
                                )
                            } else null,
                        ) {
                            SummaryBody(
                                summary = t.summary,
                                viewModel = viewModel,
                                onUpgrade = onUpgrade,
                            )
                        }
                    }

                    var searchQuery by rememberSaveable(t.id) { mutableStateOf("") }
                    var readingMode by rememberSaveable(t.id) { mutableStateOf(false) }

                    SectionCard(
                        title = stringResource(
                            if (isMinutes) R.string.detail_minutes_section
                            else R.string.detail_transcript_section
                        ),
                        icon = Icons.Default.Subtitles,
                        // The transcript is what the user came for; open it.
                        initiallyExpanded = true,
                    ) {
                        var segmentMode by remember { mutableStateOf(false) }
                        if (t.text.isNotBlank()) {
                            TextButton(onClick = { readingMode = true }) {
                                Icon(
                                    Icons.Default.Fullscreen,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.detail_reading_mode))
                            }
                        }
                        if (hasAudio) {
                            ViewModeToggle(
                                segmentMode = segmentMode,
                                onChange = { mode ->
                                    segmentMode = mode
                                    if (mode) viewModel.loadSegments()
                                },
                            )
                        }

                        // Find-in-transcript (text mode only).
                        if (!(hasAudio && segmentMode)) {
                            TranscriptSearchField(
                                query = searchQuery,
                                onQuery = { searchQuery = it },
                                matches = if (searchQuery.isNotBlank()) {
                                    countMatches(editedText ?: t.text, searchQuery)
                                } else 0,
                            )
                        }

                        if (hasAudio && segmentMode) {
                            var showSpeakers by rememberSaveable(t.id) { mutableStateOf(false) }
                            val diarizeProgress by viewModel.diarizationProgress
                                .collectAsStateWithLifecycle()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                androidx.compose.material3.FilterChip(
                                    selected = showSpeakers,
                                    onClick = { showSpeakers = !showSpeakers },
                                    label = { Text(stringResource(R.string.detail_speakers)) },
                                )
                                // Real diarization (AI, Pro) replaces the
                                // pause-based estimate once it has run.
                                TextButton(
                                    enabled = diarizeProgress == null,
                                    onClick = {
                                        when {
                                            !isProTop -> {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.ai_need_pro),
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                                onUpgrade()
                                            }
                                            !viewModel.diarizeModelReady -> Toast.makeText(
                                                context,
                                                context.getString(R.string.diarize_need_model),
                                                Toast.LENGTH_LONG,
                                            ).show()
                                            else -> {
                                                viewModel.startDiarization()
                                                showSpeakers = true
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.diarize_started),
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                        }
                                    },
                                ) {
                                    Text(stringResource(R.string.diarize_action))
                                }
                            }
                            if (diarizeProgress != null) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth(),
                                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                            SegmentList(
                                segments = segments,
                                positionMs = playerState?.positionMs?.toLong() ?: -1L,
                                canSeek = t.audioPath != null,
                                textScale = textScale,
                                showSpeakers = showSpeakers,
                                onSegmentClick = { viewModel.playFromMs(it) },
                                onCopySegment = { text ->
                                    copyToClipboard(context, text)
                                    Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
                                },
                            )
                        } else if (searchQuery.isNotBlank()) {
                            // Read-only highlighted view while searching.
                            androidx.compose.foundation.text.selection.SelectionContainer {
                                Text(
                                    highlightMatches(
                                        editedText ?: t.text,
                                        searchQuery,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                                    ),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = MaterialTheme.typography.bodyLarge.fontSize * textScale,
                                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * textScale,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
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
                        if (searchQuery.isBlank() && editedText != null && editedText != t.text) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = viewModel::saveEdits) {
                                    Text(stringResource(R.string.action_save))
                                }
                                OutlinedButton(onClick = viewModel::discardEdits) {
                                    Text(stringResource(R.string.action_discard))
                                }
                            }
                        }
                    }

                    if (hasAudio) {
                        SectionCard(
                            title = stringResource(R.string.detail_audio_section),
                            icon = Icons.Default.GraphicEq,
                            initiallyExpanded = true,
                        ) {
                            LaunchedEffect(t.audioPath) { viewModel.loadWaveform() }
                            val playbackSpeed by viewModel.playbackSpeed.collectAsStateWithLifecycle()
                            val skipSilence by viewModel.skipSilence.collectAsStateWithLifecycle()
                            PlayerCard(
                                state = playerState,
                                waveform = waveform,
                                durationMs = t.audioDurationMs,
                                speed = playbackSpeed,
                                skipSilence = skipSilence,
                                onToggle = viewModel::togglePlayback,
                                onSeek = viewModel::seekToFraction,
                                onSpeedClick = viewModel::cyclePlaybackSpeed,
                                onSkipSilenceClick = viewModel::toggleSkipSilence,
                            )
                            val markerTimes = remember(t.markers) {
                                t.markers?.split(",")
                                    ?.mapNotNull { it.trim().toLongOrNull() }
                                    ?.sorted()
                                    .orEmpty()
                            }
                            if (markerTimes.isNotEmpty()) {
                                MarkersRow(markerTimes) { viewModel.playFromMs(it) }
                            }
                            TextButton(onClick = viewModel::deleteAudioOnly) {
                                Text(
                                    stringResource(R.string.action_delete_audio),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Photos live in their own card so the top of the page
                    // stays about the words, not the attachments.
                    val attachments by viewModel.attachments.collectAsStateWithLifecycle()
                    SectionCard(
                        title = stringResource(R.string.detail_photos_section),
                        icon = Icons.Default.Image,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        badge = attachments.size.takeIf { it > 0 }?.toString(),
                    ) {
                        PhotoRow(
                            attachments = attachments,
                            onAdd = viewModel::addPhoto,
                            onRemove = viewModel::removePhoto,
                        )
                    }

                    SectionCard(
                        title = stringResource(R.string.detail_export_section),
                        icon = Icons.Default.Share,
                        // Quiet upsell at the moment of highest intent.
                        badge = if (!isProTop) stringResource(R.string.export_badge_pro) else null,
                    ) {
                        // TXT/MD/PDF can carry the transcript, the minutes, or
                        // the AI summary — ask which when there is a choice.
                        val multiSource = t.minutes != null || t.summary != null
                        fun startExport(format: String) {
                            if (multiSource) {
                                exportPickerFormat = format
                            } else {
                                exportSource = EXPORT_SOURCE_TRANSCRIPT
                                runCatching {
                                    when (format) {
                                        "txt" -> exportTxtLauncher.launch(exportFileName(t.title, "txt"))
                                        "md" -> exportMdLauncher.launch(exportFileName(t.title, "md"))
                                        else -> exportPdfLauncher.launch(exportFileName(t.title, "pdf"))
                                    }
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
                            onExportTxt = { startExport("txt") },
                            onExportSrt = {
                                runCatching { exportSrtLauncher.launch(exportFileName(t.title, "srt")) }
                            },
                            onExportVtt = {
                                runCatching { exportVttLauncher.launch(exportFileName(t.title, "vtt")) }
                            },
                            onExportMd = { startExport("md") },
                            // PDF is a Pro perk; free plan keeps TXT/SRT/VTT/MD.
                            onExportPdf = {
                                if (isProTop) startExport("pdf")
                                else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.export_pdf_pro),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                    onUpgrade()
                                }
                            },
                            showTimestamped = hasAudio,
                        )
                    }

                    exportPickerFormat?.let { format ->
                        ExportSourceDialog(
                            hasMinutes = t.minutes != null,
                            hasSummary = t.summary != null,
                            isPro = isProTop,
                            onNeedPro = {
                                exportPickerFormat = null
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.export_ai_pro),
                                    Toast.LENGTH_LONG,
                                ).show()
                                onUpgrade()
                            },
                            onPick = { source ->
                                exportPickerFormat = null
                                exportSource = source
                                runCatching {
                                    when (format) {
                                        "txt" -> exportTxtLauncher.launch(exportFileName(t.title, "txt"))
                                        "md" -> exportMdLauncher.launch(exportFileName(t.title, "md"))
                                        else -> exportPdfLauncher.launch(exportFileName(t.title, "pdf"))
                                    }
                                }
                            },
                            onDismiss = { exportPickerFormat = null },
                        )
                    }

                    if (readingMode) {
                        ReadingModeDialog(
                            title = t.title,
                            text = editedText ?: t.text,
                            textScale = textScale,
                            onDismiss = { readingMode = false },
                        )
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

    keyPointsDialog?.let { points ->
        AlertDialog(
            onDismissRequest = { keyPointsDialog = null },
            title = { Text(stringResource(R.string.key_points_title)) },
            text = {
                if (points.isEmpty()) {
                    Text(stringResource(R.string.key_points_empty))
                } else {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            points.forEach { point ->
                                Text("•  $point", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    copyToClipboard(context, points.joinToString("\n") { "• $it" })
                    Toast.makeText(
                        context, context.getString(R.string.copied), Toast.LENGTH_SHORT
                    ).show()
                    keyPointsDialog = null
                }) {
                    Text(stringResource(R.string.action_copy))
                }
            },
            dismissButton = {
                TextButton(onClick = { keyPointsDialog = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showMergeDialog) {
        var candidates by remember { mutableStateOf<List<TranscriptEntity>?>(null) }
        var selected by remember { mutableStateOf(setOf<Long>()) }
        LaunchedEffect(Unit) { candidates = viewModel.mergeCandidates() }
        AlertDialog(
            onDismissRequest = { showMergeDialog = false },
            title = { Text(stringResource(R.string.merge_title)) },
            text = {
                val list = candidates
                when {
                    list == null -> Text(stringResource(R.string.status_queued))
                    list.isEmpty() -> Text(stringResource(R.string.merge_empty))
                    else -> Column(
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        list.forEach { candidate ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selected = if (candidate.id in selected) {
                                            selected - candidate.id
                                        } else {
                                            selected + candidate.id
                                        }
                                    },
                            ) {
                                androidx.compose.material3.Checkbox(
                                    checked = candidate.id in selected,
                                    onCheckedChange = null,
                                )
                                Text(
                                    candidate.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selected.isNotEmpty(),
                    onClick = {
                        showMergeDialog = false
                        viewModel.merge(selected.toList()) { newId ->
                            Toast.makeText(
                                context,
                                context.getString(R.string.merge_done),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_merge_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showMergeDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showAskAiDialog) {
        AskAiDialog(viewModel = viewModel, onDismiss = { showAskAiDialog = false })
    }

    if (showTranslateDialog) {
        TranslateDialog(
            viewModel = viewModel,
            onDismiss = {
                showTranslateDialog = false
                viewModel.clearTranslation()
            },
            onCopy = { text ->
                copyToClipboard(context, text)
                Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
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
    speed: Float,
    skipSilence: Boolean,
    onToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onSpeedClick: () -> Unit,
    onSkipSilenceClick: () -> Unit,
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
            Surface(
                onClick = onSpeedClick,
                shape = MaterialTheme.shapes.small,
                color = if (speed != 1.0f) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
            ) {
                Text(
                    // 1.25f -> "1.25x", 2.0f -> "2x"
                    java.text.DecimalFormat("0.##").format(speed) + "x",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (speed != 1.0f) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onSkipSilenceClick) {
                Icon(
                    Icons.Default.FastForward,
                    contentDescription = stringResource(R.string.player_skip_silence),
                    tint = if (skipSilence) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
private fun AskAiDialog(viewModel: DetailViewModel, onDismiss: () -> Unit) {
    val history by viewModel.qaHistory.collectAsStateWithLifecycle()
    val busy by viewModel.qaBusy.collectAsStateWithLifecycle()
    var question by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_ask_ai)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (history.isEmpty() && !busy) {
                    Text(
                        stringResource(R.string.ask_ai_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                history.forEach { entry ->
                    Text(
                        entry.question,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        com.vocatim.app.ui.common.MarkdownText(entry.answer)
                    }
                }
                if (busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.ask_ai_hint)) },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = question.isNotBlank() && !busy,
                onClick = {
                    viewModel.askAi(question)
                    question = ""
                },
            ) { Text(stringResource(R.string.ask_ai_send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private val TRANSLATE_TARGETS = listOf(
    "English", "Indonesian", "Chinese", "Japanese", "Korean", "Arabic",
    "Spanish", "French", "German",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TranslateDialog(
    viewModel: DetailViewModel,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val translation by viewModel.translation.collectAsStateWithLifecycle()
    val busy by viewModel.translateBusy.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_translate)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    busy -> {
                        Text(
                            stringResource(R.string.translate_busy),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    translation != null -> {
                        androidx.compose.foundation.text.selection.SelectionContainer {
                            Text(translation.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    else -> {
                        Text(
                            stringResource(R.string.translate_pick),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            TRANSLATE_TARGETS.forEach { target ->
                                androidx.compose.material3.AssistChip(
                                    onClick = { viewModel.translate(target) },
                                    label = { Text(target) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (translation != null) {
                TextButton(onClick = { onCopy(translation.orEmpty()) }) {
                    Text(stringResource(R.string.action_copy))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ReadingModeDialog(
    title: String,
    text: String,
    textScale: Float,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                        )
                    }
                }
                androidx.compose.foundation.text.selection.SelectionContainer(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text,
                        modifier = Modifier.padding(20.dp),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = MaterialTheme.typography.headlineSmall.fontSize * textScale,
                            lineHeight = MaterialTheme.typography.headlineSmall.lineHeight * textScale,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkersRow(markers: List<Long>, onSeek: (Long) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Flag,
            contentDescription = stringResource(R.string.detail_markers),
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(18.dp),
        )
        markers.forEach { ms ->
            Surface(
                onClick = { onSeek(ms) },
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
            ) {
                Text(
                    formatClock(ms),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

@Composable
private fun TranscriptSearchField(query: String, onQuery: (String) -> Unit, matches: Int) {
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(stringResource(R.string.detail_search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        matches.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    IconButton(onClick = { onQuery("") }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                        )
                    }
                }
            }
        },
        shape = MaterialTheme.shapes.large,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}

/** Segment text with the currently-spoken word highlighted. */
private fun karaokeText(
    segment: com.vocatim.app.data.db.SegmentEntity,
    positionMs: Long,
    highlight: Color,
): AnnotatedString {
    val words = com.vocatim.app.data.transcribe.WordTimings.decode(segment.words)
    val plain = segment.text.trim()
    if (words.isEmpty()) return AnnotatedString(plain)
    return buildAnnotatedString {
        append(plain)
        val current = words.firstOrNull { positionMs in it.startMs..it.endMs } ?: return@buildAnnotatedString
        // Walk the words in order so a repeated word highlights the right
        // occurrence, not the first one in the segment.
        var searchFrom = 0
        for (word in words) {
            val token = word.text.trim()
            if (token.isEmpty()) continue
            val idx = plain.indexOf(token, searchFrom)
            if (idx < 0) break
            if (word === current) {
                addStyle(
                    SpanStyle(background = highlight),
                    idx,
                    (idx + token.length).coerceAtMost(plain.length),
                )
                break
            }
            searchFrom = idx + token.length
        }
    }
}

private fun countMatches(text: String, query: String): Int {
    if (query.isBlank()) return 0
    var count = 0
    var idx = text.indexOf(query, 0, ignoreCase = true)
    while (idx >= 0) {
        count++
        idx = text.indexOf(query, idx + query.length, ignoreCase = true)
    }
    return count
}

private fun highlightMatches(text: String, query: String, bg: Color): AnnotatedString =
    buildAnnotatedString {
        append(text)
        if (query.isBlank()) return@buildAnnotatedString
        var idx = text.indexOf(query, 0, ignoreCase = true)
        while (idx >= 0) {
            addStyle(SpanStyle(background = bg), idx, idx + query.length)
            idx = text.indexOf(query, idx + query.length, ignoreCase = true)
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
private fun PhotoRow(
    attachments: List<com.vocatim.app.data.db.AttachmentEntity>,
    onAdd: (android.net.Uri) -> Unit,
    onRemove: (com.vocatim.app.data.db.AttachmentEntity) -> Unit,
) {
    var fullScreen by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val pickPhoto = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onAdd) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Add tile.
        Surface(
            onClick = {
                pickPhoto.launch(
                    androidx.activity.result.PickVisualMediaRequest(
                        androidx.activity.result.contract.ActivityResultContracts
                            .PickVisualMedia.ImageOnly
                    )
                )
            },
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.detail_add_photo),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        attachments.forEach { att ->
            Box {
                coil.compose.AsyncImage(
                    model = att.path,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { fullScreen = att.path },
                )
                Surface(
                    onClick = { onRemove(att) },
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(20.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.detail_remove_photo),
                        tint = Color.White,
                        modifier = Modifier.padding(3.dp),
                    )
                }
            }
        }
    }

    fullScreen?.let { path ->
        androidx.compose.ui.window.Dialog(onDismissRequest = { fullScreen = null }) {
            Box(contentAlignment = Alignment.BottomCenter) {
                coil.compose.AsyncImage(
                    model = path,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { fullScreen = null },
                )
                FilledTonalButton(
                    onClick = { shareImage(context, path) },
                    modifier = Modifier.padding(16.dp),
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.width(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.detail_share_photo))
                }
            }
        }
    }
}

@Composable
private fun FolderRow(
    current: String?,
    folders: List<String>,
    onSelect: (String?) -> Unit,
) {
    var showNewDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.FilterChip(
            selected = current == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.folder_none)) },
        )
        // Show every known folder, plus the current one if it's brand new.
        (folders + listOfNotNull(current).filter { it !in folders }).distinct().forEach { folder ->
            androidx.compose.material3.FilterChip(
                selected = current == folder,
                onClick = { onSelect(folder) },
                label = { Text(folder) },
            )
        }
        androidx.compose.material3.AssistChip(
            onClick = { newName = ""; showNewDialog = true },
            label = { Text(stringResource(R.string.folder_new)) },
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
        )
    }

    if (showNewDialog) {
        AlertDialog(
            onDismissRequest = { showNewDialog = false },
            title = { Text(stringResource(R.string.folder_new_title)) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.folder_new_hint)) },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        onSelect(newName.trim())
                        showNewDialog = false
                    },
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showNewDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SegmentList(
    segments: List<com.vocatim.app.data.db.SegmentEntity>,
    positionMs: Long,
    canSeek: Boolean,
    textScale: Float,
    onSegmentClick: (Long) -> Unit,
    onCopySegment: (String) -> Unit,
    showSpeakers: Boolean = false,
) {
    val scrollState = rememberScrollState()
    val activeIndex = segments.indexOfFirst { positionMs in it.startMs until it.endMs }
    LaunchedEffect(activeIndex, segments.size) {
        if (activeIndex >= 0 && segments.isNotEmpty()) {
            scrollState.animateScrollTo((activeIndex * 72).coerceAtMost(scrollState.maxValue))
        }
    }
    // Diarized labels when available; else the pause-flip estimate.
    val speakers = remember(segments, showSpeakers) {
        IntArray(segments.size).also { arr ->
            if (!showSpeakers) return@also
            if (segments.any { it.speaker != null }) {
                // Real speakers are 1-based; carry the last label across
                // segments diarization couldn't attribute (e.g. silence).
                var last = 1
                for (i in segments.indices) {
                    last = segments[i].speaker ?: last
                    arr[i] = last - 1
                }
            } else {
                var spk = 0
                for (i in segments.indices) {
                    if (i > 0 && segments[i].startMs - segments[i - 1].endMs > 1500L) spk = 1 - spk
                    arr[i] = spk
                }
            }
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
            if (showSpeakers && (index == 0 || speakers[index] != speakers[index - 1])) {
                Text(
                    stringResource(R.string.detail_speaker_n, speakers[index] + 1),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                )
            }
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
                        // Karaoke: inside the active segment, the word being
                        // spoken right now is highlighted.
                        if (active) karaokeText(
                            segment,
                            positionMs,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        ) else AnnotatedString(segment.text.trim()),
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

/**
 * The uniform collapsible card every detail section uses: an icon + title
 * header with an optional badge, tap anywhere on it to reveal the content
 * below. Starts closed so the page opens as a tidy stack of headers.
 */
@Composable
private fun SectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    badge: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    // Smooth reveal: content height animates, chevron rotates in sync.
    val chevronAngle by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "chevron",
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { expanded = !expanded },
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.width(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                badge?.let {
                    Pill(it)
                    Spacer(Modifier.width(6.dp))
                }
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.action_collapse else R.string.action_expand
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(chevronAngle),
                )
            }
            if (expanded) content()
        }
    }
}

/** "Export as X — of what?" chooser for notes that carry more than a transcript. */
@Composable
private fun ExportSourceDialog(
    hasMinutes: Boolean,
    hasSummary: Boolean,
    isPro: Boolean,
    onNeedPro: () -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_pick_source)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Exporting the transcript is free; exporting AI outputs is Pro.
                data class Opt(val source: String, val label: Int, val pro: Boolean)
                val options = buildList {
                    add(Opt(EXPORT_SOURCE_TRANSCRIPT, R.string.detail_transcript_section, false))
                    if (hasMinutes) add(Opt(EXPORT_SOURCE_MINUTES, R.string.detail_minutes_section, true))
                    if (hasSummary) add(Opt(EXPORT_SOURCE_SUMMARY, R.string.summary_title, true))
                }
                options.forEach { opt ->
                    Surface(
                        onClick = { if (opt.pro && !isPro) onNeedPro() else onPick(opt.source) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(opt.label),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            if (opt.pro && !isPro) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** AI-summary card content: result, progress, or the generate/unlock pitch. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryBody(
    summary: String?,
    viewModel: DetailViewModel,
    onUpgrade: () -> Unit,
) {
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val modelState by viewModel.summaryModelState.collectAsStateWithLifecycle()
    val progress by viewModel.summaryProgress.collectAsStateWithLifecycle()
    val modelSizeBytes by viewModel.summaryModelSizeBytes.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when {
                progress != null -> {
                    LinearProgressIndicator(
                        progress = { progress ?: 0f },
                        modifier = Modifier.fillMaxWidth(),
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(
                        stringResource(R.string.summary_running),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // The summary streams in live as the model writes it.
                    val partial by viewModel.summaryPartial.collectAsStateWithLifecycle()
                    partial?.takeIf { it.isNotBlank() }?.let {
                        com.vocatim.app.ui.common.MarkdownText(it)
                    }
                    OutlinedButton(onClick = viewModel::cancelSummary) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
                summary != null -> {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        com.vocatim.app.ui.common.MarkdownText(summary)
                    }
                    // Both engines stay one tap away even after a summary
                    // exists — regenerating with the other one is common.
                    val cloudConfigured by viewModel.cloudConfigured.collectAsStateWithLifecycle()
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(onClick = {
                            copyToClipboard(context, summary)
                            Toast.makeText(
                                context, context.getString(R.string.copied), Toast.LENGTH_SHORT
                            ).show()
                        }) { Text(stringResource(R.string.action_copy)) }
                        if (modelState is ModelState.Downloaded) {
                            OutlinedButton(onClick = viewModel::startSummary) {
                                Text(stringResource(R.string.summary_regenerate_local))
                            }
                        }
                        if (cloudConfigured) {
                            OutlinedButton(onClick = viewModel::startCloudSummary) {
                                Text(stringResource(R.string.summary_generate_cloud))
                            }
                        }
                    }
                }
                !isPro -> {
                    Text(
                        stringResource(R.string.summary_pro_pitch),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onUpgrade) {
                        Text(stringResource(R.string.summary_unlock))
                    }
                }
                modelState is ModelState.Downloaded -> {
                    Text(
                        stringResource(R.string.summary_ready_pitch),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::startSummary) {
                            Text(stringResource(R.string.summary_generate))
                        }
                        val cloudConfigured by viewModel.cloudConfigured.collectAsStateWithLifecycle()
                        if (cloudConfigured) {
                            OutlinedButton(onClick = viewModel::startCloudSummary) {
                                Text(stringResource(R.string.summary_generate_cloud))
                            }
                        }
                    }
                }
                modelState is ModelState.Failed -> {
                    // Surface WHY (e.g. "not enough free storage") + retry.
                    Text(
                        (modelState as ModelState.Failed).message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = viewModel::downloadSummaryModel) {
                        Text(stringResource(R.string.debug_retry))
                    }
                }
                modelState is ModelState.Downloading -> {
                    val d = modelState as ModelState.Downloading
                    Text(
                        stringResource(
                            R.string.summary_downloading,
                            d.downloadedBytes / (1024 * 1024),
                            (d.totalBytes.takeIf { it > 0 } ?: modelSizeBytes) / (1024 * 1024),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (d.totalBytes > 0) {
                        LinearProgressIndicator(
                            progress = { d.progress },
                            modifier = Modifier.fillMaxWidth(),
                            trackColor = MaterialTheme.colorScheme.outlineVariant,
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            trackColor = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                }
                else -> {
                    Text(
                        stringResource(
                            R.string.summary_download_pitch,
                            modelSizeBytes / (1024 * 1024),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = viewModel::downloadSummaryModel) {
                            Text(stringResource(R.string.summary_download))
                        }
                        // BYOK works without the 1GB local model.
                        val cloudConfigured by viewModel.cloudConfigured.collectAsStateWithLifecycle()
                        if (cloudConfigured) {
                            OutlinedButton(onClick = viewModel::startCloudSummary) {
                                Text(stringResource(R.string.summary_generate_cloud))
                            }
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

/**
 * Human-first header: the date as quiet text, then chips anyone understands
 * at a glance — duration, word count, language. Engine trivia (model name,
 * realtime factor) stays out of the way.
 */
@Composable
private fun MetaRow(t: TranscriptEntity) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            formatDate(t.createdAt),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (t.audioDurationMs > 0) {
                MetaChip(
                    Icons.Default.Schedule,
                    formatClock(t.audioDurationMs),
                )
            }
            val words = remember(t.text) {
                t.text.split(Regex("\\s+")).count { it.isNotBlank() }
            }
            if (words > 0) {
                MetaChip(
                    Icons.Default.Subtitles,
                    stringResource(R.string.meta_words, formatCount(words)),
                )
            }
            // Parakeet is English-only; the stored language is the whisper
            // setting and would mislabel the chip.
            val languageCode =
                if (t.modelId == com.vocatim.app.data.model.ParakeetModel.ID) "en"
                else t.language.takeIf { it != "auto" } ?: t.detectedLanguage
            languageCode?.let { code ->
                MetaChip(
                    Icons.Default.Language,
                    java.util.Locale(code).let { locale ->
                        locale.getDisplayLanguage(locale)
                            .replaceFirstChar { it.uppercase() }
                    },
                )
            }
        }
    }
}

@Composable
private fun MetaChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** 532 -> "532", 1834 -> "1.8k". */
private fun formatCount(n: Int): String =
    if (n < 1000) n.toString()
    else String.format(Locale.US, "%.1fk", n / 1000f)

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
    // SRT/VTT and timestamped copy only apply to audio transcripts.
    showTimestamped: Boolean,
) {
    if (showTimestamped) {
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
    } else {
        // Text-only notes (minutes, imported text): no timestamps/subtitles.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton(
                Icons.Default.ContentCopy, stringResource(R.string.action_copy), onCopy,
                Modifier.weight(1f),
            )
            ActionButton(
                Icons.Default.Share, stringResource(R.string.action_share), onShare,
                Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton(
                Icons.Default.Description, stringResource(R.string.action_export_txt), onExportTxt,
                Modifier.weight(1f),
            )
            ActionButton(
                Icons.Default.Description, stringResource(R.string.action_export_md), onExportMd,
                Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActionButton(
                Icons.Default.Description, stringResource(R.string.action_export_pdf), onExportPdf,
                Modifier.weight(1f),
            )
        }
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

/** Prefilled system calendar event; no calendar permission needed. */
private fun addToCalendar(context: Context, t: TranscriptEntity) {
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(android.provider.CalendarContract.Events.CONTENT_URI)
        .putExtra(android.provider.CalendarContract.Events.TITLE, t.title)
        .putExtra(
            android.provider.CalendarContract.Events.DESCRIPTION,
            t.text.take(4000),
        )
        .putExtra(
            android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, t.createdAt
        )
        .putExtra(
            android.provider.CalendarContract.EXTRA_EVENT_END_TIME,
            t.createdAt + t.audioDurationMs.coerceAtLeast(60_000),
        )
    runCatching { context.startActivity(intent) }
}

@Composable
private fun LockedNoteGate(modifier: Modifier = Modifier, onUnlock: () -> Unit) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Text(
                stringResource(R.string.note_locked_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Button(onClick = onUnlock) { Text(stringResource(R.string.note_unlock)) }
        }
    }
}

/** Biometric/device-credential prompt to open a locked note. */
private fun promptUnlock(context: Context, onSuccess: () -> Unit) {
    val activity = context as? androidx.fragment.app.FragmentActivity ?: return
    val prompt = androidx.biometric.BiometricPrompt(
        activity,
        androidx.core.content.ContextCompat.getMainExecutor(context),
        object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(
                result: androidx.biometric.BiometricPrompt.AuthenticationResult,
            ) {
                onSuccess()
            }
        },
    )
    val info = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
        .setTitle(context.getString(R.string.note_locked_title))
        .setAllowedAuthenticators(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )
        .build()
    runCatching { prompt.authenticate(info) }
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

/** Shares a content URI (PDF, audio) to another app. */
private fun shareStream(context: Context, uri: android.net.Uri, mime: String) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(
        Intent.createChooser(intent, context.getString(R.string.action_share))
    )
}

/** Shares an attached photo via FileProvider so other apps can read it. */
private fun shareImage(context: Context, path: String) {
    runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", java.io.File(path)
        )
        val intent = Intent(Intent.ACTION_SEND)
            .setType("image/*")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.detail_share_photo))
        )
    }
}
