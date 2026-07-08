package com.vocatim.app.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.vocatim.app.data.prefs.UserPrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes an encrypted backup into the user-chosen folder once a week.
 * Checked on app start — no scheduler dependency, no wakeups.
 */
class AutoBackup(
    private val context: Context,
    private val userPrefs: UserPrefs,
    private val backupManager: BackupManager,
) {
    suspend fun runIfDue() {
        val settings = userPrefs.current()
        if (settings.autoBackupUri.isBlank() || settings.autoBackupPw.isBlank()) return
        val now = System.currentTimeMillis()
        if (now - settings.autoBackupLast < INTERVAL_MS) return
        val password = KeystoreCrypto.decrypt(settings.autoBackupPw) ?: return

        runCatching {
            val tree = Uri.parse(settings.autoBackupUri)
            val parent = DocumentsContract.buildDocumentUriUsingTree(
                tree, DocumentsContract.getTreeDocumentId(tree)
            )
            val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(now))
            val target = DocumentsContract.createDocument(
                context.contentResolver,
                parent,
                "application/octet-stream",
                "vocatim-auto-$stamp.vbk",
            ) ?: return
            backupManager.export(target, password.toCharArray())
            userPrefs.setAutoBackupLast(now)
        }
    }

    private companion object {
        const val INTERVAL_MS = 7L * 24 * 60 * 60 * 1000
    }
}
