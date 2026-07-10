package com.vocatim.app.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocatim.app.R
import com.vocatim.app.data.db.TranscriptEntity
import com.vocatim.app.ui.common.Pill
import com.vocatim.app.ui.common.formatClock
import com.vocatim.app.ui.theme.BrandGradient
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    onTranscriptClick: (Long) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val month by viewModel.month.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val dayLoad by viewModel.dayLoad.collectAsStateWithLifecycle()
    val monthStats by viewModel.monthStats.collectAsStateWithLifecycle()
    val dayItems by viewModel.itemsForSelectedDate.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.calendar_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (selectedDate != today || YearMonth.from(today) != month) {
                        TextButton(onClick = viewModel::goToToday) {
                            Text(stringResource(R.string.calendar_today))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            MonthHeader(
                month = month,
                stats = monthStats,
                onPrevious = viewModel::previousMonth,
                onNext = viewModel::nextMonth,
            )
            Spacer(Modifier.height(10.dp))

            // The grid slides in the direction of travel.
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 1.dp,
            ) {
                AnimatedContent(
                    targetState = month,
                    transitionSpec = {
                        val forward = targetState > initialState
                        (slideInHorizontally { if (forward) it / 3 else -it / 3 } + fadeIn())
                            .togetherWith(
                                slideOutHorizontally { if (forward) -it / 3 else it / 3 } + fadeOut()
                            )
                    },
                    label = "monthSlide",
                ) { visibleMonth ->
                    MonthGrid(
                        month = visibleMonth,
                        selected = selectedDate,
                        load = dayLoad,
                        today = today,
                        onSelect = viewModel::selectDate,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    com.vocatim.app.ui.common.formatDate(
                        selectedDate.atStartOfDay(java.time.ZoneId.systemDefault())
                            .toInstant().toEpochMilli()
                    ).substringBefore(","),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (dayItems.isNotEmpty()) {
                    Pill(
                        dayItems.size.toString(),
                        color = MaterialTheme.colorScheme.primary,
                        background = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))

            if (dayItems.isEmpty()) {
                Text(
                    stringResource(R.string.calendar_empty_day),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                ) {
                    items(dayItems, key = { it.id }) { item ->
                        DayItemCard(item, onClick = { onTranscriptClick(item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    stats: MonthStats,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
                    .replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                month.year.toString() + if (stats.noteCount > 0) {
                    "  ·  " + stringResource(
                        R.string.calendar_month_stats,
                        stats.noteCount,
                        stats.totalDurationMs / 60_000,
                    )
                } else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalIconButton(
            onClick = onPrevious,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.calendar_prev_month),
            )
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(
            onClick = onNext,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.calendar_next_month),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    load: Map<LocalDate, Int>,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstDay = month.atDay(1)
    // Monday-first grid, common in Indonesia.
    val leadingBlanks = (firstDay.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val daysInMonth = month.lengthOfMonth()

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth()) {
            for (dow in 0 until 7) {
                val day = DayOfWeek.of((DayOfWeek.MONDAY.value - 1 + dow) % 7 + 1)
                Text(
                    day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .take(2).uppercase(Locale.getDefault()),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        // Flat cell list (nulls = blanks), chunked into calendar weeks.
        val cells: List<LocalDate?> =
            List(leadingBlanks) { null } + (1..daysInMonth).map { month.atDay(it) }
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    if (date == null) {
                        Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        DayCell(
                            date = date,
                            isSelected = date == selected,
                            isToday = date == today,
                            itemCount = load[date] ?: 0,
                            onClick = { onSelect(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                repeat(7 - week.size) {
                    Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    itemCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(3.dp)
            .clip(CircleShape)
            .then(
                when {
                    isSelected -> Modifier.background(BrandGradient)
                    isToday -> Modifier.border(
                        1.5.dp, MaterialTheme.colorScheme.primary, CircleShape
                    )
                    else -> Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isSelected -> Color.White
                    isToday -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
            // One dot per note, capped at three — a tiny activity heatmap.
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(itemCount.coerceAtMost(3)) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) Color.White
                                else MaterialTheme.colorScheme.secondary
                            ),
                    )
                }
                if (itemCount == 0) Spacer(Modifier.size(4.dp))
            }
        }
    }
}

@Composable
private fun DayItemCard(item: TranscriptEntity, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leading clock time anchors the day's timeline.
            Text(
                java.text.SimpleDateFormat("HH:mm", Locale.getDefault())
                    .format(java.util.Date(item.createdAt)),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.text.isNotBlank()) {
                    Text(
                        item.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (item.audioDurationMs > 0) {
                Spacer(Modifier.width(8.dp))
                Pill(formatClock(item.audioDurationMs))
            }
        }
    }
}
