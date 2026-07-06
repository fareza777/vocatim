package com.vocatim.app.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vocatim.app.data.audio.WaveformHelper
import com.vocatim.app.data.db.TranscriptEntity
import com.vocatim.app.data.db.TranscriptStatus
import com.vocatim.app.data.repository.ImportCoordinator
import com.vocatim.app.data.repository.TranscriptRepository
import com.vocatim.app.data.transcribe.TranscriptionProgress
import com.vocatim.app.data.transcribe.TranscriptionProgressHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class HomeItem(
    val transcript: TranscriptEntity,
    val progress: TranscriptionProgress?,
    val waveform: FloatArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HomeItem) return false
        return transcript == other.transcript &&
            progress == other.progress &&
            waveform.contentEquals(other.waveform)
    }

    override fun hashCode(): Int {
        var result = transcript.hashCode()
        result = 31 * result + (progress?.hashCode() ?: 0)
        result = 31 * result + (waveform?.contentHashCode() ?: 0)
        return result
    }
}

data class QuotaBanner(val remainingMinutes: Long, val exhausted: Boolean)

data class HomeStats(val transcriptCount: Int, val totalDurationMs: Long)


enum class HomeSort { NEWEST, OLDEST, LONGEST, TITLE }

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: TranscriptRepository,
    progressHolder: TranscriptionProgressHolder,
    quotaStore: com.vocatim.app.data.billing.QuotaStore,
    private val importCoordinator: ImportCoordinator,
    private val transcriptRepository: TranscriptRepository,
) : ViewModel() {

    val quotaBanner: StateFlow<QuotaBanner?> =
        combine(quotaStore.isProCached, quotaStore.usedMs) { pro, used ->
            if (pro) null
            else {
                val remaining =
                    (com.vocatim.app.data.billing.QuotaStore.FREE_LIMIT_MS - used)
                        .coerceAtLeast(0)
                QuotaBanner(
                    remainingMinutes = remaining / 60_000,
                    exhausted = remaining <= 0,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val stats: StateFlow<HomeStats> =
        combine(repository.observeCount(), repository.observeTotalDurationMs()) { count, duration ->
            HomeStats(count, duration)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeStats(0, 0))

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _sort = MutableStateFlow(HomeSort.NEWEST)
    val sort: StateFlow<HomeSort> = _sort

    /** null = show all folders. */
    private val _selectedFolder = MutableStateFlow<String?>(null)
    val selectedFolder: StateFlow<String?> = _selectedFolder

    val folders: StateFlow<List<String>> =
        repository.observeFolders()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _waveforms = MutableStateFlow<Map<Long, FloatArray>>(emptyMap())
    val waveforms: StateFlow<Map<Long, FloatArray>> = _waveforms

    private val filters = combine(_query, _sort, _selectedFolder) { query, sort, folder ->
        Triple(query, sort, folder)
    }

    val items: StateFlow<List<HomeItem>> =
        combine(
            repository.observeAll(),
            progressHolder.progress,
            filters,
            _waveforms,
        ) { transcripts, progress, (query, sort, folder), waveforms ->
            transcripts
                .filter { t -> folder == null || t.tag == folder }
                .filter { t ->
                    query.isBlank() ||
                        t.title.contains(query, ignoreCase = true) ||
                        t.text.contains(query, ignoreCase = true) ||
                        t.tag?.contains(query, ignoreCase = true) == true
                }
                .let { sortList(it, sort) }
                .map { HomeItem(it, progress[it.id], waveforms[it.id]) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectFolder(folder: String?) {
        _selectedFolder.value = folder
    }

    init {
        viewModelScope.launch {
            repository.observeAll().collect { list ->
                prefetchWaveforms(list)
            }
        }
    }

    fun onQueryChanged(query: String) {
        _query.value = query
    }

    fun setSort(sort: HomeSort) {
        _sort.value = sort
    }

    fun togglePin(id: Long) {
        viewModelScope.launch {
            val entity = transcriptRepository.getById(id) ?: return@launch
            transcriptRepository.setPinned(id, !entity.pinned)
        }
    }

    fun setTag(id: Long, tag: String?) {
        viewModelScope.launch {
            transcriptRepository.setTag(id, tag)
        }
    }

    private var lastDeleted: com.vocatim.app.data.repository.DeletedTranscript? = null

    /** Emits the deleted transcript's title so the UI can offer an undo. */
    private val _deletedEvent = MutableStateFlow<String?>(null)
    val deletedEvent: StateFlow<String?> = _deletedEvent

    fun delete(id: Long) {
        viewModelScope.launch {
            // Finalize any prior pending delete before starting a new one.
            lastDeleted?.let { transcriptRepository.purgeDeletedFiles(it) }
            val deleted = transcriptRepository.deleteForUndo(id)
            _waveforms.value = _waveforms.value - id
            if (deleted != null) {
                lastDeleted = deleted
                _deletedEvent.value = deleted.transcript.title
            }
        }
    }

    fun undoDelete() {
        val deleted = lastDeleted ?: return
        lastDeleted = null
        _deletedEvent.value = null
        viewModelScope.launch { transcriptRepository.restore(deleted) }
    }

    /** Undo window closed: really remove the files. */
    fun finalizeDelete() {
        val deleted = lastDeleted ?: return
        lastDeleted = null
        _deletedEvent.value = null
        viewModelScope.launch { transcriptRepository.purgeDeletedFiles(deleted) }
    }

    override fun onCleared() {
        super.onCleared()
        lastDeleted?.let { d ->
            // App scope: files must still be purged even after the VM dies.
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                transcriptRepository.purgeDeletedFiles(d)
            }
        }
    }

    fun importTextFile(uri: Uri) {
        viewModelScope.launch {
            runCatching { importCoordinator.importTextFile(uri) }
        }
    }

    fun importAudio(uri: Uri) {
        viewModelScope.launch {
            runCatching { importCoordinator.startImport(uri) }
        }
    }

    private fun sortList(list: List<TranscriptEntity>, sort: HomeSort): List<TranscriptEntity> =
        when (sort) {
            HomeSort.NEWEST -> list.sortedWith(
                compareByDescending<TranscriptEntity> { it.pinned }.thenByDescending { it.createdAt }
            )
            HomeSort.OLDEST -> list.sortedWith(
                compareByDescending<TranscriptEntity> { it.pinned }.thenBy { it.createdAt }
            )
            HomeSort.LONGEST -> list.sortedWith(
                compareByDescending<TranscriptEntity> { it.pinned }
                    .thenByDescending { it.audioDurationMs }
            )
            HomeSort.TITLE -> list.sortedWith(
                compareByDescending<TranscriptEntity> { it.pinned }
                    .thenBy { it.title.lowercase() }
            )
        }

    private suspend fun prefetchWaveforms(transcripts: List<TranscriptEntity>) {
        val missing = transcripts.filter {
            it.audioPath != null && !_waveforms.value.containsKey(it.id)
        }
        if (missing.isEmpty()) return
        val loaded = withContext(Dispatchers.IO) {
            missing.mapNotNull { t ->
                val path = t.audioPath ?: return@mapNotNull null
                val peaks = WaveformHelper.peaks(File(path)) ?: return@mapNotNull null
                t.id to peaks
            }.toMap()
        }
        if (loaded.isNotEmpty()) {
            _waveforms.value = _waveforms.value + loaded
        }
    }
}
