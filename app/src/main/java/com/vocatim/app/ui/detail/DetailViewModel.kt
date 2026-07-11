package com.vocatim.app.ui.detail

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vocatim.app.data.db.TranscriptEntity
import com.vocatim.app.data.db.TranscriptStatus
import com.vocatim.app.data.export.MarkdownFormatter
import com.vocatim.app.data.export.PdfExporter
import com.vocatim.app.data.export.SrtFormatter
import com.vocatim.app.data.export.VttFormatter
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import com.vocatim.app.ui.common.formatClock
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
    private val summaryModelManager: com.vocatim.app.data.summary.SummaryModelManager,
    summaryProgressHolder: com.vocatim.app.data.summary.SummaryProgressHolder,
    private val diarizationModelManager: com.vocatim.app.data.model.DiarizationModelManager,
    diarizationProgressHolder: com.vocatim.app.data.transcribe.DiarizationProgressHolder,
    quotaStore: com.vocatim.app.data.billing.QuotaStore,
    private val userPrefs: com.vocatim.app.data.prefs.UserPrefs,
    private val cloudAiPrefs: com.vocatim.app.data.cloud.CloudAiPrefs,
    private val cloudClient: com.vocatim.app.data.cloud.CloudAiClient,
) : ViewModel() {

    /** True when the user has set up a BYOK cloud provider in Settings. */
    val cloudConfigured: StateFlow<Boolean> = cloudAiPrefs.config
        .map { it.isConfigured }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val transcriptId: Long = savedStateHandle.get<Long>("transcriptId")
        ?: savedStateHandle.get<String>("transcriptId")?.toLongOrNull()
        ?: savedStateHandle.get<Int>("transcriptId")?.toLong()
        ?: error("Detail screen opened without transcriptId")

    val isPro: StateFlow<Boolean> = quotaStore.isProCached
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The user-selected on-device model, from Settings. */
    private val selectedSummaryModel:
        StateFlow<com.vocatim.app.data.summary.SummaryModel> =
        userPrefs.settings
            .map { com.vocatim.app.data.summary.SummaryModel.fromId(it.summaryModel) }
            .stateIn(
                viewModelScope, SharingStarted.WhileSubscribed(5_000),
                com.vocatim.app.data.summary.SummaryModel.DEFAULT,
            )

    /** Approximate download size of the selected model, for UI copy. */
    val summaryModelSizeBytes: StateFlow<Long> =
        selectedSummaryModel.map { it.approxSizeBytes }
            .stateIn(
                viewModelScope, SharingStarted.WhileSubscribed(5_000),
                com.vocatim.app.data.summary.SummaryModel.DEFAULT.approxSizeBytes,
            )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val summaryModelState: StateFlow<com.vocatim.app.data.model.ModelState> =
        selectedSummaryModel
            .flatMapLatest { summaryModelManager.state(it) }
            .stateIn(
                viewModelScope, SharingStarted.WhileSubscribed(5_000),
                com.vocatim.app.data.model.ModelState.NotDownloaded,
            )

    /** null when idle; 0f..1f while a summary job for this transcript runs. */
    val summaryProgress: StateFlow<Float?> =
        summaryProgressHolder.progress.map { it[transcriptId] }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Live partial text of the summary being generated, streamed token by
     *  token from the local model. */
    val summaryPartial: StateFlow<String?> =
        summaryProgressHolder.partialText.map { it[transcriptId] }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** null when idle; 0f..1f while speaker detection runs for this note. */
    val diarizationProgress: StateFlow<Float?> =
        diarizationProgressHolder.progress.map { it[transcriptId] }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val diarizeModelReady: Boolean
        get() = diarizationModelManager.isDownloaded()

    fun startDiarization() {
        com.vocatim.app.service.DiarizationService.start(appContext, transcriptId)
        // Refresh the segment list once the job finishes so the new speaker
        // labels appear without leaving the screen.
        viewModelScope.launch {
            var running = false
            diarizationProgress.collect { p ->
                if (p != null) {
                    running = true
                } else if (running) {
                    loadSegments()
                    return@collect
                }
            }
        }
    }

    private var summaryDownloadJob: kotlinx.coroutines.Job? = null

    fun downloadSummaryModel() {
        if (summaryDownloadJob?.isActive == true) return
        summaryDownloadJob = viewModelScope.launch {
            runCatching { summaryModelManager.download(selectedSummaryModel.value) }
        }
    }

    fun startSummary() {
        com.vocatim.app.service.SummaryService.start(appContext, transcriptId)
    }

    fun startCloudSummary() {
        com.vocatim.app.service.SummaryService.start(
            appContext, transcriptId, com.vocatim.app.service.SummaryService.MODE_CLOUD
        )
    }

    fun createMinutes() {
        com.vocatim.app.service.SummaryService.start(
            appContext, transcriptId, com.vocatim.app.service.SummaryService.MODE_MINUTES
        )
    }

    fun cancelSummary() {
        com.vocatim.app.service.SummaryService.cancel(appContext)
    }

    // --- Ask AI: grounded Q&A over this transcript (BYOK) ---
    data class QaEntry(val question: String, val answer: String)

    private val _qaHistory = MutableStateFlow<List<QaEntry>>(emptyList())
    val qaHistory: StateFlow<List<QaEntry>> = _qaHistory.asStateFlow()

    private val _qaBusy = MutableStateFlow(false)
    val qaBusy: StateFlow<Boolean> = _qaBusy.asStateFlow()

    fun askAi(question: String) {
        val text = currentText()
        if (question.isBlank() || text.isBlank() || _qaBusy.value) return
        viewModelScope.launch {
            _qaBusy.value = true
            val language = transcript.value?.let {
                if (it.modelId == com.vocatim.app.data.model.ParakeetModel.ID) "en"
                else it.language.takeIf { l -> l != "auto" } ?: it.detectedLanguage
            } ?: "en"
            val answer = runCatching {
                if (cloudAiPrefs.current().isConfigured) {
                    cloudClient.chat(
                        config = cloudAiPrefs.current(),
                        system = com.vocatim.app.data.cloud.CloudPrompts.qaSystem(language),
                        user = "Transcript:\n" +
                            text.take(com.vocatim.app.data.cloud.CloudPrompts.MAX_INPUT_CHARS) +
                            "\n\nQuestion: " + question.trim(),
                        maxTokens = 2048,
                    )
                } else {
                    // No BYOK key: answer fully on-device with the local model.
                    localAnswer(text, question.trim(), language)
                }
            }.getOrElse { it.message ?: "Request failed" }
            _qaHistory.value = _qaHistory.value + QaEntry(question.trim(), answer)
            _qaBusy.value = false
        }
    }

    private suspend fun localAnswer(
        transcript: String,
        question: String,
        language: String,
    ): String = withContext(Dispatchers.Default) {
        val settings = userPrefs.current()
        com.vocatim.app.data.summary.LocalQa(
            modelManager = summaryModelManager,
            threads = com.vocatim.app.data.transcribe.ThreadPolicy(appContext)
                .threadsFor(settings.threads),
            model = com.vocatim.app.data.summary.SummaryModel.fromId(settings.summaryModel),
            ctxCapTokens = com.vocatim.app.data.summary.ContextBudget.ramCapTokens(appContext),
        ).answer(transcript, question, language)
    }

    // --- Translate transcript/minutes to any language (BYOK) ---
    private val _translation = MutableStateFlow<String?>(null)
    val translation: StateFlow<String?> = _translation.asStateFlow()

    private val _translateBusy = MutableStateFlow(false)
    val translateBusy: StateFlow<Boolean> = _translateBusy.asStateFlow()

    fun translate(targetLanguage: String) {
        val text = currentText()
        if (text.isBlank() || _translateBusy.value) return
        viewModelScope.launch {
            _translateBusy.value = true
            _translation.value = runCatching {
                cloudClient.chat(
                    config = cloudAiPrefs.current(),
                    system = com.vocatim.app.data.cloud.CloudPrompts
                        .translateSystem(targetLanguage),
                    user = text.take(com.vocatim.app.data.cloud.CloudPrompts.MAX_INPUT_CHARS),
                    maxTokens = 8192,
                )
            }.getOrElse { it.message ?: "Translation failed" }
            _translateBusy.value = false
        }
    }

    fun clearTranslation() {
        _translation.value = null
    }

    fun clearSummary() {
        viewModelScope.launch { repository.updateSummary(transcriptId, null, null) }
    }

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

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _skipSilence = MutableStateFlow(false)
    val skipSilence: StateFlow<Boolean> = _skipSilence.asStateFlow()

    fun toggleSkipSilence() {
        _skipSilence.value = !_skipSilence.value
    }

    fun cyclePlaybackSpeed() {
        val next = when (_playbackSpeed.value) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 2.0f
            else -> 1.0f
        }
        _playbackSpeed.value = next
        // Applying params to a paused player force-starts it; only touch live.
        player?.let { p ->
            if (p.isPlaying) {
                runCatching { p.playbackParams = p.playbackParams.setSpeed(next) }
            }
        }
    }

    private fun applySpeed(p: android.media.MediaPlayer) {
        val speed = _playbackSpeed.value
        if (speed != 1.0f) {
            runCatching { p.playbackParams = p.playbackParams.setSpeed(speed) }
        }
    }

    /** Sentence-level extractive summary; instant and fully offline. */
    fun keyPoints(): List<String> =
        com.vocatim.app.data.transcribe.KeyPointsExtractor.extract(currentText())

    /** Other finished transcripts that can be merged into this one. */
    suspend fun mergeCandidates(): List<TranscriptEntity> =
        repository.observeAll().first().filter {
            it.id != transcriptId && it.status == TranscriptStatus.DONE
        }

    fun merge(otherIds: List<Long>, onMerged: (Long) -> Unit) {
        viewModelScope.launch {
            val newId = repository.merge(listOf(transcriptId) + otherIds)
            if (newId != null) onMerged(newId)
        }
    }

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

    private fun savePlaybackPosition() {
        val ms = player?.currentPosition?.toLong() ?: return
        viewModelScope.launch { repository.setPlaybackPosition(transcriptId, ms) }
    }

    fun togglePlayback() {
        val existing = player
        if (existing == null) {
            startPlayback()
        } else if (existing.isPlaying) {
            existing.pause()
            positionJob?.cancel()
            savePlaybackPosition()
            _playerState.value = _playerState.value?.copy(playing = false)
        } else {
            existing.start()
            applySpeed(existing)
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
            applySpeed(existing)
            startPositionUpdates()
        }
    }

    fun seekToFraction(fraction: Float) {
        val duration = transcript.value?.audioDurationMs ?: return
        playFromMs((duration * fraction.coerceIn(0f, 1f)).toLong())
    }

    private fun startPlayback(seekToMs: Int = -1) {
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
                    viewModelScope.launch { repository.setPlaybackPosition(transcriptId, 0) }
                    _playerState.value =
                        PlayerState(playing = false, positionMs = 0, durationMs = created.duration)
                }
                player = created
                // Explicit seek wins; otherwise resume where the user stopped.
                val resume = transcript.value?.playbackPositionMs?.toInt() ?: 0
                val target = if (seekToMs >= 0) seekToMs else resume
                if (target in 1 until created.duration) created.seekTo(target)
                created.start()
                applySpeed(created)
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
                if (_skipSilence.value) skipSilentRegion(p)
                _playerState.value = PlayerState(
                    playing = p.isPlaying,
                    positionMs = p.currentPosition,
                    durationMs = p.duration,
                )
                kotlinx.coroutines.delay(200)
            }
        }
    }

    /** When the playhead sits in a quiet stretch, jump to the next audible part. */
    private fun skipSilentRegion(p: android.media.MediaPlayer) {
        val wf = _waveform.value ?: return
        val dur = p.duration
        if (dur <= 0 || wf.isEmpty()) return
        val max = wf.maxOrNull()?.takeIf { it > 0f } ?: return
        val thresh = max * 0.12f
        val bucket = (p.currentPosition.toLong() * wf.size / dur)
            .toInt().coerceIn(0, wf.size - 1)
        if (wf[bucket] >= thresh) return
        var next = bucket + 1
        while (next < wf.size && wf[next] < thresh) next++
        // Only jump over a real gap (more than one bucket ~ a few seconds).
        if (next < wf.size && next > bucket + 1) {
            p.seekTo((next.toLong() * dur / wf.size).toInt())
        }
    }

    fun setLocked(locked: Boolean) {
        viewModelScope.launch { repository.setLocked(transcriptId, locked) }
    }

    override fun onCleared() {
        super.onCleared()
        positionJob?.cancel()
        savePlaybackPosition()
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

    /** Resolves which body of text an export carries. */
    private fun textFor(source: String): String = when (source) {
        "minutes" -> transcript.value?.minutes ?: currentText()
        "summary" -> transcript.value?.summary ?: currentText()
        else -> currentText()
    }

    fun exportTxt(uri: Uri, source: String = "transcript") =
        export(uri) { textFor(source) }

    fun exportSrt(uri: Uri) = export(uri) {
        SrtFormatter.format(repository.getSegments(transcriptId))
    }

    fun exportVtt(uri: Uri) = export(uri) {
        VttFormatter.format(repository.getSegments(transcriptId))
    }

    fun exportMarkdown(
        uri: Uri,
        withTimestamps: Boolean,
        source: String = "transcript",
    ) = export(uri) {
        val title = transcript.value?.title ?: "Transcript"
        if (source == "transcript" && withTimestamps) {
            MarkdownFormatter.formatWithTimestamps(title, repository.getSegments(transcriptId))
        } else {
            MarkdownFormatter.formatPlain(title, textFor(source))
        }
    }

    fun exportPdf(uri: Uri, source: String = "transcript") {
        viewModelScope.launch {
            try {
                val t = transcript.value
                val title = t?.title ?: "Transcript"
                val photos = repository.getAttachments(transcriptId).map { it.path }
                val meta = t?.let(::exportMeta)
                withContext(Dispatchers.IO) {
                    PdfExporter.write(appContext, uri, title, textFor(source), photos, meta)
                }
                _exportEvent.value = ExportEvent.Success
            } catch (e: Exception) {
                _exportEvent.value = ExportEvent.Failure(e.message ?: "export failed")
            }
        }
    }

    private fun exportMeta(t: TranscriptEntity): String {
        val date = java.text.SimpleDateFormat("MMMM d, yyyy · HH:mm", java.util.Locale.US)
            .format(java.util.Date(t.createdAt))
        return if (t.audioDurationMs > 0) "$date · ${formatClock(t.audioDurationMs)}" else date
    }

    /** Renders the note to a cached PDF and hands back a shareable URI. */
    fun sharePdf(onReady: (Uri) -> Unit) {
        viewModelScope.launch {
            val t = transcript.value ?: return@launch
            val uri = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = java.io.File(appContext.cacheDir, "share").apply { mkdirs() }
                    val file = java.io.File(
                        dir, com.vocatim.app.ui.common.exportFileName(t.title, "pdf")
                    )
                    val provider = androidx.core.content.FileProvider.getUriForFile(
                        appContext, "${appContext.packageName}.fileprovider", file
                    )
                    val photos = repository.getAttachments(transcriptId).map { it.path }
                    PdfExporter.write(appContext, provider, t.title, currentText(), photos, exportMeta(t))
                    provider
                }.getOrNull()
            } ?: return@launch
            onReady(uri)
        }
    }

    /** FileProvider URI for the audio file, or null if there is none. */
    fun shareAudioUri(): Uri? {
        val path = transcript.value?.audioPath ?: return null
        return runCatching {
            androidx.core.content.FileProvider.getUriForFile(
                appContext, "${appContext.packageName}.fileprovider", java.io.File(path)
            )
        }.getOrNull()
    }

    fun copyWithTimestamps(): String {
        val segments = _segments.value
        if (segments.isNotEmpty()) {
            return segments.joinToString("\n") { seg ->
                "${formatClock(seg.startMs)} ${seg.text.trim()}"
            }
        }
        return currentText()
    }

    fun copyWithTimestampsAsync(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val segments = _segments.value.ifEmpty { repository.getSegments(transcriptId) }
            _segments.value = segments
            val text = if (segments.isNotEmpty()) {
                segments.joinToString("\n") { seg ->
                    "${formatClock(seg.startMs)} ${seg.text.trim()}"
                }
            } else currentText()
            onReady(text)
        }
    }

    fun setTag(tag: String?) {
        viewModelScope.launch {
            repository.setFolder(transcriptId, tag)
        }
    }

    /** Existing folder names, for the folder picker. */
    val folders: StateFlow<List<String>> = repository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val attachments: StateFlow<List<com.vocatim.app.data.db.AttachmentEntity>> =
        repository.observeAttachments(transcriptId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Copies the picked image into app storage and links it to this transcript. */
    fun addPhoto(uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = java.io.File(appContext.filesDir, "attachments").apply { mkdirs() }
                    val out = java.io.File(dir, "att_${transcriptId}_${System.currentTimeMillis()}.jpg")
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        out.outputStream().use { input.copyTo(it) }
                    } ?: return@runCatching null
                    out.absolutePath
                }.getOrNull()
            } ?: return@launch
            repository.addAttachment(transcriptId, path)
        }
    }

    fun removePhoto(attachment: com.vocatim.app.data.db.AttachmentEntity) {
        viewModelScope.launch { repository.removeAttachment(attachment) }
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
