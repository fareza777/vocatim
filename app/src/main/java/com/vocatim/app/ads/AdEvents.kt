package com.vocatim.app.ads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridges "a transcription just finished" from the background service to the
 * Activity, which is the only thing that can show a full-screen ad.
 *
 * A flag rather than an event stream: if three jobs finish while the app is in
 * the background, the user should meet one ad when they come back, not three.
 */
class AdEvents {

    private val _interstitialPending = MutableStateFlow(false)
    val interstitialPending: StateFlow<Boolean> = _interstitialPending.asStateFlow()

    fun markTranscriptionFinished() {
        _interstitialPending.value = true
    }

    fun consume() {
        _interstitialPending.value = false
    }
}
