package com.vocatim.app.data.backup

import android.content.Context
import android.net.Uri
import com.vocatim.app.data.db.SegmentEntity
import com.vocatim.app.data.repository.TranscriptRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException

/** Streams encrypted backups to/from SAF locations chosen by the user. */
class BackupManager(
    private val context: Context,
    private val repository: TranscriptRepository,
) {

    /** @return number of transcripts written. */
    suspend fun export(uri: Uri, password: CharArray): Int = withContext(Dispatchers.IO) {
        val transcripts = repository.observeAll().first()
        val segments = mutableListOf<SegmentEntity>()
        val attachments = mutableListOf<BackupAttachment>()
        transcripts.forEach { t ->
            segments.addAll(repository.getSegments(t.id))
            repository.getAttachments(t.id).forEach { att ->
                val file = java.io.File(att.path)
                if (file.exists()) {
                    attachments.add(
                        BackupAttachment(
                            transcriptId = t.id,
                            fileName = file.name,
                            base64 = android.util.Base64.encodeToString(
                                file.readBytes(), android.util.Base64.NO_WRAP
                            ),
                        )
                    )
                }
            }
        }

        val blob = BackupCodec.encrypt(
            BackupData(transcripts, segments, attachments), password
        )
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(blob) }
            ?: throw IOException("Couldn't open backup destination")
        transcripts.size
    }

    /**
     * Imports transcripts that don't already exist (matched on
     * createdAt + title). Audio files are not part of backups.
     * @return number of transcripts imported.
     */
    suspend fun import(uri: Uri, password: CharArray): Int = withContext(Dispatchers.IO) {
        val blob = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("Couldn't read backup file")
        val data = BackupCodec.decrypt(blob, password)

        val existing = repository.observeAll().first()
            .map { it.createdAt to it.title }
            .toHashSet()

        var imported = 0
        for (transcript in data.transcripts) {
            if ((transcript.createdAt to transcript.title) in existing) continue
            val newId = repository.create(transcript.copy(id = 0))
            val ownSegments = data.segments
                .filter { it.transcriptId == transcript.id }
                .map { it.copy(id = 0, transcriptId = newId) }
            if (ownSegments.isNotEmpty()) {
                repository.appendSegments(newId, ownSegments)
            }
            // Restore attached photos into app storage.
            data.attachments
                .filter { it.transcriptId == transcript.id }
                .forEach { att ->
                    runCatching {
                        val dir = java.io.File(context.filesDir, "attachments")
                            .apply { mkdirs() }
                        val out = java.io.File(dir, "restored_${newId}_${att.fileName}")
                        out.writeBytes(
                            android.util.Base64.decode(att.base64, android.util.Base64.NO_WRAP)
                        )
                        repository.addAttachment(newId, out.absolutePath)
                    }
                }
            imported++
        }
        imported
    }
}
