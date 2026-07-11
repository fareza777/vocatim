package com.vocatim.app.ui.settings

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vocatim.app.data.model.ModelManager
import com.vocatim.app.data.model.ModelState
import com.vocatim.app.data.model.WhisperModel
import com.vocatim.app.data.prefs.UserPrefs
import com.vocatim.app.data.prefs.UserSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class StorageUsage(val modelBytes: Long, val audioBytes: Long)

sealed interface BackupEvent {
    data class ExportDone(val count: Int) : BackupEvent
    data class ImportDone(val count: Int) : BackupEvent
    data class Error(val message: String) : BackupEvent
}

sealed interface CloudTestState {
    data object Idle : CloudTestState
    data object Testing : CloudTestState
    data class Ok(val latencyMs: Long) : CloudTestState
    data class Error(val message: String) : CloudTestState
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val modelManager: ModelManager,
    private val parakeetManager: com.vocatim.app.data.model.ParakeetModelManager,
    private val liveCaptionManager: com.vocatim.app.data.model.LiveCaptionModelManager,
    private val diarizationManager: com.vocatim.app.data.model.DiarizationModelManager,
    private val summaryModelManager: com.vocatim.app.data.summary.SummaryModelManager,
    private val userPrefs: UserPrefs,
    private val backupManager: com.vocatim.app.data.backup.BackupManager,
    private val cloudAiPrefs: com.vocatim.app.data.cloud.CloudAiPrefs,
    private val cloudClient: com.vocatim.app.data.cloud.CloudAiClient,
    private val transcriber: com.vocatim.app.data.transcribe.WhisperTranscriber,
    quotaStore: com.vocatim.app.data.billing.QuotaStore,
) : ViewModel() {

    /** Realtime-factor benchmark per model: null=idle, running, or a value. */
    sealed interface Bench {
        data object Running : Bench
        data class Done(val rtf: Float) : Bench
        data object Failed : Bench
    }

    private val _bench = MutableStateFlow<Map<WhisperModel, Bench>>(emptyMap())
    val bench: StateFlow<Map<WhisperModel, Bench>> = _bench.asStateFlow()

    /** Transcribes a short synthetic clip to measure this phone's speed. */
    fun benchmark(model: WhisperModel) {
        if (_bench.value[model] == Bench.Running) return
        _bench.value = _bench.value + (model to Bench.Running)
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val seconds = 6
                    val samples = FloatArray(seconds * 16_000) {
                        (Math.random().toFloat() - 0.5f) * 0.02f
                    }
                    val start = android.os.SystemClock.elapsedRealtime()
                    transcriber.transcribe(model, samples, language = "en")
                    val elapsed = android.os.SystemClock.elapsedRealtime() - start
                    elapsed / (seconds * 1000f)
                }.getOrNull()
            }
            _bench.value = _bench.value +
                (model to (result?.let { Bench.Done(it) } ?: Bench.Failed))
        }
    }

    val cloudConfig: StateFlow<com.vocatim.app.data.cloud.CloudAiConfig?> =
        cloudAiPrefs.config
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun saveCloudConfig(baseUrl: String, apiKey: String, model: String) {
        viewModelScope.launch { cloudAiPrefs.save(baseUrl, apiKey, model) }
    }

    fun clearCloudConfig() {
        viewModelScope.launch { cloudAiPrefs.clear() }
    }

    private val _cloudTest = MutableStateFlow<CloudTestState>(CloudTestState.Idle)
    val cloudTest: StateFlow<CloudTestState> = _cloudTest.asStateFlow()

    /** Sends a tiny prompt to verify the BYOK endpoint/key/model work. */
    fun testCloud(baseUrl: String, apiKey: String, model: String) {
        viewModelScope.launch {
            _cloudTest.value = CloudTestState.Testing
            val start = System.currentTimeMillis()
            _cloudTest.value = try {
                cloudClient.ping(
                    com.vocatim.app.data.cloud.CloudAiConfig(
                        baseUrl.trim(), apiKey.trim(), model.trim()
                    )
                )
                CloudTestState.Ok(System.currentTimeMillis() - start)
            } catch (e: Exception) {
                CloudTestState.Error(e.message ?: "failed")
            }
        }
    }

    fun resetCloudTest() {
        _cloudTest.value = CloudTestState.Idle
    }

    val summaryModelStates:
        StateFlow<Map<com.vocatim.app.data.summary.SummaryModel, ModelState>> =
        combine(
            com.vocatim.app.data.summary.SummaryModel.entries
                .map { summaryModelManager.state(it) }
        ) { states ->
            com.vocatim.app.data.summary.SummaryModel.entries
                .zip(states.toList()).toMap()
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            com.vocatim.app.data.summary.SummaryModel.entries
                .associateWith { summaryModelManager.state(it).value },
        )

    private val summaryDownloadJobs =
        mutableMapOf<com.vocatim.app.data.summary.SummaryModel, Job>()

    fun selectSummaryModel(model: com.vocatim.app.data.summary.SummaryModel) {
        viewModelScope.launch { userPrefs.setSummaryModel(model.id) }
    }

    fun downloadSummaryModel(model: com.vocatim.app.data.summary.SummaryModel) {
        if (summaryDownloadJobs[model]?.isActive == true) return
        summaryDownloadJobs[model] = viewModelScope.launch {
            try {
                summaryModelManager.download(model)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // ModelState.Failed carries the message.
            }
            refreshStorage()
        }
    }

    fun cancelSummaryDownload(model: com.vocatim.app.data.summary.SummaryModel) {
        summaryDownloadJobs.remove(model)?.cancel()
    }

    fun deleteSummaryModel(model: com.vocatim.app.data.summary.SummaryModel) {
        summaryModelManager.delete(model)
        refreshStorage()
    }

    private val _backupEvent = MutableStateFlow<BackupEvent?>(null)
    val backupEvent: StateFlow<BackupEvent?> = _backupEvent.asStateFlow()

    fun exportBackup(uri: android.net.Uri, password: String) {
        viewModelScope.launch {
            _backupEvent.value = try {
                BackupEvent.ExportDone(backupManager.export(uri, password.toCharArray()))
            } catch (e: Exception) {
                BackupEvent.Error(e.message ?: "backup failed")
            }
        }
    }

    fun importBackup(uri: android.net.Uri, password: String) {
        viewModelScope.launch {
            _backupEvent.value = try {
                BackupEvent.ImportDone(backupManager.import(uri, password.toCharArray()))
            } catch (e: Exception) {
                BackupEvent.Error(e.message ?: "restore failed")
            }
        }
    }

    fun consumeBackupEvent() {
        _backupEvent.value = null
    }

    val isPro: StateFlow<Boolean> = quotaStore.isProCached
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val settings: StateFlow<UserSettings?> =
        userPrefs.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val modelStates: StateFlow<Map<WhisperModel, ModelState>> =
        combine(WhisperModel.entries.map { modelManager.state(it) }) { states ->
            WhisperModel.entries.zip(states.toList()).toMap()
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            WhisperModel.entries.associateWith { modelManager.state(it).value },
        )

    private val _storage = MutableStateFlow<StorageUsage?>(null)
    val storage: StateFlow<StorageUsage?> = _storage.asStateFlow()

    /** Low-RAM devices should stick to tiny; shown as a recommendation. */
    val isLowRamDevice: Boolean =
        appContext.getSystemService<ActivityManager>()?.isLowRamDevice ?: false

    val biometricAvailable: Boolean =
        androidx.biometric.BiometricManager.from(appContext).canAuthenticate(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS

    private val downloadJobs = mutableMapOf<WhisperModel, Job>()

    init {
        refreshStorage()
    }

    fun selectModel(model: WhisperModel) {
        viewModelScope.launch { userPrefs.setModel(model) }
    }

    // --- Parakeet (English engine, parallel to whisper) ---

    val parakeetState: StateFlow<ModelState> = parakeetManager.state

    private var parakeetJob: Job? = null

    fun selectParakeet() {
        viewModelScope.launch {
            userPrefs.setModelId(com.vocatim.app.data.model.ParakeetModel.ID)
        }
    }

    fun downloadParakeet() {
        if (parakeetJob?.isActive == true) return
        parakeetJob = viewModelScope.launch {
            try {
                parakeetManager.download()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // ModelState.Failed carries the message.
            }
            refreshStorage()
        }
    }

    fun cancelParakeetDownload() {
        parakeetJob?.cancel()
        parakeetJob = null
    }

    // --- Live caption model (streaming, powers the Live recording mode) ---

    val liveCaptionState: StateFlow<ModelState> = liveCaptionManager.state

    private var liveCaptionJob: Job? = null

    fun downloadLiveCaption() {
        if (liveCaptionJob?.isActive == true) return
        liveCaptionJob = viewModelScope.launch {
            try {
                liveCaptionManager.download()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // ModelState.Failed carries the message.
            }
            refreshStorage()
        }
    }

    fun cancelLiveCaptionDownload() {
        liveCaptionJob?.cancel()
        liveCaptionJob = null
    }

    fun deleteLiveCaption() {
        liveCaptionManager.delete()
        refreshStorage()
    }

    // --- Speaker detection (diarization) model ---

    val diarizationState: StateFlow<ModelState> = diarizationManager.state

    private var diarizationJob: Job? = null

    fun downloadDiarization() {
        if (diarizationJob?.isActive == true) return
        diarizationJob = viewModelScope.launch {
            try {
                diarizationManager.download()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // ModelState.Failed carries the message.
            }
            refreshStorage()
        }
    }

    fun cancelDiarizationDownload() {
        diarizationJob?.cancel()
        diarizationJob = null
    }

    fun deleteDiarization() {
        diarizationManager.delete()
        refreshStorage()
    }

    fun deleteParakeet() {
        parakeetManager.delete()
        viewModelScope.launch {
            // Don't leave the selection pointing at a deleted engine.
            if (userPrefs.current().selectedModelId ==
                com.vocatim.app.data.model.ParakeetModel.ID
            ) {
                userPrefs.setModel(WhisperModel.TINY)
            }
        }
        refreshStorage()
    }

    fun selectLanguage(code: String) {
        viewModelScope.launch { userPrefs.setLanguage(code) }
    }

    fun setTranslate(enabled: Boolean) {
        viewModelScope.launch { userPrefs.setTranslate(enabled) }
    }

    fun setThreads(threads: Int) {
        viewModelScope.launch { userPrefs.setThreads(threads) }
    }

    fun setAppLock(enabled: Boolean) {
        viewModelScope.launch { userPrefs.setAppLock(enabled) }
    }

    fun setBlockScreenshots(enabled: Boolean) {
        viewModelScope.launch { userPrefs.setBlockScreenshots(enabled) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch { userPrefs.setThemeMode(mode) }
    }

    fun setTextScale(scale: Float) {
        viewModelScope.launch { userPrefs.setTextScale(scale) }
    }

    fun setAccent(key: String) {
        viewModelScope.launch { userPrefs.setAccent(key) }
    }

    fun setSurfaceStyle(key: String) {
        viewModelScope.launch { userPrefs.setSurfaceStyle(key) }
    }

    fun setCustomVocab(text: String) {
        viewModelScope.launch { userPrefs.setCustomVocab(text) }
    }

    fun setHighAccuracy(enabled: Boolean) {
        viewModelScope.launch { userPrefs.setHighAccuracy(enabled) }
    }

    fun setVadEnabled(enabled: Boolean) {
        viewModelScope.launch { userPrefs.setVadEnabled(enabled) }
    }

    fun setAutoSummarize(enabled: Boolean) {
        viewModelScope.launch { userPrefs.setAutoSummarize(enabled) }
    }

    fun setMinutesTemplate(template: String) {
        viewModelScope.launch { userPrefs.setMinutesTemplate(template) }
    }

    fun setCustomMinutesPrompt(prompt: String) {
        viewModelScope.launch { userPrefs.setCustomMinutesPrompt(prompt) }
    }

    fun setCompressAudio(enabled: Boolean) {
        viewModelScope.launch { userPrefs.setCompressAudio(enabled) }
    }

    /** Persists folder access + keystore-wrapped password for weekly backups. */
    fun enableAutoBackup(uri: android.net.Uri, password: String) {
        viewModelScope.launch {
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            val encrypted = com.vocatim.app.data.backup.KeystoreCrypto.encrypt(password)
                ?: return@launch
            userPrefs.setAutoBackup(uri.toString(), encrypted)
        }
    }

    fun disableAutoBackup() {
        viewModelScope.launch { userPrefs.clearAutoBackup() }
    }

    fun download(model: WhisperModel) {
        if (downloadJobs[model]?.isActive == true) return
        downloadJobs[model] = viewModelScope.launch {
            try {
                modelManager.download(model)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // ModelState.Failed carries the message.
            }
            refreshStorage()
        }
    }

    fun cancelDownload(model: WhisperModel) {
        downloadJobs.remove(model)?.cancel()
    }

    fun delete(model: WhisperModel) {
        modelManager.delete(model)
        refreshStorage()
    }

    private fun refreshStorage() {
        viewModelScope.launch {
            _storage.value = withContext(Dispatchers.IO) {
                StorageUsage(
                    modelBytes = dirSize(File(appContext.filesDir, "models")),
                    audioBytes = dirSize(File(appContext.filesDir, "recordings")) +
                        dirSize(File(appContext.filesDir, "imports")),
                )
            }
        }
    }

    private fun dirSize(dir: File): Long =
        dir.listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L
}
