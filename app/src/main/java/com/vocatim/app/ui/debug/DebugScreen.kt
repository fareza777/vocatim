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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    val benchmarkResults by viewModel.benchmarkResults.collectAsStateWithLifecycle()

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

            BenchmarkSection(
                results = benchmarkResults,
                onRun = viewModel::runBenchmark,
            )

            val devPro by viewModel.devPro.collectAsStateWithLifecycle()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Skip ads (debug)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Hides ads without buying Remove ads. Release builds ignore this.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                androidx.compose.material3.Switch(
                    checked = devPro,
                    onCheckedChange = viewModel::setDevPro,
                )
            }

            AdsDiagnosticsCard(viewModel)

            var diagText by remember { mutableStateOf<String?>(null) }
            OutlinedButton(onClick = { diagText = viewModel.readSummaryDiag() }) {
                Text("Show AI summary diagnostic")
            }
            diagText?.let { text ->
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

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

/**
 * Live readout of every stage that can silently suppress ads. A screenshot of
 * this card replaces a logcat session when diagnosing "no ads" reports.
 */
@Composable
private fun AdsDiagnosticsCard(viewModel: DebugViewModel) {
    val breakdown by viewModel.adFreeBreakdown.collectAsStateWithLifecycle()
    val ready by viewModel.adsReady.collectAsStateWithLifecycle()
    val d = viewModel.adsDiagnostics
    val blocked by d.blockedByEntitlement.collectAsStateWithLifecycle()
    val started by d.controllerStarted.collectAsStateWithLifecycle()
    val canRequest by d.consentCanRequestAds.collectAsStateWithLifecycle()
    val consentErr by d.lastConsentError.collectAsStateWithLifecycle()
    val bannerLoaded by d.bannerLoaded.collectAsStateWithLifecycle()
    val bannerErr by d.lastBannerError.collectAsStateWithLifecycle()
    val interLoaded by d.interstitialLoaded.collectAsStateWithLifecycle()
    val interErr by d.lastInterstitialError.collectAsStateWithLifecycle()
    val decision by d.lastShowDecision.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Ads diagnostics", style = MaterialTheme.typography.titleMedium)
        Card {
            SelectionContainer {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    val lines = listOf(
                        "ad units: " + if (BuildConfig.ADMOB_TEST_IDS) "TEST" else "PRODUCTION",
                        "banner id: " + BuildConfig.ADMOB_BANNER_UNIT_ID,
                        "interstitial id: " + BuildConfig.ADMOB_INTERSTITIAL_UNIT_ID,
                        "entitlement: removeAds=${breakdown?.removeAds} " +
                            "legacyPro=${breakdown?.legacyPro} " +
                            "devOverride=${breakdown?.devOverride}",
                        "controller: started=$started blockedByEntitlement=$blocked",
                        "consent: canRequestAds=$canRequest error=${consentErr ?: "-"}",
                        "sdk ready: $ready",
                        "banner: loaded=$bannerLoaded error=${bannerErr ?: "-"}",
                        "interstitial: loaded=$interLoaded error=${interErr ?: "-"}",
                        "last show decision: ${decision ?: "-"}",
                    )
                    lines.forEach {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Text(
            "error 'code 3' = NO_FILL dari AdMob; 'code 0' = masalah jaringan/SDK. " +
                "Screenshot kartu ini saat iklan tidak tampil.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BenchmarkSection(
    results: Map<String, Float?>,
    onRun: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.debug_benchmark_title),
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedButton(onClick = onRun) {
            Text(stringResource(R.string.debug_benchmark_run))
        }
        results.forEach { (modelId, rtf) ->
            Text(
                if (rtf == null) {
                    stringResource(R.string.debug_benchmark_running, modelId)
                } else {
                    stringResource(
                        R.string.debug_benchmark_result,
                        modelId,
                        String.format(Locale.US, "%.2f", rtf),
                    )
                },
                style = MaterialTheme.typography.bodySmall,
            )
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
