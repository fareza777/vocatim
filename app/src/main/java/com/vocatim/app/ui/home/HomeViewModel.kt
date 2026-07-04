package com.vocatim.app.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vocatim.app.data.db.TranscriptEntity
import com.vocatim.app.data.repository.ImportCoordinator
import com.vocatim.app.data.repository.TranscriptRepository
import com.vocatim.app.data.transcribe.TranscriptionProgress
import com.vocatim.app.data.transcribe.TranscriptionProgressHolder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeItem(
    val transcript: TranscriptEntity,
    val progress: TranscriptionProgress?,
)

data class QuotaBanner(val remainingMinutes: Long, val exhausted: Boolean)

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: TranscriptRepository,
    progressHolder: TranscriptionProgressHolder,
    quotaStore: com.vocatim.app.data.billing.QuotaStore,
    private val importCoordinator: ImportCoordinator,
) : ViewModel() {

    /** null = Pro (no banner). */
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

    private val _query = kotlinx.coroutines.flow.MutableStateFlow("")
    val query: StateFlow<String> = _query

    val items: StateFlow<List<HomeItem>> =
        combine(
            repository.observeAll(),
            progressHolder.progress,
            _query,
        ) { transcripts, progress, query ->
            transcripts
                .filter { t ->
                    query.isBlank() ||
                        t.title.contains(query, ignoreCase = true) ||
                        t.text.contains(query, ignoreCase = true)
                }
                .map { HomeItem(it, progress[it.id]) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChanged(query: String) {
        _query.value = query
    }

    fun importAudio(uri: Uri) {
        viewModelScope.launch {
            runCatching { importCoordinator.startImport(uri) }
        }
    }
}
