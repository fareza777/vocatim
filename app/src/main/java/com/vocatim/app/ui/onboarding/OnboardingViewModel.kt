package com.vocatim.app.ui.onboarding

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.getSystemService
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vocatim.app.data.model.ModelManager
import com.vocatim.app.data.model.ModelState
import com.vocatim.app.data.model.WhisperModel
import com.vocatim.app.data.prefs.UserPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
) : ViewModel() {

    private val isLowRam =
        appContext.getSystemService<ActivityManager>()?.isLowRamDevice == true

    private val _selectedLanguage = MutableStateFlow("id")
    val selectedLanguage: StateFlow<String> = _selectedLanguage

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

    val modelState: StateFlow<ModelState> = recommendedModel
        .flatMapLatest { modelManager.state(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ModelState.NotDownloaded)

    fun selectLanguage(code: String) {
        _selectedLanguage.value = code
        viewModelScope.launch { userPrefs.setLanguage(code) }
    }

    fun downloadRecommended() {
        val model = recommendedModel.value
        viewModelScope.launch {
            userPrefs.setModel(model)
            try {
                modelManager.download(model)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // State flow carries the failure; user can retry from Settings.
            }
        }
    }

    fun finish() {
        // Persist the default model even when the download is skipped.
        viewModelScope.launch {
            userPrefs.setModel(recommendedModel.value)
            userPrefs.setOnboardingDone()
        }
    }
}
