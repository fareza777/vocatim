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
        val name = displayName(uri)
        val id = repository.create(
            TranscriptEntity(
                title = name ?: "Import",
                language = userPrefs.language(),
                modelId = userPrefs.model().id,
                audioPath = null,
                status = TranscriptStatus.PENDING,
                sourceName = name,
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
