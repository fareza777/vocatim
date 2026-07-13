package com.vocatim.app.data.repository

import com.vocatim.app.data.db.AttachmentEntity
import com.vocatim.app.data.db.SegmentEntity
import com.vocatim.app.data.db.TranscriptDao
import com.vocatim.app.data.db.TranscriptEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class TranscriptRepository(private val dao: TranscriptDao) {

    fun observeAll(): Flow<List<TranscriptEntity>> = dao.observeAll()

    fun observeFolders(): Flow<List<String>> = dao.observeFolders()

    fun observeAttachments(transcriptId: Long): Flow<List<AttachmentEntity>> =
        dao.observeAttachments(transcriptId)

    suspend fun getAttachments(transcriptId: Long): List<AttachmentEntity> =
        dao.getAttachments(transcriptId)

    suspend fun setFolder(id: Long, folder: String?) =
        dao.updateTag(id, folder?.trim()?.ifBlank { null })

    suspend fun addAttachment(transcriptId: Long, path: String): Long =
        dao.insertAttachment(AttachmentEntity(transcriptId = transcriptId, path = path))

    suspend fun removeAttachment(attachment: AttachmentEntity) {
        withContext(Dispatchers.IO) { File(attachment.path).delete() }
        dao.deleteAttachment(attachment.id)
    }

    fun observeTotalDurationMs(): Flow<Long> = dao.observeTotalDurationMs()

    fun observeCount(): Flow<Int> = dao.observeCount()

    fun observeById(id: Long): Flow<TranscriptEntity?> = dao.observeById(id)

    suspend fun getById(id: Long): TranscriptEntity? = dao.getById(id)

    suspend fun create(transcript: TranscriptEntity): Long = dao.insert(transcript)

    suspend fun update(transcript: TranscriptEntity) = dao.update(transcript)

    suspend fun updateText(id: Long, text: String) = dao.updateText(id, text)

    suspend fun updateTitle(id: Long, title: String) = dao.updateTitle(id, title)

    suspend fun setPinned(id: Long, pinned: Boolean) = dao.updatePinned(id, pinned)

    suspend fun setTag(id: Long, tag: String?) = dao.updateTag(id, tag)

    suspend fun updateStatus(id: Long, status: String, error: String? = null) =
        dao.updateStatus(id, status, error)

    suspend fun updateCheckpoint(id: Long, completedChunks: Int) =
        dao.updateCheckpoint(id, completedChunks)

    suspend fun updateSummary(id: Long, summary: String?, source: String? = null) =
        dao.updateSummary(id, summary, source)

    suspend fun setMarkers(id: Long, markers: String?) =
        dao.setMarkers(id, markers)

    suspend fun updateMinutes(id: Long, minutes: String?) =
        dao.updateMinutes(id, minutes)

    suspend fun setPlaybackPosition(id: Long, ms: Long) =
        dao.setPlaybackPosition(id, ms)

    suspend fun setLocked(id: Long, locked: Boolean) =
        dao.setLocked(id, locked)

    // --- Trash: soft delete with a 30-day retention window ---

    fun observeTrash(): Flow<List<TranscriptEntity>> = dao.observeTrash()

    /** Moves a transcript to the trash; files stay until purge. */
    suspend fun moveToTrash(id: Long) = dao.setDeletedAt(id, System.currentTimeMillis())

    suspend fun restoreFromTrash(id: Long) = dao.setDeletedAt(id, null)

    suspend fun getAllAudioPaths(): List<String> = dao.getAllAudioPaths()

    /** Hard-deletes trashed rows past the retention window, files included. */
    suspend fun purgeExpiredTrash(retentionMs: Long = TRASH_RETENTION_MS) {
        val cutoff = System.currentTimeMillis() - retentionMs
        dao.getTrashOlderThan(cutoff).forEach { delete(it.id) }
    }

    companion object {
        const val TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }

    suspend fun getByStatuses(statuses: List<String>): List<TranscriptEntity> =
        dao.getByStatuses(statuses)

    suspend fun appendSegments(transcriptId: Long, segments: List<SegmentEntity>) =
        dao.insertSegments(segments)

    suspend fun commitChunk(
        transcriptId: Long,
        segments: List<SegmentEntity>,
        text: String,
        completedChunks: Int,
    ) = dao.commitChunk(transcriptId, segments, text, completedChunks)

    suspend fun getSegments(transcriptId: Long): List<SegmentEntity> =
        dao.getSegments(transcriptId)

    /** Writes diarized speaker indices onto their segments. */
    suspend fun setSegmentSpeakers(assignments: Map<Long, Int>) {
        assignments.forEach { (segmentId, speaker) ->
            dao.setSegmentSpeaker(segmentId, speaker)
        }
    }

    suspend fun setSpeakerNames(transcriptId: Long, namesJson: String?) =
        dao.setSpeakerNames(transcriptId, namesJson)

    suspend fun setUserNotes(transcriptId: Long, notes: String?) =
        dao.setUserNotes(transcriptId, notes)

    /** Ids of transcripts matching an FTS query, live-updating. */
    fun observeSearchIds(query: String) = dao.observeSearchIds(query)

    suspend fun clearSegments(transcriptId: Long) = dao.deleteSegments(transcriptId)

    /**
     * Combines several finished transcripts into a new one: texts joined,
     * segment timestamps shifted onto one continuous timeline. Audio files
     * are not merged — the result is text-only.
     */
    suspend fun merge(ids: List<Long>): Long? {
        val entities = ids.mapNotNull { dao.getById(it) }
        if (entities.size < 2) return null
        val first = entities.first()

        var offsetMs = 0L
        val mergedSegments = mutableListOf<SegmentEntity>()
        val textParts = mutableListOf<String>()
        for (entity in entities) {
            textParts.add(entity.text)
            val segments = dao.getSegments(entity.id)
            segments.forEach { s ->
                mergedSegments.add(
                    s.copy(id = 0, startMs = s.startMs + offsetMs, endMs = s.endMs + offsetMs)
                )
            }
            val partDuration = when {
                entity.audioDurationMs > 0 -> entity.audioDurationMs
                segments.isNotEmpty() -> segments.last().endMs
                else -> 0L
            }
            offsetMs += partDuration
        }

        val mergedId = dao.insert(
            first.copy(
                id = 0,
                title = entities.joinToString(" + ") { it.title }.take(80),
                text = textParts.filter { it.isNotBlank() }.joinToString("\n\n"),
                audioDurationMs = offsetMs,
                processingTimeMs = entities.sumOf { it.processingTimeMs },
                audioPath = null,
                sourceUri = null,
                sourceName = null,
                customTitle = true,
                createdAt = System.currentTimeMillis(),
            )
        )
        dao.insertSegments(mergedSegments.map { it.copy(transcriptId = mergedId) })
        return mergedId
    }

    /** Deletes the row, its segments/attachments (cascade), and files on disk. */
    suspend fun delete(id: Long) {
        val entity = dao.getById(id) ?: return
        val attachments = dao.getAttachments(id)
        withContext(Dispatchers.IO) {
            entity.audioPath?.let { File(it).delete() }
            attachments.forEach { File(it.path).delete() }
        }
        dao.deleteById(id)
    }

    /** Removes only the audio file to free space, keeping the transcript. */
    suspend fun deleteAudioFile(id: Long) {
        val entity = dao.getById(id) ?: return
        withContext(Dispatchers.IO) {
            entity.audioPath?.let { File(it).delete() }
        }
        dao.update(entity.copy(audioPath = null))
    }
}
