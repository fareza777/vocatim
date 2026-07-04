package com.vocatim.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocatim.app.R
import com.vocatim.app.data.db.TranscriptStatus
import com.vocatim.app.ui.common.Pill
import com.vocatim.app.ui.common.formatClock
import com.vocatim.app.ui.common.formatDate
import com.vocatim.app.ui.theme.BrandGradient

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

    val pickAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { viewModel.importAudio(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            // Hero action: gradient record button.
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
                onImport = { pickAudio.launch(arrayOf("audio/*", "video/mp4", "application/ogg")) },
                onSettings = onSettingsClick,
                onDebug = onDebugClick,
            )

            SearchField(
                query = query,
                onQueryChanged = viewModel::onQueryChanged,
            )

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
                EmptyState()
            } else if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.home_search_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp, end = 20.dp, top = 4.dp, bottom = 100.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items, key = { it.transcript.id }) { item ->
                        TranscriptCard(item, onClick = { onTranscriptClick(item.transcript.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChanged: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
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
    onImport: () -> Unit,
    onSettings: () -> Unit,
    onDebug: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
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
        IconButton(onClick = onImport) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.home_import),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
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
            Text(
                formatDate(t.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
