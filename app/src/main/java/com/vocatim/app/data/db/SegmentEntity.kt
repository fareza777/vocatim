package com.vocatim.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One Whisper segment; kept per transcript so SRT export has timestamps. */
@Entity(
    tableName = "segments",
    foreignKeys = [
        ForeignKey(
            entity = TranscriptEntity::class,
            parentColumns = ["id"],
            childColumns = ["transcriptId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("transcriptId")],
)
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transcriptId: Long,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    /** Word timings as JSON [[startMs,endMs,"word"],...]; null when absent. */
    val words: String? = null,
)
