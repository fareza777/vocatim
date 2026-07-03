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

    @Query("DELETE FROM transcripts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
