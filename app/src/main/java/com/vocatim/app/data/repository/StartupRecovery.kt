package com.vocatim.app.data.repository

import android.content.Context
import android.net.Uri
import com.vocatim.app.data.db.TranscriptStatus
import com.vocatim.app.service.TranscriptionService

/**
 * Runs once at app start: transcripts left in an in-flight state by a
 * process kill are re-queued (resume from checkpoint) when their audio is
 * still available, otherwise marked failed so the user sees a retry option.
 */
class StartupRecovery(
    private val context: Context,
    private val repository: TranscriptRepository,
    private val userPrefs: com.vocatim.app.data.prefs.UserPrefs,
) {
    suspend fun recover() {
        // Trash retention: hard-delete anything trashed more than 30 days ago.
        repository.purgeExpiredTrash()
        recoverOrphanRecordings()
        val stuck = repository.getByStatuses(
            listOf(
                TranscriptStatus.PENDING,
                TranscriptStatus.CONVERTING,
                TranscriptStatus.TRANSCRIBING,
            )
        )
        for (entity in stuck) {
            val canResume = entity.audioPath != null || canReadSource(entity.sourceUri)
            if (canResume) {
                repository.updateStatus(entity.id, TranscriptStatus.PENDING)
                TranscriptionService.enqueue(
                    context, entity.id, sourceUri = entity.sourceUri?.let(Uri::parse)
                )
            } else {
                repository.updateStatus(entity.id, TranscriptStatus.FAILED, "SOURCE_GONE")
            }
        }
    }

    /**
     * A crash mid-recording leaves a WAV on disk with no DB row (the row is
     * created on stop). Surface such orphans as recovered notes and queue
     * them for transcription instead of silently losing the audio.
     */
    private suspend fun recoverOrphanRecordings() {
        val dir = java.io.File(context.filesDir, "recordings")
        val known = repository.getAllAudioPaths().toHashSet()
        val settings = userPrefs.current()
        dir.listFiles { f -> f.isFile && f.extension == "wav" }
            ?.filter { it.absolutePath !in known }
            ?.forEach { wav ->
                val durationMs = runCatching {
                    com.vocatim.app.data.audio.WavStreamReader(wav).use { it.durationMs }
                }.getOrDefault(0L)
                // Under a second of audio is a torn header, not a recording.
                if (durationMs < 1000) {
                    wav.delete()
                    return@forEach
                }
                val id = repository.create(
                    com.vocatim.app.data.db.TranscriptEntity(
                        title = "Recovered recording",
                        language = settings.language,
                        modelId = settings.model.id,
                        audioPath = wav.absolutePath,
                        audioDurationMs = durationMs,
                        status = TranscriptStatus.PENDING,
                        createdAt = wav.lastModified().takeIf { it > 0 }
                            ?: System.currentTimeMillis(),
                    )
                )
                TranscriptionService.enqueue(context, id, sourceUri = null)
            }
    }

    /** Share-intent grants die with the process; probe before re-queueing. */
    private fun canReadSource(sourceUri: String?): Boolean {
        if (sourceUri == null) return false
        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(sourceUri))?.use { }
            true
        }.getOrDefault(false)
    }
}
