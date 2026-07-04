package com.vocatim.app.ui.record

import android.content.Context
import android.os.StatFs
import androidx.lifecycle.ViewModel
import com.vocatim.app.service.RecordingService
import com.vocatim.app.service.RecordingState
import com.vocatim.app.service.RecordingStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class StorageStatus { OK, LOW, FULL }

@HiltViewModel
class RecordViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    stateHolder: RecordingStateHolder,
) : ViewModel() {

    val state: StateFlow<RecordingState> = stateHolder.state
    val finished: SharedFlow<Long> = stateHolder.finished

    private val _storageStatus = MutableStateFlow(checkStorage())
    val storageStatus: StateFlow<StorageStatus> = _storageStatus.asStateFlow()

    /** Returns false (and refreshes the warning) when storage is too full. */
    fun start(): Boolean {
        val status = checkStorage()
        _storageStatus.value = status
        if (status == StorageStatus.FULL) return false
        RecordingService.start(appContext)
        return true
    }

    fun pause() = RecordingService.pause(appContext)
    fun resume() = RecordingService.resume(appContext)
    fun stop() = RecordingService.stop(appContext)

    private fun checkStorage(): StorageStatus {
        val available = try {
            StatFs(appContext.filesDir.absolutePath).availableBytes
        } catch (e: Exception) {
            return StorageStatus.OK
        }
        return when {
            available < FULL_THRESHOLD_BYTES -> StorageStatus.FULL
            available < LOW_THRESHOLD_BYTES -> StorageStatus.LOW
            else -> StorageStatus.OK
        }
    }

    private companion object {
        // 16kHz mono PCM16 ≈ 110 MB/hour. FULL blocks (~25 min headroom),
        // LOW warns (~2 hours headroom).
        const val FULL_THRESHOLD_BYTES = 50L * 1024 * 1024
        const val LOW_THRESHOLD_BYTES = 250L * 1024 * 1024
    }
}
