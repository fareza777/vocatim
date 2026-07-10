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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Delete
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
import com.vocatim.app.ui.common.tourTarget
import com.vocatim.app.ui.theme.BrandGradient

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    onRecordClick: () -> Unit,
    onTranscriptClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onCalendarClick: () -> Unit,
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
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val selectionMode = selection.isNotEmpty()
    var sortMenuOpen by remember { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val context = LocalContext.current

    // One-time spotlight tour over the real controls.
    val showTour by viewModel.showTour.collectAsStateWithLifecycle()
    var tourStep by remember { mutableStateOf(0) }
    val tourTargets = remember {
        androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>()
    }

    androidx.activity.compose.BackHandler(enabled = selectionMode) {
        viewModel.clearSelection()
    }

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

    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(BrandGradient)
                    .clickable(onClick = onRecordClick)
                    .tourTarget(tourTargets, "record"),
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
            if (selectionMode) {
                SelectionBar(
                    count = selection.size,
                    folders = folders,
                    onClose = viewModel::clearSelection,
                    onDelete = viewModel::deleteSelected,
                    onMove = viewModel::moveSelectedToFolder,
                )
            } else {
                HomeHeader(
                    onImportAudio = { pickAudio.launch(arrayOf("audio/*", "video/mp4", "application/ogg")) },
                    onImportText = { pickText.launch(arrayOf("text/*")) },
                    onSettings = onSettingsClick,
                    onCalendar = onCalendarClick,
                    onDebug = onDebugClick,
                    tourTargets = tourTargets,
                )
            }

            if (stats.transcriptCount > 0) {
                StatsBar(stats)
            }

            if (folders.isNotEmpty()) {
                val folderCounts by viewModel.folderCounts.collectAsStateWithLifecycle()
                FolderRow(
                    folders = folders,
                    counts = folderCounts,
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
                    modifier = Modifier
                        .weight(1f)
                        .tourTarget(tourTargets, "search"),
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (banner.exhausted) {
                                    stringResource(R.string.quota_banner_exhausted)
                                } else {
                                    stringResource(R.string.quota_banner, banner.remainingMinutes)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (!banner.exhausted) {
                                Text(
                                    stringResource(R.string.quota_silence_free),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
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
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                                )
                            }
                        }
                        items(groupItems, key = { it.transcript.id }) { item ->
                            val id = item.transcript.id
                            // No swipe gestures: they fired accidentally.
                            // Long-press opens an explicit menu instead.
                            var cardMenuOpen by remember { mutableStateOf(false) }
                            Box {
                                TranscriptCard(
                                    item,
                                    onClick = {
                                        if (selectionMode) viewModel.toggleSelection(id)
                                        else onTranscriptClick(id)
                                    },
                                    onLongClick = {
                                        if (selectionMode) viewModel.toggleSelection(id)
                                        else cardMenuOpen = true
                                    },
                                    selectionMode = selectionMode,
                                    selected = id in selection,
                                )
                                DropdownMenu(
                                    expanded = cardMenuOpen,
                                    onDismissRequest = { cardMenuOpen = false },
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(
                                                    if (item.transcript.pinned) R.string.action_unpin
                                                    else R.string.action_pin
                                                )
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(Icons.Default.PushPin, contentDescription = null)
                                        },
                                        onClick = {
                                            cardMenuOpen = false
                                            viewModel.togglePin(id)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_select)) },
                                        leadingIcon = {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        },
                                        onClick = {
                                            cardMenuOpen = false
                                            viewModel.toggleSelection(id)
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                stringResource(R.string.action_delete),
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error,
                                            )
                                        },
                                        onClick = {
                                            cardMenuOpen = false
                                            viewModel.delete(id)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTour && !selectionMode) {
        val tourSteps = listOf(
            com.vocatim.app.ui.common.TourStep(
                "record",
                stringResource(R.string.tour_record_title),
                stringResource(R.string.tour_record_body),
            ),
            com.vocatim.app.ui.common.TourStep(
                "import",
                stringResource(R.string.tour_import_title),
                stringResource(R.string.tour_import_body),
            ),
            com.vocatim.app.ui.common.TourStep(
                "calendar",
                stringResource(R.string.tour_calendar_title),
                stringResource(R.string.tour_calendar_body),
            ),
            com.vocatim.app.ui.common.TourStep(
                "search",
                stringResource(R.string.tour_search_title),
                stringResource(R.string.tour_search_body),
            ),
            com.vocatim.app.ui.common.TourStep(
                "settings",
                stringResource(R.string.tour_settings_title),
                stringResource(R.string.tour_settings_body),
            ),
        )
        com.vocatim.app.ui.common.TourOverlay(
            steps = tourSteps,
            stepIndex = tourStep,
            targets = tourTargets,
            onNext = {
                if (tourStep >= tourSteps.size - 1) viewModel.finishTour()
                else tourStep++
            },
            onSkip = viewModel::finishTour,
        )
    }
    } // end tour root Box
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
    val durationValue = if (hours > 0) {
        stringResource(R.string.home_stat_hours_value, hours, minutes)
    } else {
        "${minutes.coerceAtLeast(if (stats.totalDurationMs > 0) 1 else 0)}m"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCard(
            value = stats.transcriptCount.toString(),
            label = stringResource(R.string.home_stat_notes),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = durationValue,
            label = stringResource(R.string.home_stat_transcribed),
            modifier = Modifier.weight(1f),
        )
        StatCard(
            value = stats.weekCount.toString(),
            label = stringResource(R.string.home_stat_week),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FolderRow(
    folders: List<String>,
    counts: Map<String, Int>,
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
            val n = counts[folder] ?: 0
            FolderChip(
                label = if (n > 0) "$folder · $n" else folder,
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
private fun SelectionBar(
    count: Int,
    folders: List<String>,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onMove: (String?) -> Unit,
) {
    var moveOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
        }
        Text(
            stringResource(R.string.home_selected_count, count),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Box {
            IconButton(onClick = { moveOpen = true }) {
                Icon(
                    Icons.AutoMirrored.Filled.DriveFileMove,
                    contentDescription = stringResource(R.string.home_move),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = moveOpen, onDismissRequest = { moveOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.folder_none)) },
                    onClick = { onMove(null); moveOpen = false },
                )
                folders.forEach { folder ->
                    DropdownMenuItem(
                        text = { Text(folder) },
                        onClick = { onMove(folder); moveOpen = false },
                    )
                }
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.action_delete),
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun HomeHeader(
    onImportAudio: () -> Unit,
    onImportText: () -> Unit,
    onSettings: () -> Unit,
    onCalendar: () -> Unit,
    onDebug: () -> Unit,
    tourTargets: MutableMap<String, androidx.compose.ui.geometry.Rect> = mutableMapOf(),
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
            IconButton(
                onClick = { importMenuOpen = true },
                modifier = Modifier.tourTarget(tourTargets, "import"),
            ) {
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
        IconButton(
            onClick = onCalendar,
            modifier = Modifier.tourTarget(tourTargets, "calendar"),
        ) {
            Icon(
                Icons.Default.CalendarMonth,
                contentDescription = stringResource(R.string.calendar_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(
            onClick = onSettings,
            modifier = Modifier.tourTarget(tourTargets, "settings"),
        ) {
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
            // Layered halo behind the mic — a warmer first impression than a
            // single flat circle, still calm on the linen background.
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                )
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                )
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(BrandGradient),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
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
            Text(
                stringResource(R.string.home_empty_tip_wa),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun TranscriptCard(
    item: HomeItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    selectionMode: Boolean = false,
    selected: Boolean = false,
) {
    val t = item.transcript
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.primary
        ) else null,
        // Soft lift so cards read as layers on the porcelain background.
        shadowElevation = 1.dp,
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Pill(stringResource(cardTypeLabel(t)))
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
                if (t.summary != null) {
                    Pill(
                        stringResource(R.string.card_badge_ai),
                        color = MaterialTheme.colorScheme.primary,
                        background = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        dot = true,
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
                    Spacer(Modifier.height(4.dp))
                    // Skeleton lines hint at the transcript still coming in.
                    com.vocatim.app.ui.common.ShimmerLine(widthFraction = 0.95f)
                    Spacer(Modifier.height(6.dp))
                    com.vocatim.app.ui.common.ShimmerLine(widthFraction = 0.7f)
                    Spacer(Modifier.height(6.dp))
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

private fun cardTypeLabel(t: com.vocatim.app.data.db.TranscriptEntity): Int = when {
    t.modelId == com.vocatim.app.service.SummaryService.MODEL_ID_MINUTES -> R.string.card_type_minutes
    t.audioPath == null && t.audioDurationMs == 0L -> R.string.card_type_note
    else -> R.string.card_type_recording
}

@Composable
private fun tagLabel(tag: String): String = when (tag) {
    // Legacy built-in tags are localized; custom folders show verbatim.
    "work" -> stringResource(R.string.tag_work)
    "study" -> stringResource(R.string.tag_study)
    "interview" -> stringResource(R.string.tag_interview)
    "personal" -> stringResource(R.string.tag_personal)
    "other" -> stringResource(R.string.tag_other)
    else -> tag
}
