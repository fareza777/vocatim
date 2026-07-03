package com.vocatim.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transcripts")
data class TranscriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val text: String,
    /** ISO 639-1 code selected by the user, or "auto". */
    val language: String,
    /** Model id used, e.g. "tiny", "base". */
    val modelId: String,
    /** Duration of the source audio in milliseconds. */
    val audioDurationMs: Long,
    /** Wall-clock transcription time in milliseconds. */
    val processingTimeMs: Long,
    /** Absolute path of the audio file in app storage; null when not retained. */
    val audioPath: String?,
    val createdAt: Long = System.currentTimeMillis(),
)
