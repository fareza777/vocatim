package com.vocatim.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptDao {
    @Insert
    suspend fun insert(transcript: TranscriptEntity): Long

    @Update
    suspend fun update(transcript: TranscriptEntity)

    @Query("SELECT * FROM transcripts ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TranscriptEntity>>

    @Query("SELECT * FROM transcripts WHERE id = :id")
    suspend fun getById(id: Long): TranscriptEntity?

    @Query("SELECT * FROM transcripts WHERE id = :id")
    fun observeById(id: Long): Flow<TranscriptEntity?>

    @Query("DELETE FROM transcripts WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE transcripts SET text = :text WHERE id = :id")
    suspend fun updateText(id: Long, text: String)

    @Query("UPDATE transcripts SET status = :status, errorMessage = :error WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, error: String? = null)

    @Query("UPDATE transcripts SET title = :title, customTitle = 1 WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("UPDATE transcripts SET completedChunks = :completedChunks WHERE id = :id")
    suspend fun updateCheckpoint(id: Long, completedChunks: Int)

    @Query("SELECT * FROM transcripts WHERE status IN (:statuses)")
    suspend fun getByStatuses(statuses: List<String>): List<TranscriptEntity>

    @Insert
    suspend fun insertSegments(segments: List<SegmentEntity>)

    /**
     * Atomic per-chunk checkpoint: segments, merged text, and the resume
     * cursor land together, so a kill mid-write can't cause duplicated
     * segments on resume.
     */
    @Transaction
    suspend fun commitChunk(
        id: Long,
        segments: List<SegmentEntity>,
        text: String,
        completedChunks: Int,
    ) {
        insertSegments(segments)
        updateText(id, text)
        updateCheckpoint(id, completedChunks)
    }

    @Query("SELECT * FROM segments WHERE transcriptId = :transcriptId ORDER BY startMs")
    suspend fun getSegments(transcriptId: Long): List<SegmentEntity>

    @Query("DELETE FROM segments WHERE transcriptId = :transcriptId")
    suspend fun deleteSegments(transcriptId: Long)
}
