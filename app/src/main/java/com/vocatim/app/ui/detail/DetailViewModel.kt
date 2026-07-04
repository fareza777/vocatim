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

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val appContext: Context,
    private val repository: TranscriptRepository,
    progressHolder: TranscriptionProgressHolder,
) : ViewModel() {

    private val transcriptId: Long = checkNotNull(savedStateHandle["transcriptId"])

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

    fun retry() {
        viewModelScope.launch {
            repository.updateStatus(transcriptId, TranscriptStatus.PENDING)
            TranscriptionService.enqueue(appContext, transcriptId, sourceUri = null)
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
