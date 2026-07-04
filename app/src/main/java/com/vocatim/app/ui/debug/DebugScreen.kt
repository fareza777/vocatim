package com.vocatim.app.ui.debug

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocatim.app.BuildConfig
import com.vocatim.app.R
import com.vocatim.app.data.model.ModelState
import com.vocatim.app.data.model.WhisperModel
import java.util.Locale

private val LANGUAGES = listOf("auto", "id", "en")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(viewModel: DebugViewModel = hiltViewModel()) {
    val modelStates by viewModel.modelStates.collectAsStateWithLifecycle()
    val selectedModel by viewModel.selectedModel.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val transcription by viewModel.transcription.collectAsStateWithLifecycle()

    val pickWav = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.transcribeWav(it) }
    }

    val systemInfo by viewModel.systemInfo.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(stringResource(R.string.debug_title) + " v" + BuildConfig.VERSION_NAME)
            })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.debug_section_model), style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                WhisperModel.entries.forEachIndexed { index, model ->
                    SegmentedButton(
                        selected = selectedModel == model,
                        onClick = { viewModel.selectModel(model) },
                        shape = SegmentedButtonDefaults.itemShape(index, WhisperModel.entries.size),
                    ) {
                        Text(model.id)
                    }
                }
            }

            ModelStatusCard(
                model = selectedModel,
                state = modelStates[selectedModel] ?: ModelState.NotDownloaded,
                onDownload = viewModel::downloadSelectedModel,
                onCancel = viewModel::cancelDownload,
                onDelete = { viewModel.deleteModel(selectedModel) },
            )

            Text(stringResource(R.string.debug_section_language), style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                LANGUAGES.forEachIndexed { index, code ->
                    SegmentedButton(
                        selected = language == code,
                        onClick = { viewModel.selectLanguage(code) },
                        shape = SegmentedButtonDefaults.itemShape(index, LANGUAGES.size),
                    ) {
                        Text(code)
                    }
                }
            }

            Button(
                onClick = { pickWav.launch(arrayOf("audio/x-wav", "audio/wav", "audio/*")) },
                modifier = Modifier.fillMaxWidth(),
                enabled = modelStates[selectedModel] == ModelState.Downloaded &&
                    transcription !is TranscriptionUiState.Preparing &&
                    transcription !is TranscriptionUiState.Transcribing,
            ) {
                Text(stringResource(R.string.debug_pick_wav))
            }

            TranscriptionResult(transcription)

            if (systemInfo.isNotEmpty()) {
                Text(
                    text = systemInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun ModelStatusCard(
    model: WhisperModel,
    state: ModelState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state) {
                is ModelState.NotDownloaded -> {
                    Text(
                        stringResource(
                            R.string.debug_model_not_downloaded,
                            model.fileName,
                            model.approxSizeBytes / (1024 * 1024),
                        )
                    )
                    Button(onClick = onDownload) {
                        Text(stringResource(R.string.debug_download))
                    }
                }
                is ModelState.Downloading -> {
                    val totalMb = state.totalBytes / (1024 * 1024)
                    val doneMb = state.downloadedBytes / (1024 * 1024)
                    Text(stringResource(R.string.debug_downloading, doneMb, totalMb))
                    if (state.totalBytes > 0) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedButton(onClick = onCancel) {
                        Text(stringResource(R.string.debug_cancel))
                    }
                }
                is ModelState.Downloaded -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.debug_model_ready, model.fileName),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = stringResource(R.string.debug_delete_model),
                            )
                        }
                    }
                }
                is ModelState.Failed -> {
                    Text(
                        stringResource(R.string.debug_download_failed, state.message),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = onDownload) {
                        Text(stringResource(R.string.debug_retry))
                    }
                }
            }
        }
    }
}

@Composable
private fun TranscriptionResult(state: TranscriptionUiState) {
    when (state) {
        is TranscriptionUiState.Idle -> Unit
        is TranscriptionUiState.Preparing -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.debug_preparing))
            }
        }
        is TranscriptionUiState.Transcribing -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(
                        R.string.debug_transcribing,
                        formatDuration(state.audioDurationMs),
                    )
                )
            }
        }
        is TranscriptionUiState.Success -> {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(
                            R.string.debug_result_stats,
                            formatDuration(state.audioDurationMs),
                            formatDuration(state.processingTimeMs),
                            String.format(Locale.US, "%.2f", state.realtimeFactor),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    SelectionContainer {
                        Text(state.text.ifBlank { stringResource(R.string.debug_result_empty) })
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        is TranscriptionUiState.Error -> {
            val message = if (state.message == "MODEL_NOT_DOWNLOADED") {
                stringResource(R.string.debug_error_no_model)
            } else {
                stringResource(R.string.debug_error, state.message)
            }
            Text(message, color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
