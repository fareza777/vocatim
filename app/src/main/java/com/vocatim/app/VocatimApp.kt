package com.vocatim.app

import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.StrictMode
import com.vocatim.app.data.repository.StartupRecovery
import com.vocatim.app.data.repository.TranscriptRepository
import com.vocatim.app.service.Notifications
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class VocatimApp : android.app.Application() {

    @Inject lateinit var startupRecovery: StartupRecovery
    @Inject lateinit var transcriptRepository: TranscriptRepository
    @Inject lateinit var autoBackup: com.vocatim.app.data.backup.AutoBackup

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Foreground services (recording/transcription) post to these
        // channels; without them startForeground throws on API 26+.
        Notifications.createChannels(this)
        appScope.launch {
            runCatching { startupRecovery.recover() }
            runCatching { autoBackup.runIfDue() }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                runCatching { publishShortcuts() }
            }
        }
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build()
            )
        }
    }

    private suspend fun publishShortcuts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val manager = getSystemService(ShortcutManager::class.java) ?: return
        val record = ShortcutInfo.Builder(this, "record")
            .setShortLabel(getString(R.string.shortcut_record))
            .setLongLabel(getString(R.string.shortcut_record_long))
            .setIcon(Icon.createWithResource(this, R.drawable.ic_stat_mic))
            .setIntent(
                Intent(this, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_START_RECORD)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            )
            .build()
        val latest = transcriptRepository.observeAll().first().firstOrNull()
        val shortcuts = mutableListOf(record)
        if (latest != null) {
            shortcuts += ShortcutInfo.Builder(this, "latest_${latest.id}")
                .setShortLabel(latest.title.take(24))
                .setLongLabel(getString(R.string.shortcut_latest, latest.title))
                .setIcon(Icon.createWithResource(this, R.drawable.ic_stat_mic))
                .setIntent(
                    Intent(this, MainActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .putExtra(MainActivity.EXTRA_TRANSCRIPT_ID, latest.id)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
                .build()
        }
        manager.dynamicShortcuts = shortcuts
    }
}
