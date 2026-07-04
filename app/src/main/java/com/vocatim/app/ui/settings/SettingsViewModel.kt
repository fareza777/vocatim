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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val modelManager: ModelManager,
    private val userPrefs: UserPrefs,
    quotaStore: com.vocatim.app.data.billing.QuotaStore,
) : ViewModel() {

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
