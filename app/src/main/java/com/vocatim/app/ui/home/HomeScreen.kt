package com.vocatim.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocatim.app.R
import com.vocatim.app.data.db.TranscriptStatus
import com.vocatim.app.ui.common.MiniWaveform
import com.vocatim.app.ui.common.Pill
import com.vocatim.app.ui.common.formatClock
import com.vocatim.app.ui.common.formatDate
import com.vocatim.app.ui.theme.BrandGradient

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onRecordClick: () -> Unit,
    onTranscriptClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onDebugClick: () -> Unit,
    onUpgradeClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val quotaBanner by viewModel.quotaBanner.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val selectedFolder by viewModel.selectedFolder.collectAsStateWithLifecycle()
    val deletedEvent by viewModel.deletedEvent.collectAsStateWithLifecycle()
    var sortMenuOpen by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current

    val pickAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { viewModel.importAudio(it) }
    }
    val pickText = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importTextFile(it) } }

    // Undo snackbar: action restores, any other dismissal finalizes the delete.
    LaunchedEffect(deletedEvent) {
        val title = deletedEvent ?: return@LaunchedEffect
        val result = snackbarHost.showSnackbar(
            message = context.getString(R.string.home_deleted, title),
            actionLabel = context.getString(R.string.action_undo),
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete()
        else viewModel.finalizeDelete()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(BrandGradient)
                    .clickable(onClick = onRecordClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = stringResource(R.string.home_record),
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding()
        ) {
            HomeHeader(
                onImportAudio = { pickAudio.launch(arrayOf("audio/*", "video/mp4", "application/ogg")) },
                onImportText = { pickText.launch(arrayOf("text/*")) },
                onSettings = onSettingsClick,
                onDebug = onDebugClick,
            )

            if (stats.transcriptCount > 0) {
                StatsBar(stats)
            }

            if (folders.isNotEmpty()) {
                FolderRow(
                    folders = folders,
                    selected = selectedFolder,
                    onSelect = viewModel::selectFolder,
                )
            }

            // Search + sort share one row; status filters were dropped —
            // every card already wears its status pill.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchField(
                    query = query,
                    onQueryChanged = viewModel::onQueryChanged,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { sortMenuOpen = true }) {
                        Icon(
                            Icons.Default.Sort,
                            contentDescription = stringResource(R.string.home_sort),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                        HomeSort.entries.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(homeSortLabel(s)) },
                                onClick = {
                                    viewModel.setSort(s)
                                    sortMenuOpen = false
                                },
                                trailingIcon = {
                                    if (sort == s) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }

            quotaBanner?.let { banner ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(onClick = onUpgradeClick),
                    shape = MaterialTheme.shapes.medium,
                    color = if (banner.exhausted) {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (banner.exhausted) {
                                stringResource(R.string.quota_banner_exhausted)
                            } else {
                                stringResource(R.string.quota_banner, banner.remainingMinutes)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(R.string.quota_banner_cta),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            if (items.isEmpty() && query.isBlank()) {
                EmptyState(onImport = { pickAudio.launch(arrayOf("audio/*", "video/mp4", "application/ogg")) })
            } else if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.home_search_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // Day headers only make sense for date-ordered lists.
                val grouped = if (sort == HomeSort.NEWEST || sort == HomeSort.OLDEST) {
                    groupedByDay(items)
                } else {
                    listOf(null to items)
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp, end = 20.dp, top = 4.dp, bottom = 100.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    grouped.forEach { (label, groupItems) ->
                        if (label != null) {
                            item(key = "header_$label") {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                                )
                            }
                        }
                        items(groupItems, key = { it.transcript.id }) { item ->
                            SwipeableTranscriptCard(
                                item = item,
                                onClick = { onTranscriptClick(item.transcript.id) },
                                onPin = { viewModel.togglePin(item.transcript.id) },
                                onDelete = { viewModel.delete(item.transcript.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun homeSortLabel(sort: HomeSort): String = when (sort) {
    HomeSort.NEWEST -> stringResource(R.string.home_sort_newest)
    HomeSort.OLDEST -> stringResource(R.string.home_sort_oldest)
    HomeSort.LONGEST -> stringResource(R.string.home_sort_longest)
    HomeSort.TITLE -> stringResource(R.string.home_sort_title)
}

@Composable
private fun StatsBar(stats: HomeStats) {
    val hours = stats.totalDurationMs / 3_600_000
    val minutes = (stats.totalDurationMs % 3_600_000) / 60_000
    val durationLabel = if (hours > 0) {
        stringResource(R.string.home_stats_hours, hours, minutes)
    } else {
        stringResource(R.string.home_stats_minutes, minutes.coerceAtLeast(1))
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    ) {
        Text(
            stringResource(R.string.home_stats, stats.transcriptCount, durationLabel),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTranscriptCard(
    item: HomeItem,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onPin()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    true
                }
                else -> false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val color = when (direction) {
                SwipeToDismissBoxValue.StartToEnd ->
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                SwipeToDismissBoxValue.EndToStart ->
                    MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                else -> Color.Transparent
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.large)
                    .background(color)
                    .padding(horizontal = 20.dp),
                contentAlignment = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                    else -> Alignment.Center
                },
            ) {
                Text(
                    when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> stringResource(R.string.home_swipe_pin)
                        SwipeToDismissBoxValue.EndToStart -> stringResource(R.string.home_swipe_delete)
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    ) {
        TranscriptCard(item, onClick = onClick)
    }
}

@Composable
private fun FolderRow(
    folders: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FolderChip(
            label = stringResource(R.string.folder_all),
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        folders.forEach { folder ->
            FolderChip(
                label = folder,
                selected = selected == folder,
                onClick = { onSelect(folder) },
            )
        }
    }
}

@Composable
private fun FolderChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun groupedByDay(items: List<HomeItem>): List<Pair<String?, List<HomeItem>>> {
    val todayLabel = stringResource(R.string.home_group_today)
    val yesterdayLabel = stringResource(R.string.home_group_yesterday)
    return remember(items, todayLabel, yesterdayLabel) {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now(zone)
        val formatter = java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy")
        items
            .groupBy { item ->
                java.time.Instant.ofEpochMilli(item.transcript.createdAt)
                    .atZone(zone).toLocalDate()
            }
            .map { (date, list) ->
                val label: String? = when (date) {
                    today -> todayLabel
                    today.minusDays(1) -> yesterdayLabel
                    else -> date.format(formatter)
                }
                label to list
            }
    }
}

@Composable
private fun SearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = modifier.padding(start = 20.dp, top = 4.dp, bottom = 4.dp),
        placeholder = { Text(stringResource(R.string.home_search_hint)) },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChanged("") }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        singleLine = true,
        shape = androidx.compose.foundation.shape.CircleShape,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}

@Composable
private fun HomeHeader(
    onImportAudio: () -> Unit,
    onImportText: () -> Unit,
    onSettings: () -> Unit,
    onDebug: () -> Unit,
) {
    var importMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { importMenuOpen = true }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = stringResource(R.string.home_import),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = importMenuOpen, onDismissRequest = { importMenuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_import_audio)) },
                    onClick = { importMenuOpen = false; onImportAudio() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_import_text)) },
                    onClick = { importMenuOpen = false; onImportText() },
                )
            }
        }
        IconButton(onClick = onSettings) {
            Icon(
                Icons.Default.Settings,
                contentDescription = stringResource(R.string.settings_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (com.vocatim.app.BuildConfig.DEBUG) {
            IconButton(onClick = onDebug) {
                Icon(
                    Icons.Default.GraphicEq,
                    contentDescription = stringResource(R.string.home_debug),
                    tint = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onImport: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            // Deliberately muted: the one gradient mic on screen is the FAB.
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp),
                )
            }
            Text(
                stringResource(R.string.home_empty_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.home_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onImport) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.home_import_cta))
            }
        }
    }
}

@Composable
private fun TranscriptCard(item: HomeItem, onClick: () -> Unit) {
    val t = item.transcript
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (t.pinned) {
                    Icon(
                        Icons.Default.PushPin,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 4.dp),
                    )
                }
                Text(
                    t.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (t.audioDurationMs > 0) {
                    Spacer(Modifier.size(8.dp))
                    Pill(formatClock(t.audioDurationMs))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    formatDate(t.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                t.tag?.let { tag ->
                    Pill(
                        tagLabel(tag),
                        color = MaterialTheme.colorScheme.secondary,
                        background = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                    )
                }
            }
            if (t.audioPath != null) {
                MiniWaveform(item.waveform, modifier = Modifier.fillMaxWidth())
            }
            when (t.status) {
                TranscriptStatus.DONE -> {
                    if (t.text.isNotBlank()) {
                        Text(
                            t.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                TranscriptStatus.FAILED -> {
                    Pill(
                        stringResource(R.string.status_failed),
                        color = MaterialTheme.colorScheme.error,
                        background = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        dot = true,
                    )
                }
                else -> {
                    val p = item.progress
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Pill(
                            when {
                                p == null -> stringResource(R.string.status_queued)
                                p.converting -> stringResource(R.string.status_converting)
                                else -> stringResource(R.string.status_transcribing)
                            },
                            color = MaterialTheme.colorScheme.secondary,
                            background = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                            dot = true,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
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
                }
            }
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
