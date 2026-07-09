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

    @Query("SELECT * FROM transcripts WHERE deletedAt IS NULL ORDER BY pinned DESC, createdAt DESC")
    fun observeAll(): Flow<List<TranscriptEntity>>

    @Query(
        "SELECT COALESCE(SUM(audioDurationMs), 0) FROM transcripts " +
            "WHERE status = 'DONE' AND deletedAt IS NULL"
    )
    fun observeTotalDurationMs(): Flow<Long>

    @Query("SELECT COUNT(*) FROM transcripts WHERE deletedAt IS NULL")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM transcripts WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrash(): Flow<List<TranscriptEntity>>

    @Query("UPDATE transcripts SET deletedAt = :ts WHERE id = :id")
    suspend fun setDeletedAt(id: Long, ts: Long?)

    @Query("SELECT * FROM transcripts WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun getTrashOlderThan(cutoff: Long): List<TranscriptEntity>

    /** Every referenced audio path, trash included — for orphan-file scans. */
    @Query("SELECT audioPath FROM transcripts WHERE audioPath IS NOT NULL")
    suspend fun getAllAudioPaths(): List<String>

    @Query("UPDATE transcripts SET pinned = :pinned WHERE id = :id")
    suspend fun updatePinned(id: Long, pinned: Boolean)

    @Query("UPDATE transcripts SET tag = :tag WHERE id = :id")
    suspend fun updateTag(id: Long, tag: String?)

    /** Distinct non-empty folder names, for the Home folder filter. */
    @Query(
        "SELECT DISTINCT tag FROM transcripts " +
            "WHERE tag IS NOT NULL AND tag != '' AND deletedAt IS NULL ORDER BY tag"
    )
    fun observeFolders(): Flow<List<String>>

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

    @Query("UPDATE transcripts SET summary = :summary, summarySource = :source WHERE id = :id")
    suspend fun updateSummary(id: Long, summary: String?, source: String?)

    @Query("UPDATE transcripts SET markers = :markers WHERE id = :id")
    suspend fun setMarkers(id: Long, markers: String?)

    @Query("UPDATE transcripts SET minutes = :minutes WHERE id = :id")
    suspend fun updateMinutes(id: Long, minutes: String?)

    @Query("UPDATE transcripts SET playbackPositionMs = :ms WHERE id = :id")
    suspend fun setPlaybackPosition(id: Long, ms: Long)

    @Query("UPDATE transcripts SET locked = :locked WHERE id = :id")
    suspend fun setLocked(id: Long, locked: Boolean)

    @Query("SELECT * FROM transcripts WHERE status IN (:statuses) AND deletedAt IS NULL")
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

    @Insert
    suspend fun insertAttachment(attachment: AttachmentEntity): Long

    @Insert
    suspend fun insertAttachments(attachments: List<AttachmentEntity>)

    @Query("SELECT * FROM attachments WHERE transcriptId = :transcriptId ORDER BY createdAt")
    fun observeAttachments(transcriptId: Long): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE transcriptId = :transcriptId ORDER BY createdAt")
    suspend fun getAttachments(transcriptId: Long): List<AttachmentEntity>

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteAttachment(id: Long)
}
