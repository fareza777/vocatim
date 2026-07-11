package com.vocatim.app.ui.record

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vocatim.app.data.model.ModelManager
import com.vocatim.app.data.model.WhisperModel
import com.vocatim.app.data.prefs.UserPrefs
import com.vocatim.app.data.repository.TranscriptRepository
import com.vocatim.app.data.transcribe.TranscriptionProgressHolder
import com.vocatim.app.data.transcribe.WhisperTranscriber
import com.vocatim.app.service.RecordingService
import com.vocatim.app.service.RecordingState
import com.vocatim.app.service.RecordingStateHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class StorageStatus { OK, LOW, FULL }

@HiltViewModel
class RecordViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: TranscriptRepository,
    private val stateHolder: RecordingStateHolder,
    private val transcriber: WhisperTranscriber,
    private val modelManager: ModelManager,
    private val progressHolder: TranscriptionProgressHolder,
    private val userPrefs: UserPrefs,
    private val liveCaptionManager: com.vocatim.app.data.model.LiveCaptionModelManager,
    private val liveCaptionEngine: com.vocatim.app.data.transcribe.LiveCaptionEngine,
) : ViewModel() {

    val state: StateFlow<RecordingState> = stateHolder.state
    val finished: SharedFlow<Long> = stateHolder.finished

    // Bookmarks captured during this recording (elapsed ms), saved on finish.
    private val markerTimes = mutableListOf<Long>()
    private val _markers = MutableStateFlow<List<Long>>(emptyList())
    val markers: StateFlow<List<Long>> = _markers.asStateFlow()

    /** Records a bookmark at the current elapsed time. */
    fun addMarker() {
        val s = state.value
        if (s is RecordingState.Active) {
            markerTimes.add(s.elapsedMs)
            _markers.value = markerTimes.toList()
        }
    }

    /** Persists captured bookmarks onto the finished transcript. */
    fun saveMarkers(transcriptId: Long) {
        if (markerTimes.isEmpty()) return
        val csv = markerTimes.sorted().joinToString(",")
        viewModelScope.launch { repository.setMarkers(transcriptId, csv) }
        markerTimes.clear()
        _markers.value = emptyList()
    }

    // --- Live text preview while recording (battery-guarded) ---

    /** Rough transcript of the last ~15s; null while unavailable. */
    private val _livePreview = MutableStateFlow<String?>(null)
    val livePreview: StateFlow<String?> = _livePreview.asStateFlow()

    private var previewJob: Job? = null

    /**
     * Periodically transcribes the tail of the live recording with the tiny
     * model. Deliberately conservative: runs only while the Record screen is
     * on and interactive, never in power-save or when the device is warm,
     * and never competes with a real transcription job.
     */
    fun startLivePreview() {
        if (previewJob?.isActive == true) return
        previewJob = viewModelScope.launch {
            while (true) {
                delay(PREVIEW_INTERVAL_MS)
                val s = state.value
                if (s !is RecordingState.Active || s.paused) {
                    if (s is RecordingState.Idle) _livePreview.value = null
                    continue
                }
                if (!previewAllowed()) continue
                val samples = stateHolder.previewBuffer.snapshot()
                if (samples.size < MIN_PREVIEW_SAMPLES) continue
                val text = runCatching {
                    transcriber.transcribe(
                        model = WhisperModel.TINY,
                        samples = samples,
                        language = userPrefs.language(),
                        numThreads = PREVIEW_THREADS,
                    ).text
                }.getOrNull()
                if (!text.isNullOrBlank()) _livePreview.value = text
            }
        }
    }

    fun stopLivePreview() {
        previewJob?.cancel()
        previewJob = null
        _livePreview.value = null
    }

    private fun previewAllowed(): Boolean {
        // Live mode has its own streaming captions; the periodic whisper
        // preview would be duplicate inference on top of them.
        if (_liveMode.value) return false
        val pm = appContext.getSystemService(PowerManager::class.java) ?: return false
        if (!pm.isInteractive || pm.isPowerSaveMode) return false
        if (Build.VERSION.SDK_INT >= 29 &&
            pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
        ) return false
        // A real transcription job owns the whisper context; don't cause
        // model-swap thrash for a preview.
        if (progressHolder.progress.value.isNotEmpty()) return false
        return modelManager.isDownloaded(WhisperModel.TINY)
    }

    // --- Live recording mode: true streaming captions (English) ---

    /** True while this recording session runs in Live mode. */
    private val _liveMode = MutableStateFlow(false)
    val liveMode: StateFlow<Boolean> = _liveMode.asStateFlow()

    private val _captions = MutableStateFlow(com.vocatim.app.data.transcribe.CaptionState())
    val captions: StateFlow<com.vocatim.app.data.transcribe.CaptionState> = _captions.asStateFlow()

    /** Captions suspended because the device is warm or in power-save. */
    private val _liveThrottled = MutableStateFlow(false)
    val liveThrottled: StateFlow<Boolean> = _liveThrottled.asStateFlow()

    /** Kept for the caption panel API; the restored engine path never sets
     *  it, matching the device-verified build. */
    private val _liveError = MutableStateFlow<String?>(null)
    val liveError: StateFlow<String?> = _liveError.asStateFlow()

    val liveModelReady: Boolean
        get() = liveCaptionManager.isDownloaded()

    private var liveJob: Job? = null

    // NOTE: this live path is a 1:1 restore of the device-verified build.
    // Resist "hardening" it without on-device verification — the last two
    // attempts (joined stops, session tokens, fresh-start) broke it.

    /** Starts a Live-mode recording; false when blocked (storage/model). */
    fun startLive(): Boolean {
        if (!liveModelReady) return false
        val status = checkStorage()
        _storageStatus.value = status
        if (status == StorageStatus.FULL) return false
        _liveMode.value = true
        _captions.value = com.vocatim.app.data.transcribe.CaptionState()
        stateHolder.liveTapEnabled = true
        liveJob?.cancel()
        liveJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            runCatching { liveCaptionEngine.start(numThreads = 2) }
                .onFailure { return@launch }
            var guardTick = 0
            stateHolder.liveAudio.collect { samples ->
                val s = state.value
                if (s !is RecordingState.Active || s.paused) return@collect
                // Re-check the battery/thermal guard every ~10s of audio.
                if (guardTick++ % 50 == 0) {
                    _liveThrottled.value = !captionsAllowed()
                }
                if (!_liveThrottled.value) {
                    _captions.value = liveCaptionEngine.feed(samples)
                }
            }
        }
        // The recorder itself is byte-for-byte the normal path.
        RecordingService.start(appContext)
        return true
    }

    private fun captionsAllowed(): Boolean {
        val pm = appContext.getSystemService(PowerManager::class.java) ?: return false
        if (pm.isPowerSaveMode) return false
        if (Build.VERSION.SDK_INT >= 29 &&
            pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
        ) return false
        return true
    }

    private fun stopLiveCaptions() {
        if (!_liveMode.value) return
        _liveMode.value = false
        stateHolder.liveTapEnabled = false
        liveJob?.cancel()
        liveJob = null
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            runCatching { liveCaptionEngine.stop() }
        }
    }

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

    // Placed LAST on purpose: viewModelScope.launch on Main.immediate runs
    // the collector synchronously during construction, so every property it
    // touches (the live-mode state above) must already be initialized.
    init {
        // Live captions end with the recording session, however it stops.
        viewModelScope.launch {
            state.collect { s ->
                if (s is RecordingState.Idle || s is RecordingState.Error) {
                    stopLiveCaptions()
                }
            }
        }
    }

    private companion object {
        // 16kHz mono PCM16 ≈ 110 MB/hour. FULL blocks (~25 min headroom),
        // LOW warns (~2 hours headroom).
        const val FULL_THRESHOLD_BYTES = 50L * 1024 * 1024
        const val LOW_THRESHOLD_BYTES = 250L * 1024 * 1024

        const val PREVIEW_INTERVAL_MS = 20_000L
        const val PREVIEW_THREADS = 2
        /** Don't bother below ~4s of audio. */
        const val MIN_PREVIEW_SAMPLES = 4 * 16_000
    }
}
