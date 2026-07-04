package com.vocatim.app.ui.record

import android.content.Context
import androidx.lifecycle.ViewModel
import com.vocatim.app.service.RecordingService
import com.vocatim.app.service.RecordingState
import com.vocatim.app.service.RecordingStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class RecordViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    stateHolder: RecordingStateHolder,
) : ViewModel() {

    val state: StateFlow<RecordingState> = stateHolder.state
    val finished: SharedFlow<Long> = stateHolder.finished

    fun start() = RecordingService.start(appContext)
    fun pause() = RecordingService.pause(appContext)
    fun resume() = RecordingService.resume(appContext)
    fun stop() = RecordingService.stop(appContext)
}
