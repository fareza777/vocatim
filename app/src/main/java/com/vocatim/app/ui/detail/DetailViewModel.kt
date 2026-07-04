package com.vocatim.app.ui.detail

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vocatim.app.data.db.TranscriptEntity
import com.vocatim.app.data.db.TranscriptStatus
import com.vocatim.app.data.export.SrtFormatter
import com.vocatim.app.data.repository.TranscriptRepository
import com.vocatim.app.data.transcribe.TranscriptionProgress
import com.vocatim.app.data.transcribe.TranscriptionProgressHolder
import com.vocatim.app.service.TranscriptionService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

sealed interface ExportEvent {
    data object Success : ExportEvent
    data class Failure(val message: String) : ExportEvent
}

data class PlayerState(
    val playing: Boolean,
    val positionMs: Int,
    val durationMs: Int,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val repository: TranscriptRepository,
    progressHolder: TranscriptionProgressHolder,
    userPrefs: com.vocatim.app.data.prefs.UserPrefs,
) : ViewModel() {

    private val transcriptId: Long = checkNotNull(savedStateHandle["transcriptId"])

    /** Reading-comfort multiplier for the transcript text. */
    val textScale: StateFlow<Float> = userPrefs.settings
        .map { it.textScale }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1.0f)

    val transcript: StateFlow<TranscriptEntity?> =
        repository.observeById(transcriptId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val progress: StateFlow<TranscriptionProgress?> =
        progressHolder.progress.map { it[transcriptId] }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Non-null while the user has unsaved edits. */
    private val _editedText = MutableStateFlow<String?>(null)
    val editedText: StateFlow<String?> = _editedText.asStateFlow()

    private val _exportEvent = MutableStateFlow<ExportEvent?>(null)
    val exportEvent: StateFlow<ExportEvent?> = _exportEvent.asStateFlow()

    private var player: android.media.MediaPlayer? = null
    private var positionJob: kotlinx.coroutines.Job? = null
    private val _playerState = MutableStateFlow<PlayerState?>(null)
    val playerState: StateFlow<PlayerState?> = _playerState.asStateFlow()

    /** Downsampled peaks of the audio file, for the playback waveform. */
    private val _waveform = MutableStateFlow<FloatArray?>(null)
    val waveform: StateFlow<FloatArray?> = _waveform.asStateFlow()

    /** Segments for the timestamped view; loaded once the transcript is done. */
    private val _segments = MutableStateFlow<List<com.vocatim.app.data.db.SegmentEntity>>(emptyList())
    val segments: StateFlow<List<com.vocatim.app.data.db.SegmentEntity>> = _segments.asStateFlow()

    fun loadSegments() {
        viewModelScope.launch {
            _segments.value = repository.getSegments(transcriptId)
        }
    }

    fun loadWaveform() {
        if (_waveform.value != null) return
        val path = transcript.value?.audioPath ?: return
        viewModelScope.launch {
            _waveform.value = withContext(Dispatchers.IO) {
                runCatching { computeWaveform(java.io.File(path)) }.getOrNull()
            }
        }
    }

    private fun computeWaveform(file: java.io.File, buckets: Int = 64): FloatArray {
        com.vocatim.app.data.audio.WavStreamReader(file).use { reader ->
            val total = reader.totalSamples
            if (total <= 0) return FloatArray(buckets)
            val bucketSize = total / buckets
            // Sample a slice per bucket instead of scanning multi-hour files.
            val probe = minOf(bucketSize, 4_096L).toInt().coerceAtLeast(1)
            return FloatArray(buckets) { i ->
                val samples = reader.read(i * bucketSize, probe)
                var peak = 0f
                for (s in samples) {
                    val a = if (s < 0) -s else s
                    if (a > peak) peak = a
                }
                peak
            }
        }
    }

    fun togglePlayback() {
        val existing = player
        if (existing == null) {
            startPlayback()
        } else if (existing.isPlaying) {
            existing.pause()
            positionJob?.cancel()
            _playerState.value = _playerState.value?.copy(playing = false)
        } else {
            existing.start()
            startPositionUpdates()
        }
    }

    /** Jump to an absolute position, starting playback if needed. */
    fun playFromMs(ms: Long) {
        val existing = player
        if (existing == null) {
            startPlayback(seekToMs = ms.toInt())
        } else {
            existing.seekTo(ms.toInt())
            if (!existing.isPlaying) existing.start()
            startPositionUpdates()
        }
    }

    fun seekToFraction(fraction: Float) {
        val duration = transcript.value?.audioDurationMs ?: return
        playFromMs((duration * fraction.coerceIn(0f, 1f)).toLong())
    }

    private fun startPlayback(seekToMs: Int = 0) {
        val path = transcript.value?.audioPath ?: return
        viewModelScope.launch {
            try {
                val created = withContext(Dispatchers.IO) {
                    android.media.MediaPlayer().apply {
                        setDataSource(path)
                        prepare()
                    }
                }
                created.setOnCompletionListener {
                    positionJob?.cancel()
                    _playerState.value =
                        PlayerState(playing = false, positionMs = 0, durationMs = created.duration)
                }
                player = created
                if (seekToMs > 0) created.seekTo(seekToMs)
                created.start()
                startPositionUpdates()
            } catch (e: Exception) {
                _playerState.value = null
            }
        }
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (true) {
                val p = player ?: break
                _playerState.value = PlayerState(
                    playing = p.isPlaying,
                    positionMs = p.currentPosition,
                    durationMs = p.duration,
                )
                kotlinx.coroutines.delay(200)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        positionJob?.cancel()
        player?.release()
        player = null
    }

    fun onTextChanged(text: String) {
        _editedText.value = text
    }

    fun saveEdits() {
        val text = _editedText.value ?: return
        viewModelScope.launch {
            repository.updateText(transcriptId, text)
            _editedText.value = null
        }
    }

    fun discardEdits() {
        _editedText.value = null
    }

    /** Current text: unsaved edits win over the stored value. */
    fun currentText(): String = _editedText.value ?: transcript.value?.text.orEmpty()

    fun rename(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.updateTitle(transcriptId, trimmed)
        }
    }

    fun retry() {
        viewModelScope.launch {
            repository.updateStatus(transcriptId, TranscriptStatus.PENDING)
            TranscriptionService.enqueue(appContext, transcriptId, sourceUri = null)
        }
    }

    fun cancel() {
        TranscriptionService.cancel(appContext, transcriptId)
    }

    /** Frees disk space but keeps the transcript text and segments. */
    fun deleteAudioOnly() {
        viewModelScope.launch {
            repository.deleteAudioFile(transcriptId)
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.delete(transcriptId)
            onDeleted()
        }
    }

    fun exportTxt(uri: Uri) = export(uri) { currentText() }

    fun exportSrt(uri: Uri) = export(uri) {
        SrtFormatter.format(repository.getSegments(transcriptId))
    }

    fun consumeExportEvent() {
        _exportEvent.value = null
    }

    private fun export(uri: Uri, content: suspend () -> String) {
        viewModelScope.launch {
            try {
                val text = content()
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri, "wt")
                        ?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: throw IOException("Couldn't open destination")
                }
                _exportEvent.value = ExportEvent.Success
            } catch (e: Exception) {
                _exportEvent.value = ExportEvent.Failure(e.message ?: "export failed")
            }
        }
    }
}
