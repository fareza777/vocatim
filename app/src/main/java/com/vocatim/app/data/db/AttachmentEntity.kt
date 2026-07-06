package com.vocatim.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A photo attached to a transcript; [path] is a file in app storage. */
@Entity(
    tableName = "attachments",
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
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transcriptId: Long,
    val path: String,
    val createdAt: Long = System.currentTimeMillis(),
)
