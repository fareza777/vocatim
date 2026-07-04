package com.vocatim.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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

    @Query("UPDATE transcripts SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Insert
    suspend fun insertSegments(segments: List<SegmentEntity>)

    @Query("SELECT * FROM segments WHERE transcriptId = :transcriptId ORDER BY startMs")
    suspend fun getSegments(transcriptId: Long): List<SegmentEntity>

    @Query("DELETE FROM segments WHERE transcriptId = :transcriptId")
    suspend fun deleteSegments(transcriptId: Long)
}
