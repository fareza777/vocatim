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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext appContext: Context,
    private val userPrefs: UserPrefs,
    private val modelManager: ModelManager,
) : ViewModel() {

    /** Low-RAM devices start with tiny; everyone else with base. */
    val recommendedModel: WhisperModel =
        if (appContext.getSystemService<ActivityManager>()?.isLowRamDevice == true) {
            WhisperModel.TINY
        } else {
            WhisperModel.BASE
        }

    val modelState: StateFlow<ModelState> = modelManager.state(recommendedModel)

    fun selectLanguage(code: String) {
        viewModelScope.launch { userPrefs.setLanguage(code) }
    }

    fun downloadRecommended() {
        viewModelScope.launch {
            userPrefs.setModel(recommendedModel)
            try {
                modelManager.download(recommendedModel)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // State flow carries the failure; user can retry from Settings.
            }
        }
    }

    fun finish() {
        viewModelScope.launch { userPrefs.setOnboardingDone() }
    }
}
