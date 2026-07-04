package com.vocatim.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vocatim.app.R
import com.vocatim.app.data.model.ModelState
import com.vocatim.app.data.model.WhisperModel

private val LANGUAGES = listOf(
    "auto", "id", "en", "ms", "ar", "zh", "ja", "ko", "es", "fr", "de", "ru", "pt", "hi"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val modelStates by viewModel.modelStates.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val s = settings ?: return@Scaffold
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.settings_model), style = MaterialTheme.typography.titleMedium)
            if (viewModel.isLowRamDevice) {
                Text(
                    stringResource(R.string.settings_low_ram_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
            // Full small is hidden: q5_1 matches its quality at 2.4x less
            // download. Still listed while present on disk so it can be
            // deleted or kept in use.
            val visibleModels = WhisperModel.entries.filter { model ->
                model != WhisperModel.SMALL ||
                    modelStates[model] is ModelState.Downloaded ||
                    modelStates[model] is ModelState.Downloading ||
                    s.model == model
            }
            Card {
                Column(modifier = Modifier.padding(8.dp)) {
                    visibleModels.forEachIndexed { index, model ->
                        ModelRow(
                            model = model,
                            state = modelStates[model] ?: ModelState.NotDownloaded,
                            selected = s.model == model,
                            onSelect = { viewModel.selectModel(model) },
                            onDownload = { viewModel.download(model) },
                            onCancel = { viewModel.cancelDownload(model) },
                            onDelete = { viewModel.delete(model) },
                        )
                        if (index < visibleModels.size - 1) HorizontalDivider()
                    }
                }
            }

            Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
            LanguagePicker(current = s.language, onSelect = viewModel::selectLanguage)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_translate))
                    Text(
                        stringResource(R.string.settings_translate_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = s.translate, onCheckedChange = viewModel::setTranslate)
            }

            Text(stringResource(R.string.settings_threads), style = MaterialTheme.typography.titleMedium)
            Column {
                Text(
                    if (s.threads == 0) stringResource(R.string.settings_threads_auto)
                    else s.threads.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = s.threads.toFloat(),
                    onValueChange = { viewModel.setThreads(it.toInt()) },
                    valueRange = 0f..8f,
                    steps = 7,
                )
            }

            Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_app_lock))
                    Text(
                        if (viewModel.biometricAvailable) {
                            stringResource(R.string.settings_app_lock_desc)
                        } else {
                            stringResource(R.string.settings_app_lock_unavailable)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = s.appLock,
                    onCheckedChange = viewModel::setAppLock,
                    enabled = viewModel.biometricAvailable,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_block_screenshots))
                    Text(
                        stringResource(R.string.settings_block_screenshots_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = s.blockScreenshots, onCheckedChange = viewModel::setBlockScreenshots)
            }

            Text(stringResource(R.string.settings_storage), style = MaterialTheme.typography.titleMedium)
            storage?.let { usage ->
                Text(
                    stringResource(
                        R.string.settings_storage_usage,
                        formatMb(usage.modelBytes),
                        formatMb(usage.audioBytes),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun ModelRow(
    model: WhisperModel,
    state: ModelState,
    selected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                enabled = state is ModelState.Downloaded,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(model.id, style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(speedLabel(model)) + " · " +
                        formatMb(model.approxSizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (state) {
                is ModelState.NotDownloaded ->
                    Button(onClick = onDownload) { Text(stringResource(R.string.debug_download)) }
                is ModelState.Downloading ->
                    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.debug_cancel)) }
                is ModelState.Downloaded ->
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.debug_delete_model),
                        )
                    }
                is ModelState.Failed ->
                    Button(onClick = onDownload) { Text(stringResource(R.string.debug_retry)) }
            }
        }
        if (state is ModelState.Downloading) {
            if (state.totalBytes > 0) {
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        if (state is ModelState.Failed) {
            Text(
                state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun LanguagePicker(current: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    languageLabel(current),
                    modifier = Modifier.weight(1f).padding(8.dp),
                )
                OutlinedButton(onClick = { expanded = !expanded }) {
                    Text(stringResource(R.string.settings_change))
                }
            }
            if (expanded) {
                LANGUAGES.forEach { code ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = current == code,
                            onClick = {
                                onSelect(code)
                                expanded = false
                            },
                        )
                        Text(languageLabel(code))
                    }
                }
            }
        }
    }
}

@Composable
private fun languageLabel(code: String): String = when (code) {
    "auto" -> stringResource(R.string.lang_auto)
    else -> java.util.Locale(code).let { locale ->
        locale.getDisplayLanguage(locale).replaceFirstChar { it.uppercase() } + " ($code)"
    }
}

private fun speedLabel(model: WhisperModel): Int = when (model) {
    WhisperModel.TINY -> R.string.model_speed_tiny
    WhisperModel.BASE -> R.string.model_speed_base
    WhisperModel.SMALL -> R.string.model_speed_small
    WhisperModel.SMALL_Q5 -> R.string.model_speed_small_q5
}

private fun formatMb(bytes: Long): String = "${bytes / (1024 * 1024)} MB"
