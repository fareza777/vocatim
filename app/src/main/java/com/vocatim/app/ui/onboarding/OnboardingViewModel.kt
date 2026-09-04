package com.vocatim.app.ui.onboarding

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vocatim.app.data.model.ModelManager
import com.vocatim.app.data.model.ModelState
import com.vocatim.app.data.model.ParakeetModel
import com.vocatim.app.data.model.ParakeetModelManager
import com.vocatim.app.data.model.WhisperModel
import com.vocatim.app.data.prefs.UserPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext appContext: Context,
    private val userPrefs: UserPrefs,
    private val modelManager: ModelManager,
    private val parakeetManager: ParakeetModelManager,
    private val cloudTranscribePrefs: com.vocatim.app.data.cloud.CloudTranscribePrefs,
    private val onboardingGate: com.vocatim.app.data.prefs.OnboardingGate,
) : ViewModel() {

    /** Which engine the user is setting up. Offline is the safe default. */
    enum class Mode { OFFLINE, ONLINE }

    private val _mode = MutableStateFlow(Mode.OFFLINE)
    val mode: StateFlow<Mode> = _mode

    fun selectMode(mode: Mode) {
        _mode.value = mode
    }

    private val _cloudKeySaved = MutableStateFlow(false)
    val cloudKeySaved: StateFlow<Boolean> = _cloudKeySaved

    val cloudProvider = com.vocatim.app.data.cloud.CloudTranscribeProvider.DEFAULT

    fun saveCloudKey(key: String) {
        viewModelScope.launch {
            cloudTranscribePrefs.setProvider(cloudProvider)
            cloudTranscribePrefs.setApiKey(key)
            _cloudKeySaved.value = cloudTranscribePrefs.current().isConfigured
        }
    }

    private val isLowRam =
        appContext.getSystemService<ActivityManager>()?.isLowRamDevice == true

    private val _selectedLanguage = MutableStateFlow("id")
    val selectedLanguage: StateFlow<String> = _selectedLanguage

    /**
     * English users can pick Parakeet here instead of digging through
     * Settings. It is both faster and more accurate than whisper on English,
     * but the bundle is ~660 MB against base's 148 MB, so it stays an opt-in
     * choice rather than the recommendation — a forced 660 MB download at
     * first run would cost more users than the accuracy wins back.
     */
    private val _useParakeet = MutableStateFlow(false)
    val useParakeet: StateFlow<Boolean> = _useParakeet

    /** Parakeet is English-only, so the choice is hidden for other languages. */
    val parakeetOffered: StateFlow<Boolean> = _selectedLanguage
        .map { it == "en" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val parakeetBytes: Long = ParakeetModel.totalBytes

    /**
     * Recommendation follows the chosen language: base is strong for
     * English and fast; non-English quality needs small-q5_1 (own testing:
     * base vs q5_1 on Indonesian is a big gap). Low-RAM devices always tiny.
     */
    val recommendedModel: StateFlow<WhisperModel> = _selectedLanguage
        .map { language ->
            when {
                isLowRam -> WhisperModel.TINY
                language == "en" -> WhisperModel.BASE
                else -> WhisperModel.SMALL_Q5
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, WhisperModel.SMALL_Q5)

    val modelState: StateFlow<ModelState> =
        combine(recommendedModel, _useParakeet) { model, parakeet -> model to parakeet }
            .flatMapLatest { (model, parakeet) ->
                if (parakeet) parakeetManager.state else modelManager.state(model)
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ModelState.NotDownloaded)

    fun selectLanguage(code: String) {
        _selectedLanguage.value = code
        // Parakeet cannot transcribe anything but English; leaving it selected
        // after a language switch would silently produce nothing usable.
        if (code != "en") _useParakeet.value = false
        viewModelScope.launch { userPrefs.setLanguage(code) }
    }

    fun setUseParakeet(enabled: Boolean) {
        _useParakeet.value = enabled
    }

    fun downloadRecommended() {
        viewModelScope.launch {
            persistEngine()
            try {
                if (_useParakeet.value) parakeetManager.download()
                else modelManager.download(recommendedModel.value)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // State flow carries the failure; user can retry from Settings.
            }
        }
    }

    fun finish() {
        // Persist the chosen engine even when the download is skipped. Nothing
        // here may throw: this is the only way out of onboarding, so a failure
        // reading the cloud key must not cost the user the whole app.
        viewModelScope.launch {
            val online = _mode.value == Mode.ONLINE &&
                runCatching { cloudTranscribePrefs.current().isConfigured }
                    .getOrDefault(false)
            if (online) {
                userPrefs.setModelId(com.vocatim.app.data.model.CloudEngine.ID)
            } else {
                // No key means online cannot work; fall back rather than
                // leaving a first-run user with an engine that always fails.
                persistEngine()
            }
            // Marker first, flag second: MainActivity re-evaluates the moment
            // the flag flips, and OnboardingGate treats "flag set, no marker"
            // as a restore.
            onboardingGate.markSetUp()
            userPrefs.setOnboardingDone()
        }
    }

    private suspend fun persistEngine() {
        if (_useParakeet.value) userPrefs.setModelId(ParakeetModel.ID)
        else userPrefs.setModel(recommendedModel.value)
    }
}
