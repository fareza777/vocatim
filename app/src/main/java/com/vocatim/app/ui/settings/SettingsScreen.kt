package com.vocatim.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Save
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.ListItem
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    onUpgradeClick: () -> Unit = {},
    onTrashClick: () -> Unit = {},
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
            val isPro by viewModel.isPro.collectAsStateWithLifecycle()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isPro, onClick = onUpgradeClick),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_unlimited_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Text(
                            if (isPro) stringResource(R.string.settings_unlimited_active)
                            else stringResource(R.string.settings_unlimited_upsell),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            com.vocatim.app.ui.common.ExpandableSection(
                title = stringResource(R.string.settings_model),
                icon = Icons.Default.GraphicEq,
                subtitle = com.vocatim.app.ui.common.modelDisplayName(s.selectedModelId),
            ) {
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
            val bench by viewModel.bench.collectAsStateWithLifecycle()
            Card {
                Column(modifier = Modifier.padding(8.dp)) {
                    visibleModels.forEachIndexed { index, model ->
                        ModelRow(
                            model = model,
                            state = modelStates[model] ?: ModelState.NotDownloaded,
                            selected = s.selectedModelId == model.id,
                            bench = bench[model],
                            onSelect = { viewModel.selectModel(model) },
                            onDownload = { viewModel.download(model) },
                            onCancel = { viewModel.cancelDownload(model) },
                            onDelete = { viewModel.delete(model) },
                            onBenchmark = { viewModel.benchmark(model) },
                        )
                        if (index < visibleModels.size - 1) HorizontalDivider()
                    }
                }
            }
            Text(
                stringResource(R.string.settings_model_ladder_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                stringResource(R.string.settings_english_engine),
                style = MaterialTheme.typography.titleSmall,
            )
            val parakeetState by viewModel.parakeetState.collectAsStateWithLifecycle()
            val parakeetBench by viewModel.parakeetBench.collectAsStateWithLifecycle()
            Card {
                ParakeetRow(
                    state = parakeetState,
                    selected = s.selectedModelId == com.vocatim.app.data.model.ParakeetModel.ID,
                    bench = parakeetBench,
                    onSelect = viewModel::selectParakeet,
                    onDownload = viewModel::downloadParakeet,
                    onCancel = viewModel::cancelParakeetDownload,
                    onDelete = viewModel::deleteParakeet,
                    onBenchmark = viewModel::benchmarkParakeet,
                )
            }
            val liveState by viewModel.liveCaptionState.collectAsStateWithLifecycle()
            Card {
                LiveCaptionRow(
                    state = liveState,
                    onDownload = viewModel::downloadLiveCaption,
                    onCancel = viewModel::cancelLiveCaptionDownload,
                    onDelete = viewModel::deleteLiveCaption,
                )
            }
            Text(
                stringResource(R.string.settings_diarize_section),
                style = MaterialTheme.typography.titleSmall,
            )
            val diarizeState by viewModel.diarizationState.collectAsStateWithLifecycle()
            Card {
                DiarizationRow(
                    state = diarizeState,
                    onDownload = viewModel::downloadDiarization,
                    onCancel = viewModel::cancelDiarizationDownload,
                    onDelete = viewModel::deleteDiarization,
                )
            }
            }

            com.vocatim.app.ui.common.ExpandableSection(
                title = stringResource(R.string.settings_section_language),
                icon = Icons.Default.Language,
                subtitle = languageLabel(s.language),
            ) {
            LanguagePickerSheet(current = s.language, onSelect = viewModel::selectLanguage)

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

            Text(stringResource(R.string.settings_accuracy), style = MaterialTheme.typography.titleMedium)
            var vocab by remember(s.customVocab) { mutableStateOf(s.customVocab) }
            androidx.compose.material3.OutlinedTextField(
                value = vocab,
                onValueChange = { vocab = it; viewModel.setCustomVocab(it) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                label = { Text(stringResource(R.string.settings_custom_vocab)) },
                placeholder = { Text(stringResource(R.string.settings_custom_vocab_hint)) },
            )
            Text(
                stringResource(R.string.settings_custom_vocab_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_high_accuracy))
                    Text(
                        stringResource(R.string.settings_high_accuracy_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = s.highAccuracy, onCheckedChange = viewModel::setHighAccuracy)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_vad))
                    Text(
                        stringResource(R.string.settings_vad_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = s.vadEnabled, onCheckedChange = viewModel::setVadEnabled)
            }
            }

            com.vocatim.app.ui.common.ExpandableSection(
                title = stringResource(R.string.settings_appearance),
                icon = Icons.Default.Palette,
            ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val modes = listOf(
                    com.vocatim.app.data.prefs.UserPrefs.THEME_SYSTEM to R.string.theme_system,
                    com.vocatim.app.data.prefs.UserPrefs.THEME_LIGHT to R.string.theme_light,
                    com.vocatim.app.data.prefs.UserPrefs.THEME_DARK to R.string.theme_dark,
                )
                modes.forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = s.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    ) { Text(stringResource(label)) }
                }
            }
            Text(
                stringResource(R.string.settings_accent),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                com.vocatim.app.ui.theme.Accent.entries.forEach { accent ->
                    val color = if (isSystemInDarkTheme()) accent.dark else accent.light
                    val isSelected = s.accent == accent.key
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(color)
                            .clickable { viewModel.setAccent(accent.key) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.settings_surface),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val dark = s.themeMode == com.vocatim.app.data.prefs.UserPrefs.THEME_DARK ||
                    (s.themeMode == com.vocatim.app.data.prefs.UserPrefs.THEME_SYSTEM &&
                        isSystemInDarkTheme())
                com.vocatim.app.ui.theme.SurfaceStyle.entries.forEach { style ->
                    SurfaceStyleCard(
                        style = style,
                        dark = dark,
                        selected = s.surfaceStyle == style.key,
                        accentKey = s.accent,
                        onClick = { viewModel.setSurfaceStyle(style.key) },
                    )
                }
            }
            Text(
                stringResource(R.string.settings_text_size),
                style = MaterialTheme.typography.titleSmall,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val scales = listOf(1.0f, 1.15f, 1.3f)
                scales.forEachIndexed { index, scale ->
                    SegmentedButton(
                        selected = kotlin.math.abs(s.textScale - scale) < 0.01f,
                        onClick = { viewModel.setTextScale(scale) },
                        shape = SegmentedButtonDefaults.itemShape(index, scales.size),
                    ) {
                        Text(
                            "A",
                            style = when (index) {
                                0 -> MaterialTheme.typography.bodySmall
                                1 -> MaterialTheme.typography.bodyMedium
                                else -> MaterialTheme.typography.bodyLarge
                            },
                        )
                    }
                }
            }
            }

            com.vocatim.app.ui.common.ExpandableSection(
                title = stringResource(R.string.settings_privacy),
                icon = Icons.Default.Lock,
            ) {
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
            }

            com.vocatim.app.ui.common.ExpandableSection(
                title = stringResource(R.string.settings_section_ai),
                icon = Icons.Default.AutoAwesome,
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_auto_summarize))
                    Text(
                        stringResource(R.string.settings_auto_summarize_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = s.autoSummarize, onCheckedChange = viewModel::setAutoSummarize)
            }
            Text(stringResource(R.string.settings_summary_model), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.settings_summary_model_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val summaryModelStates by viewModel.summaryModelStates.collectAsStateWithLifecycle()
            Card {
                Column(modifier = Modifier.padding(8.dp)) {
                    val models = com.vocatim.app.data.summary.SummaryModel.entries
                    models.forEachIndexed { index, model ->
                        SummaryModelRow(
                            model = model,
                            state = summaryModelStates[model] ?: ModelState.NotDownloaded,
                            selected = s.summaryModel == model.id,
                            onSelect = { viewModel.selectSummaryModel(model) },
                            onDownload = { viewModel.downloadSummaryModel(model) },
                            onCancel = { viewModel.cancelSummaryDownload(model) },
                            onDelete = { viewModel.deleteSummaryModel(model) },
                        )
                        if (index < models.size - 1) HorizontalDivider()
                    }
                }
            }

            Text(stringResource(R.string.settings_minutes_template), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.settings_minutes_template_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val templates = listOf(
                    com.vocatim.app.data.cloud.CloudPrompts.TEMPLATE_GENERAL to R.string.minutes_template_general,
                    com.vocatim.app.data.cloud.CloudPrompts.TEMPLATE_ONE_ON_ONE to R.string.minutes_template_one_on_one,
                    com.vocatim.app.data.cloud.CloudPrompts.TEMPLATE_INTERVIEW to R.string.minutes_template_interview,
                    com.vocatim.app.data.cloud.CloudPrompts.TEMPLATE_CUSTOM to R.string.minutes_template_custom,
                )
                templates.forEach { (id, label) ->
                    androidx.compose.material3.FilterChip(
                        selected = s.minutesTemplate == id,
                        onClick = { viewModel.setMinutesTemplate(id) },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            if (s.minutesTemplate == com.vocatim.app.data.cloud.CloudPrompts.TEMPLATE_CUSTOM) {
                var customPrompt by remember(s.customMinutesPrompt) {
                    mutableStateOf(s.customMinutesPrompt)
                }
                androidx.compose.material3.OutlinedTextField(
                    value = customPrompt,
                    onValueChange = { customPrompt = it; viewModel.setCustomMinutesPrompt(it) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = { Text(stringResource(R.string.minutes_template_custom)) },
                    placeholder = { Text(stringResource(R.string.settings_custom_prompt_hint)) },
                )
            }

            Text(stringResource(R.string.settings_cloud_section), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.settings_cloud_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CloudAiSection(viewModel)
            }

            com.vocatim.app.ui.common.ExpandableSection(
                title = stringResource(R.string.settings_section_data),
                icon = Icons.Default.Save,
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_compress_audio))
                    Text(
                        stringResource(R.string.settings_compress_audio_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = s.compressAudio, onCheckedChange = viewModel::setCompressAudio)
            }
            storage?.let { usage ->
                Text(
                    stringResource(
                        R.string.settings_storage_usage,
                        formatMb(usage.modelBytes),
                        formatMb(usage.audioBytes),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTrashClick),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.trash_title))
                    Text(
                        stringResource(R.string.trash_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(stringResource(R.string.settings_backup_section), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.settings_backup_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BackupSection(viewModel, onUpgrade = onUpgradeClick)
            }
        }
    }
}

@Composable
private fun CloudAiSection(viewModel: SettingsViewModel) {
    val saved by viewModel.cloudConfig.collectAsStateWithLifecycle()
    val cloudTest by viewModel.cloudTest.collectAsStateWithLifecycle()
    var baseUrl by remember(saved?.baseUrl) { mutableStateOf(saved?.baseUrl ?: "") }
    var apiKey by remember(saved?.apiKey) { mutableStateOf(saved?.apiKey ?: "") }
    var model by remember(saved?.model) { mutableStateOf(saved?.model ?: "") }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Provider presets fill the base URL; model stays user-typed.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            com.vocatim.app.data.cloud.CloudAiPrefs.PRESETS.forEach { (name, url) ->
                androidx.compose.material3.AssistChip(
                    onClick = { baseUrl = url },
                    label = { Text(name) },
                )
            }
        }
        androidx.compose.material3.OutlinedTextField(
            value = baseUrl,
            onValueChange = { baseUrl = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_cloud_base_url)) },
            placeholder = { Text("https://api.minimax.io/v1") },
        )
        androidx.compose.material3.OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_cloud_api_key)) },
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        )
        androidx.compose.material3.OutlinedTextField(
            value = model,
            onValueChange = { model = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.settings_cloud_model)) },
            placeholder = { Text("MiniMax-M3") },
        )
        val complete = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = complete,
                onClick = {
                    viewModel.saveCloudConfig(baseUrl, apiKey, model)
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.settings_cloud_saved),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                },
            ) { Text(stringResource(R.string.action_save)) }
            OutlinedButton(
                enabled = complete && cloudTest !is CloudTestState.Testing,
                onClick = { viewModel.testCloud(baseUrl, apiKey, model) },
            ) { Text(stringResource(R.string.settings_cloud_test)) }
            if (saved?.isConfigured == true) {
                OutlinedButton(onClick = viewModel::clearCloudConfig) {
                    Text(stringResource(R.string.settings_cloud_clear))
                }
            }
        }
        when (val t = cloudTest) {
            is CloudTestState.Testing -> Text(
                stringResource(R.string.settings_cloud_testing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            is CloudTestState.Ok -> Text(
                stringResource(R.string.settings_cloud_test_ok, t.latencyMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
            is CloudTestState.Error -> Text(
                stringResource(R.string.settings_cloud_test_fail, t.message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            CloudTestState.Idle -> Unit
        }
    }
}

@Composable
private fun BackupSection(viewModel: SettingsViewModel, onUpgrade: () -> Unit) {
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val backupEvent by viewModel.backupEvent.collectAsStateWithLifecycle()
    var pendingExportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(backupEvent) {
        backupEvent?.let { event ->
            val message = when (event) {
                is BackupEvent.ExportDone ->
                    context.getString(R.string.backup_export_ok, event.count)
                is BackupEvent.ImportDone ->
                    context.getString(R.string.backup_import_ok, event.count)
                is BackupEvent.Error ->
                    context.getString(R.string.backup_error, event.message)
            }
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            viewModel.consumeBackupEvent()
        }
    }

    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> uri?.let { pendingExportUri = it; password = "" } }
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { pendingImportUri = it; password = "" } }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            val date = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US)
                .format(java.util.Date())
            exportLauncher.launch("vocatim-backup-$date.vbk")
        }) {
            Text(stringResource(R.string.settings_backup))
        }
        OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
            Text(stringResource(R.string.settings_restore))
        }
    }

    // Weekly automatic backup into a user-chosen folder.
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val autoBackupOn = settings?.autoBackupUri?.isNotBlank() == true
    var pendingAutoTree by remember { mutableStateOf<android.net.Uri?>(null) }
    var autoPassword by remember { mutableStateOf("") }
    val treeLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { pendingAutoTree = it; autoPassword = "" } }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_auto_backup))
            Text(
                if (autoBackupOn && (settings?.autoBackupLast ?: 0L) > 0L) {
                    stringResource(
                        R.string.settings_auto_backup_last,
                        java.text.DateFormat.getDateInstance()
                            .format(java.util.Date(settings?.autoBackupLast ?: 0L)),
                    )
                } else {
                    stringResource(R.string.settings_auto_backup_desc)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = autoBackupOn,
            onCheckedChange = { on ->
                when {
                    on && !isPro -> onUpgrade()
                    on -> treeLauncher.launch(null)
                    else -> viewModel.disableAutoBackup()
                }
            },
        )
    }

    pendingAutoTree?.let { treeUri ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingAutoTree = null },
            title = { Text(stringResource(R.string.backup_password_title)) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = autoPassword,
                    onValueChange = { autoPassword = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.backup_password_hint)) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = autoPassword.length >= 4,
                    onClick = {
                        viewModel.enableAutoBackup(treeUri, autoPassword)
                        pendingAutoTree = null
                        autoPassword = ""
                    },
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingAutoTree = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    val activeUri = pendingExportUri ?: pendingImportUri
    if (activeUri != null) {
        val isExport = pendingExportUri != null
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingExportUri = null; pendingImportUri = null },
            title = { Text(stringResource(R.string.backup_password_title)) },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.backup_password_hint)) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    enabled = password.length >= 4,
                    onClick = {
                        if (isExport) viewModel.exportBackup(activeUri, password)
                        else viewModel.importBackup(activeUri, password)
                        pendingExportUri = null
                        pendingImportUri = null
                        password = ""
                    },
                ) {
                    Text(
                        stringResource(
                            if (isExport) R.string.settings_backup else R.string.settings_restore
                        )
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    pendingExportUri = null
                    pendingImportUri = null
                }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun ModelRow(
    model: WhisperModel,
    state: ModelState,
    selected: Boolean,
    bench: SettingsViewModel.Bench?,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onBenchmark: () -> Unit,
) {
    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                enabled = state is ModelState.Downloaded,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    com.vocatim.app.ui.common.modelDisplayName(model.id) +
                        " · " + formatMb(model.approxSizeBytes),
                    style = MaterialTheme.typography.titleSmall,
                )
                com.vocatim.app.ui.common.StarMeter(
                    stringResource(R.string.stars_speed), model.speedStars
                )
                com.vocatim.app.ui.common.StarMeter(
                    stringResource(R.string.stars_accuracy), model.accuracyStars
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
        if (state is ModelState.Downloaded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.TextButton(
                    onClick = onBenchmark,
                    enabled = bench != SettingsViewModel.Bench.Running,
                ) { Text(stringResource(R.string.settings_speed_test)) }
                when (bench) {
                    SettingsViewModel.Bench.Running -> Text(
                        stringResource(R.string.settings_speed_running),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    is SettingsViewModel.Bench.Done -> Text(
                        stringResource(
                            R.string.settings_speed_result,
                            String.format(java.util.Locale.US, "%.2f", bench.rtf),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (bench.rtf <= 1f) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.tertiary,
                    )
                    SettingsViewModel.Bench.Failed -> Text(
                        stringResource(R.string.settings_speed_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    null -> Unit
                }
            }
        }
    }
}

/** The NVIDIA Parakeet English engine: same row anatomy as ModelRow, but a
 *  multi-file bundle managed by its own manager. */
@Composable
private fun ParakeetRow(
    state: ModelState,
    selected: Boolean,
    bench: SettingsViewModel.Bench?,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
    onBenchmark: () -> Unit,
) {
    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                enabled = state is ModelState.Downloaded,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.model_name_parakeet) + " · " +
                        formatMb(com.vocatim.app.data.model.ParakeetModel.totalBytes),
                    style = MaterialTheme.typography.titleSmall,
                )
                com.vocatim.app.ui.common.StarMeter(
                    stringResource(R.string.stars_speed),
                    com.vocatim.app.data.model.ParakeetModel.SPEED_STARS,
                )
                com.vocatim.app.ui.common.StarMeter(
                    stringResource(R.string.stars_accuracy),
                    com.vocatim.app.data.model.ParakeetModel.ACCURACY_STARS,
                )
                Text(
                    stringResource(R.string.parakeet_note),
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
        if (state is ModelState.Downloaded) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.TextButton(
                    onClick = onBenchmark,
                    enabled = bench != SettingsViewModel.Bench.Running,
                ) { Text(stringResource(R.string.settings_speed_test)) }
                when (bench) {
                    SettingsViewModel.Bench.Running -> Text(
                        stringResource(R.string.settings_speed_running),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    is SettingsViewModel.Bench.Done -> Text(
                        stringResource(
                            R.string.settings_speed_result,
                            String.format(java.util.Locale.US, "%.2f", bench.rtf),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (bench.rtf <= 1f) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.tertiary,
                    )
                    SettingsViewModel.Bench.Failed -> Text(
                        stringResource(R.string.settings_speed_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    null -> Unit
                }
            }
        }
    }
}

/** Speaker-detection bundle: like LiveCaptionRow, presence-only (no radio). */
@Composable
private fun DiarizationRow(
    state: ModelState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    stringResource(R.string.diarize_model_name) + " · " +
                        formatMb(com.vocatim.app.data.model.DiarizationModel.totalBytes),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.diarize_model_note),
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

/** Streaming caption model for the Live recording mode. Not a transcription
 *  engine choice — no radio; it just needs to be present on disk. */
@Composable
private fun LiveCaptionRow(
    state: ModelState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    stringResource(R.string.live_model_name) + " · " +
                        formatMb(com.vocatim.app.data.model.LiveCaptionModel.totalBytes),
                    style = MaterialTheme.typography.titleSmall,
                )
                com.vocatim.app.ui.common.StarMeter(
                    stringResource(R.string.stars_speed),
                    com.vocatim.app.data.model.LiveCaptionModel.SPEED_STARS,
                )
                com.vocatim.app.ui.common.StarMeter(
                    stringResource(R.string.stars_accuracy),
                    com.vocatim.app.data.model.LiveCaptionModel.ACCURACY_STARS,
                )
                Text(
                    stringResource(R.string.live_model_note),
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
private fun SummaryModelRow(
    model: com.vocatim.app.data.summary.SummaryModel,
    state: ModelState,
    selected: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val supported = com.vocatim.app.data.summary.ContextBudget.supports(
        androidx.compose.ui.platform.LocalContext.current, model
    )
    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = selected,
                onClick = onSelect,
                enabled = supported && state is ModelState.Downloaded,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(
                        when (model) {
                            com.vocatim.app.data.summary.SummaryModel.QWEN25 ->
                                R.string.summary_model_qwen25
                            com.vocatim.app.data.summary.SummaryModel.GEMMA3_1B ->
                                R.string.summary_model_gemma3_1b
                            com.vocatim.app.data.summary.SummaryModel.QWEN3 ->
                                R.string.summary_model_qwen3
                            com.vocatim.app.data.summary.SummaryModel.QWEN3_4B ->
                                R.string.summary_model_qwen3_4b
                            com.vocatim.app.data.summary.SummaryModel.GEMMA3_4B ->
                                R.string.summary_model_gemma3_4b
                            com.vocatim.app.data.summary.SummaryModel.QWEN3_8B ->
                                R.string.summary_model_qwen3_8b
                        }
                    ) + " · " + formatMb(model.approxSizeBytes),
                    style = MaterialTheme.typography.titleSmall,
                )
                com.vocatim.app.ui.common.StarMeter(
                    stringResource(R.string.stars_speed), model.speedStars
                )
                com.vocatim.app.ui.common.StarMeter(
                    stringResource(R.string.stars_quality), model.qualityStars
                )
                if (!supported) {
                    Text(
                        stringResource(
                            if (model.minTotalRamMb >= 10_000) R.string.summary_model_needs_ram_12
                            else R.string.summary_model_needs_ram
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            when (state) {
                is ModelState.NotDownloaded ->
                    Button(onClick = onDownload, enabled = supported) {
                        Text(stringResource(R.string.debug_download))
                    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguagePickerSheet(current: String, onSelect: (String) -> Unit) {
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showSheet = true },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        ListItem(
            headlineContent = { Text(languageLabel(current)) },
            supportingContent = { Text(stringResource(R.string.settings_language)) },
            leadingContent = {
                Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            trailingContent = {
                Text(
                    stringResource(R.string.settings_change),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
            },
        )
    }
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
        ) {
            LazyColumn(modifier = Modifier.fillMaxHeight(0.6f)) {
                items(LANGUAGES) { code ->
                    ListItem(
                        headlineContent = { Text(languageLabel(code)) },
                        modifier = Modifier.clickable {
                            onSelect(code)
                            showSheet = false
                        },
                        trailingContent = {
                            if (current == code) {
                                RadioButton(selected = true, onClick = null)
                            }
                        },
                    )
                    HorizontalDivider()
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

/** Mini live preview of one surface palette: background, a card, an accent
 *  dot, and two text lines — enough to judge the mood before applying. */
@Composable
private fun SurfaceStyleCard(
    style: com.vocatim.app.ui.theme.SurfaceStyle,
    dark: Boolean,
    selected: Boolean,
    accentKey: String,
    onClick: () -> Unit,
) {
    val bg = if (dark) style.darkBg else style.lightBg
    val card = if (dark) style.darkCard else style.lightCard
    val border = if (dark) style.darkBorder else style.lightBorder
    val accent = com.vocatim.app.ui.theme.Accent.fromKey(accentKey)
        .let { if (dark) it.dark else it.light }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(width = 74.dp, height = 92.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(bg)
                .then(
                    if (selected) {
                        Modifier.border(
                            2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium
                        )
                    } else {
                        Modifier.border(1.dp, border, MaterialTheme.shapes.medium)
                    }
                )
                .clickable(onClick = onClick)
                .padding(8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(accent),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(card),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(5.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(border),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(5.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(border),
                )
            }
        }
        Text(
            style.key.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

private fun formatMb(bytes: Long): String = "${bytes / (1024 * 1024)} MB"
