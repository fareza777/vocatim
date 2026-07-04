package com.vocatim.app.ui.debug

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vocatim.app.data.audio.WavDecoder
import com.vocatim.app.data.db.TranscriptDao
import com.vocatim.app.data.db.TranscriptEntity
import com.vocatim.app.data.model.ModelManager
import com.vocatim.app.data.model.ModelState
import com.vocatim.app.data.model.WhisperModel
import com.vocatim.app.data.transcribe.WhisperTranscriber
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

sealed interface TranscriptionUiState {
    data object Idle : TranscriptionUiState
    data object Preparing : TranscriptionUiState
    data class Transcribing(val audioDurationMs: Long) : TranscriptionUiState
    data class Success(
        val text: String,
        val audioDurationMs: Long,
        val processingTimeMs: Long,
    ) : TranscriptionUiState {
        /** Realtime factor: processing time / audio duration. Lower is faster. */
        val realtimeFactor: Float
            get() = if (audioDurationMs > 0) processingTimeMs.toFloat() / audioDurationMs else 0f
    }
    data class Error(val message: String) : TranscriptionUiState
}

@HiltViewModel
class DebugViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val modelManager: ModelManager,
    private val transcriber: WhisperTranscriber,
    private val transcriptDao: TranscriptDao,
) : ViewModel() {

    val modelStates: StateFlow<Map<WhisperModel, ModelState>> =
        combine(WhisperModel.entries.map { modelManager.state(it) }) { states ->
            WhisperModel.entries.zip(states.toList()).toMap()
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            WhisperModel.entries.associateWith { modelManager.state(it).value },
        )

    private val _selectedModel = MutableStateFlow(WhisperModel.TINY)
    val selectedModel: StateFlow<WhisperModel> = _selectedModel.asStateFlow()

    private val _language = MutableStateFlow("auto")
    val language: StateFlow<String> = _language.asStateFlow()

    private val _transcription = MutableStateFlow<TranscriptionUiState>(TranscriptionUiState.Idle)
    val transcription: StateFlow<TranscriptionUiState> = _transcription.asStateFlow()

    /** Native runtime diagnostics (SIMD features, thread count) for the debug screen. */
    val systemInfo: StateFlow<String> = kotlinx.coroutines.flow.flow {
        emit("threads=${com.vocatim.whisper.WhisperCpuConfig.preferredThreadCount} | " +
            com.vocatim.whisper.WhisperContext.getSystemInfo())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Lazily, "")

    private var downloadJob: Job? = null
    private var transcribeJob: Job? = null

    fun selectModel(model: WhisperModel) {
        _selectedModel.value = model
    }

    fun selectLanguage(code: String) {
        _language.value = code
    }

    fun downloadSelectedModel() {
        val model = _selectedModel.value
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            try {
                modelManager.download(model)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // State flow already carries ModelState.Failed with the message.
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    fun deleteModel(model: WhisperModel) {
        modelManager.delete(model)
    }

    fun transcribeWav(uri: Uri) {
        if (transcribeJob?.isActive == true) return
        val model = _selectedModel.value
        if (!modelManager.isDownloaded(model)) {
            _transcription.value = TranscriptionUiState.Error("MODEL_NOT_DOWNLOADED")
            return
        }
        transcribeJob = viewModelScope.launch {
            _transcription.value = TranscriptionUiState.Preparing
            try {
                val decoded = withContext(Dispatchers.IO) {
                    val bytes = appContext.contentResolver.openInputStream(uri)
                        ?.use { it.readBytes() }
                        ?: throw IOException("Couldn't open the selected file")
                    WavDecoder.decode(bytes)
                }
                _transcription.value = TranscriptionUiState.Transcribing(decoded.durationMs)

                val result = transcriber.transcribe(
                    model = model,
                    samples = decoded.samples,
                    language = _language.value,
                )

                transcriptDao.insert(
                    TranscriptEntity(
                        title = "Debug " + TITLE_FORMAT.format(Date()),
                        text = result.text,
                        language = _language.value,
                        modelId = model.id,
                        audioDurationMs = decoded.durationMs,
                        processingTimeMs = result.processingTimeMs,
                        audioPath = null,
                    )
                )

                _transcription.value = TranscriptionUiState.Success(
                    text = result.text,
                    audioDurationMs = decoded.durationMs,
                    processingTimeMs = result.processingTimeMs,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _transcription.value =
                    TranscriptionUiState.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // ViewModel scope is gone; release native memory on a detached scope.
        kotlinx.coroutines.CoroutineScope(Dispatchers.Default).launch {
            transcriber.release()
        }
    }

    private companion object {
        val TITLE_FORMAT = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
    }
}
