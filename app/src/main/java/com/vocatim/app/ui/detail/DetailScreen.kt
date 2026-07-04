package com.vocatim.app.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocatim.app.R
import com.vocatim.app.data.db.TranscriptEntity
import com.vocatim.app.data.db.TranscriptStatus
import com.vocatim.app.ui.common.formatClock
import com.vocatim.app.ui.common.formatDate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    onBack: () -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val transcript by viewModel.transcript.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val editedText by viewModel.editedText.collectAsStateWithLifecycle()
    val exportEvent by viewModel.exportEvent.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(transcript?.title ?: "")
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
                        )
                    }
                },
            )
        },
    ) { padding ->
        val t = transcript ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MetaLine(t)

            when (t.status) {
                TranscriptStatus.DONE -> {
                    OutlinedTextField(
                        value = editedText ?: t.text,
                        onValueChange = viewModel::onTextChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp),
                        placeholder = { Text(stringResource(R.string.detail_empty_text)) },
                    )
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
                    ActionRow(
                        onCopy = {
                            copyToClipboard(context, viewModel.currentText())
                            Toast.makeText(
                                context,
                                context.getString(R.string.copied),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                        onShare = { shareText(context, viewModel.currentText()) },
                        onExportTxt = { exportTxtLauncher.launch(t.title + ".txt") },
                        onExportSrt = { exportSrtLauncher.launch(t.title + ".srt") },
                    )
                }
                TranscriptStatus.FAILED -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                if (t.errorMessage == "CANCELLED") {
                                    stringResource(R.string.detail_cancelled)
                                } else {
                                    stringResource(
                                        R.string.detail_failed, t.errorMessage ?: "?"
                                    )
                                },
                                color = MaterialTheme.colorScheme.error,
                            )
                            if (t.audioPath != null || t.text.isNotBlank()) {
                                Button(onClick = viewModel::retry, enabled = t.audioPath != null) {
                                    Text(stringResource(R.string.action_retry))
                                }
                            }
                        }
                    }
                    if (t.text.isNotBlank()) {
                        Text(t.text)
                    }
                }
                else -> {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val p = progress
                            val label = when {
                                p?.converting == true -> stringResource(R.string.status_converting)
                                p != null -> stringResource(R.string.status_transcribing)
                                else -> stringResource(R.string.status_queued)
                            }
                            Text(label, style = MaterialTheme.typography.titleSmall)
                            if (p != null && p.fraction > 0f) {
                                LinearProgressIndicator(
                                    progress = { p.fraction },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                            p?.etaMs?.let { eta ->
                                Text(
                                    stringResource(R.string.detail_eta, formatClock(eta)),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                    // Streaming partial text as chunks land.
                    if (t.text.isNotBlank()) {
                        Text(t.text)
                    }
                }
            }
        }
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
                    Text(stringResource(R.string.action_delete))
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
private fun MetaLine(t: TranscriptEntity) {
    Row {
        Text(
            formatDate(t.createdAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (t.audioDurationMs > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                formatClock(t.audioDurationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            t.modelId,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (t.status == TranscriptStatus.DONE && t.audioDurationMs > 0 && t.processingTimeMs > 0) {
            Spacer(Modifier.width(8.dp))
            Text(
                String.format(
                    Locale.US, "%.2fx", t.processingTimeMs.toFloat() / t.audioDurationMs
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionRow(
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onExportTxt: () -> Unit,
    onExportSrt: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = onCopy) {
            Icon(Icons.Default.ContentCopy, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.action_copy))
        }
        FilledTonalButton(onClick = onShare) {
            Icon(Icons.Default.Share, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.action_share))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onExportTxt) { Text(stringResource(R.string.action_export_txt)) }
        OutlinedButton(onClick = onExportSrt) { Text(stringResource(R.string.action_export_srt)) }
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
