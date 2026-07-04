package com.vocatim.app.data.repository

import com.vocatim.app.data.db.SegmentEntity
import com.vocatim.app.data.db.TranscriptDao
import com.vocatim.app.data.db.TranscriptEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class TranscriptRepository(private val dao: TranscriptDao) {

    fun observeAll(): Flow<List<TranscriptEntity>> = dao.observeAll()

    fun observeById(id: Long): Flow<TranscriptEntity?> = dao.observeById(id)

    suspend fun getById(id: Long): TranscriptEntity? = dao.getById(id)

    suspend fun create(transcript: TranscriptEntity): Long = dao.insert(transcript)

    suspend fun update(transcript: TranscriptEntity) = dao.update(transcript)

    suspend fun updateText(id: Long, text: String) = dao.updateText(id, text)

    suspend fun updateTitle(id: Long, title: String) = dao.updateTitle(id, title)

    suspend fun updateStatus(id: Long, status: String, error: String? = null) =
        dao.updateStatus(id, status, error)

    suspend fun updateCheckpoint(id: Long, completedChunks: Int) =
        dao.updateCheckpoint(id, completedChunks)

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

    /** Deletes the row, its segments (cascade), and the audio file on disk. */
    suspend fun delete(id: Long) {
        val entity = dao.getById(id) ?: return
        withContext(Dispatchers.IO) {
            entity.audioPath?.let { File(it).delete() }
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
