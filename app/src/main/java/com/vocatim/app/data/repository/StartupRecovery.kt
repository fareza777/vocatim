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
) {
    suspend fun recover() {
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

    /** Share-intent grants die with the process; probe before re-queueing. */
    private fun canReadSource(sourceUri: String?): Boolean {
        if (sourceUri == null) return false
        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(sourceUri))?.use { }
            true
        }.getOrDefault(false)
    }
}
