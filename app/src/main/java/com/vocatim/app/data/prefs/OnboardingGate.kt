package com.vocatim.app.data.prefs

import android.content.Context
import com.vocatim.app.data.cloud.CloudTranscribePrefs
import com.vocatim.app.data.model.CloudEngine
import com.vocatim.app.data.model.ModelManager
import com.vocatim.app.data.model.ParakeetModel
import com.vocatim.app.data.model.ParakeetModelManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides whether onboarding may be skipped.
 *
 * The settings file is the only thing Android Auto Backup restores: models,
 * the database and the API key stay on the old device. A restore therefore
 * arrives with onboarding_done = true and nothing to transcribe with, so the
 * flag alone is not evidence that setup ever happened *on this install*.
 *
 * A marker in noBackupFilesDir supplies that evidence. It survives updates
 * and never travels in a backup:
 *   marker present -> trust the flag (deleting a model later is deliberate)
 *   marker absent  -> require a usable engine before skipping onboarding
 */
@Singleton
class OnboardingGate @Inject constructor(
    @ApplicationContext context: Context,
    private val modelManager: ModelManager,
    private val parakeetManager: ParakeetModelManager,
    private val cloudPrefs: CloudTranscribePrefs,
) {
    private val marker = File(context.noBackupFilesDir, "setup_done")

    suspend fun isSetUp(settings: UserSettings): Boolean {
        if (!settings.onboardingDone) return false
        if (marker.exists()) return true
        // Either an update from a build older than this marker, or a restore.
        // A working engine tells the two apart.
        if (!engineUsable(settings)) return false
        markSetUp()
        return true
    }

    /** Called when onboarding finishes, including when the download is skipped. */
    fun markSetUp() {
        runCatching { marker.createNewFile() }
    }

    private suspend fun engineUsable(settings: UserSettings): Boolean =
        when (settings.selectedModelId) {
            CloudEngine.ID -> cloudPrefs.current().isConfigured
            ParakeetModel.ID -> parakeetManager.isDownloaded()
            else -> modelManager.isDownloaded(settings.model)
        }
}
