package com.vocatim.app.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.vocatim.app.data.db.TranscriptEntity
import com.vocatim.app.data.db.TranscriptStatus
import com.vocatim.app.data.prefs.UserPrefs
import com.vocatim.app.service.TranscriptionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Entry point for "transcribe this external file": creates the transcript
 * row (so Home shows it immediately) and hands the URI to the service,
 * which converts and transcribes.
 */
class ImportCoordinator(
    private val context: Context,
    private val repository: TranscriptRepository,
    private val userPrefs: UserPrefs,
) {
    suspend fun startImport(uri: Uri): Long {
        // SAF picks allow persisting the grant, which lets conversion resume
        // after a process kill. Share intents don't; recovery then fails
        // with a clear message instead of silently.
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val name = displayName(uri)
        val settings = userPrefs.current()
        val id = repository.create(
            TranscriptEntity(
                title = name ?: "Import",
                language = settings.language,
                modelId = settings.model.id,
                audioPath = null,
                status = TranscriptStatus.PENDING,
                sourceName = name,
                sourceUri = uri.toString(),
                translate = settings.translate,
            )
        )
        TranscriptionService.enqueue(context, id, sourceUri = uri)
        return id
    }

    private suspend fun displayName(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
    }
}
