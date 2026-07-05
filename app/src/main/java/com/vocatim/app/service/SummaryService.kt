package com.vocatim.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import com.vocatim.app.MainActivity
import com.vocatim.app.R
import com.vocatim.app.data.prefs.UserPrefs
import com.vocatim.app.data.repository.TranscriptRepository
import com.vocatim.app.data.summary.SummaryProgressHolder
import com.vocatim.app.data.summary.Summarizer
import com.vocatim.app.data.transcribe.ThreadPolicy
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Runs one AI-summary job as a foreground (dataSync) service so it survives
 * backgrounding. LLM inference is CPU-heavy and minutes-long; a partial
 * wakelock keeps it alive.
 */
@AndroidEntryPoint
class SummaryService : Service() {

    @Inject lateinit var summarizerFactory: SummarizerFactory
    @Inject lateinit var repository: TranscriptRepository
    @Inject lateinit var progressHolder: SummaryProgressHolder
    @Inject lateinit var userPrefs: UserPrefs

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(EXTRA_ID, -1) ?: -1
        if (intent?.action == ACTION_CANCEL) {
            summarizerFactory.cancelActive()
            return START_NOT_STICKY
        }
        if (id <= 0) return START_NOT_STICKY

        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(getString(R.string.summary_notif_running), id),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        acquireWake()
        enqueue(id)
        return START_NOT_STICKY
    }

    private fun acquireWake() {
        if (wakeLock == null) {
            wakeLock = getSystemService<PowerManager>()
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vocatim:summary")
                ?.apply { acquire(30L * 60 * 1000) }
        }
    }

    private fun enqueue(transcriptId: Long) {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                val entity = repository.getById(transcriptId) ?: return@launch
                val settings = userPrefs.current()
                summarizerFactory.useDiagDir(filesDir)
                val summarizer = summarizerFactory.create(
                    ThreadPolicy(this@SummaryService).threadsFor(settings.threads)
                )
                progressHolder.set(transcriptId, 0f)
                // "auto" carries no target language for the LLM; fall back to
                // the detected language, then Indonesian (the app's audience).
                // Whisper routinely mis-detects Indonesian as Malay ("ms") —
                // they're mutually intelligible — so normalise ms -> id.
                val effectiveLanguage = (
                    entity.language.takeIf { it != "auto" }
                        ?: entity.detectedLanguage
                        ?: "id"
                    ).let { if (it == "ms") "id" else it }
                val summary = summarizer.summarize(
                    text = entity.text,
                    language = effectiveLanguage,
                ) { fraction ->
                    progressHolder.set(transcriptId, fraction)
                    updateNotification(
                        getString(R.string.summary_notif_progress, (fraction * 100).toInt()),
                        transcriptId,
                    )
                }
                repository.updateSummary(transcriptId, summary)
            } catch (e: Exception) {
                // Leave summary null; the UI offers retry.
            } finally {
                progressHolder.remove(transcriptId)
                wakeLock?.let { if (it.isHeld) it.release() }
                wakeLock = null
                ServiceCompat.stopForeground(this@SummaryService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun buildNotification(text: String, id: Long): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TRANSCRIPT_ID, id),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val cancel = PendingIntent.getService(
            this, 1,
            Intent(this, SummaryService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_TRANSCRIPTION)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle(getString(R.string.summary_notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.action_cancel), cancel)
            .build()
    }

    private fun updateNotification(text: String, id: Long) {
        getSystemService<android.app.NotificationManager>()
            ?.notify(NOTIF_ID, buildNotification(text, id))
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ID = "transcript_id"
        private const val ACTION_CANCEL = "com.vocatim.app.summary.CANCEL"
        private const val NOTIF_ID = 3

        fun start(context: Context, transcriptId: Long) {
            context.startForegroundService(
                Intent(context, SummaryService::class.java).putExtra(EXTRA_ID, transcriptId)
            )
        }

        fun cancel(context: Context) {
            context.startService(
                Intent(context, SummaryService::class.java).setAction(ACTION_CANCEL)
            )
        }
    }
}

/** Hilt-provided factory so the service can build a Summarizer with a
 *  runtime thread count and expose cancellation of the active engine. */
class SummarizerFactory @Inject constructor(
    private val modelManager: com.vocatim.app.data.summary.SummaryModelManager,
) {
    @Volatile private var active: Summarizer? = null

    private var diagFile: java.io.File? = null

    /** Route diagnostics to [dir]/llm_diag.txt so it survives a native crash. */
    fun useDiagDir(dir: java.io.File) {
        diagFile = java.io.File(dir, "llm_diag.txt")
    }

    fun create(threads: Int): Summarizer =
        Summarizer(modelManager, threads, diagFile).also { active = it }

    fun cancelActive() {
        active?.cancel()
    }
}
