package com.vocatim.app.data.repository

import com.vocatim.app.data.db.AttachmentEntity
import com.vocatim.app.data.db.SegmentEntity
import com.vocatim.app.data.db.TranscriptDao
import com.vocatim.app.data.db.TranscriptEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/** Snapshot of a deleted transcript, kept in memory to power undo. */
data class DeletedTranscript(
    val transcript: TranscriptEntity,
    val segments: List<SegmentEntity>,
    val attachments: List<AttachmentEntity>,
)

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

    /**
     * Deletes the DB rows but keeps the files, returning a snapshot so the
     * delete can be undone. Call [purgeDeletedFiles] once the undo window
     * closes, or [restore] to bring it back.
     */
    suspend fun deleteForUndo(id: Long): DeletedTranscript? {
        val entity = dao.getById(id) ?: return null
        val segments = dao.getSegments(id)
        val attachments = dao.getAttachments(id)
        dao.deleteById(id) // cascade removes segment + attachment rows
        return DeletedTranscript(entity, segments, attachments)
    }

    suspend fun restore(deleted: DeletedTranscript) {
        val newId = dao.insert(deleted.transcript.copy(id = 0))
        dao.insertSegments(deleted.segments.map { it.copy(id = 0, transcriptId = newId) })
        dao.insertAttachments(deleted.attachments.map { it.copy(id = 0, transcriptId = newId) })
    }

    suspend fun purgeDeletedFiles(deleted: DeletedTranscript) {
        withContext(Dispatchers.IO) {
            deleted.transcript.audioPath?.let { File(it).delete() }
            deleted.attachments.forEach { File(it.path).delete() }
        }
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
