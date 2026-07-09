package com.vocatim.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

object TranscriptStatus {
    /** Waiting for the transcription service to pick it up. */
    const val PENDING = "PENDING"
    /** Converting an imported file to 16kHz WAV. */
    const val CONVERTING = "CONVERTING"
    const val TRANSCRIBING = "TRANSCRIBING"
    const val DONE = "DONE"
    const val FAILED = "FAILED"
}

@Entity(tableName = "transcripts")
data class TranscriptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    /** Merged transcript text; grows chunk-by-chunk while transcribing. */
    val text: String = "",
    /** ISO 639-1 code selected by the user, or "auto". */
    val language: String,
    /** Model id used, e.g. "tiny", "base". */
    val modelId: String,
    /** Duration of the source audio in milliseconds; 0 until known. */
    val audioDurationMs: Long = 0,
    /** Wall-clock transcription time in milliseconds; 0 until finished. */
    val processingTimeMs: Long = 0,
    /** Absolute path of the 16kHz WAV in app storage; null after deletion. */
    val audioPath: String?,
    /** One of [TranscriptStatus]. */
    val status: String,
    val errorMessage: String? = null,
    /** Original file name for imported audio; null for recordings. */
    val sourceName: String? = null,
    /** Content URI of the original import, for conversion retry after restart. */
    val sourceUri: String? = null,
    /** Whisper translate-to-English mode captured at creation time. */
    val translate: Boolean = false,
    /** Checkpoint: chunks fully persisted; resume continues from here. */
    val completedChunks: Int = 0,
    /** True once the user renames; blocks auto-titling from content. */
    val customTitle: Boolean = false,
    /** Language Whisper detected when the user chose "auto". */
    val detectedLanguage: String? = null,
    /** Pinned transcripts stay at the top of the home list. */
    val pinned: Boolean = false,
    /** Optional folder/tag label, e.g. "work", "study". */
    val tag: String? = null,
    /** AI-generated summary; null until the user runs it. */
    val summary: String? = null,
    /** Which engine produced [summary]: "local" or "cloud"; null if none. */
    val summarySource: String? = null,
    /** AI meeting minutes for THIS transcript; null until generated. */
    val minutes: String? = null,
    /** Recording bookmarks as comma-separated millisecond offsets; null if none. */
    val markers: String? = null,
    /** Soft-delete timestamp: non-null rows live in the trash for 30 days. */
    val deletedAt: Long? = null,
    /** Last audio playback position in ms, to resume where the user stopped. */
    val playbackPositionMs: Long = 0,
    /** When true, opening this note requires biometric even if app-lock is off. */
    val locked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
