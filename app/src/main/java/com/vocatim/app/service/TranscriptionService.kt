package com.vocatim.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import com.vocatim.app.MainActivity
import com.vocatim.app.R
import com.vocatim.app.data.transcribe.TranscriptionProgressHolder
import com.vocatim.app.data.transcribe.TranscriptionRunner
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import javax.inject.Inject

/**
 * Processes queued transcription jobs one at a time as a foreground
 * (dataSync) service, so long jobs survive backgrounding. Holds a partial
 * wakelock: whisper inference stops if the CPU sleeps.
 */
@AndroidEntryPoint
class TranscriptionService : Service() {

    @Inject lateinit var runner: TranscriptionRunner
    @Inject lateinit var progressHolder: TranscriptionProgressHolder
    @Inject lateinit var userPrefs: com.vocatim.app.data.prefs.UserPrefs
    @Inject lateinit var repository: com.vocatim.app.data.repository.TranscriptRepository
    @Inject lateinit var quotaStore: com.vocatim.app.data.billing.QuotaStore
    @Inject lateinit var cloudAiPrefs: com.vocatim.app.data.cloud.CloudAiPrefs
    @Inject lateinit var summaryModelManager: com.vocatim.app.data.summary.SummaryModelManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val queue = ConcurrentLinkedQueue<JobSpec>()
    private var workerJob: Job? = null
    private var notifierJob: Job? = null
    private var currentJob: Job? = null
    @Volatile private var currentTranscriptId: Long = -1
    private var wakeLock: PowerManager.WakeLock? = null

    private data class JobSpec(val transcriptId: Long, val sourceUri: Uri?)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(EXTRA_TRANSCRIPT_ID, -1) ?: -1
        when (intent?.action) {
            ACTION_CANCEL -> if (id > 0) cancelJob(id)
            else -> if (id > 0) {
                // Source URI travels in intent.data so the read grant from the
                // sharing app propagates to this service.
                queue.add(JobSpec(id, intent?.data))
                ensureForeground()
                ensureWorker()
            }
        }
        return START_NOT_STICKY
    }

    private fun cancelJob(transcriptId: Long) {
        queue.removeIf { it.transcriptId == transcriptId }
        if (currentTranscriptId == transcriptId) {
            currentJob?.cancel()
        } else {
            // Queued but not started: mark cancelled so retry is offered.
            scope.launch {
                runner.markCancelled(transcriptId)
            }
        }
    }

    private fun ensureForeground() {
        ServiceCompat.startForeground(
            this,
            Notifications.NOTIFICATION_ID_TRANSCRIPTION,
            buildNotification(getString(R.string.notif_transcribing_starting)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (wakeLock == null) {
            wakeLock = getSystemService<PowerManager>()
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vocatim:transcription")
                ?.apply { acquire(MAX_WAKELOCK_MS) }
        }
    }

    private fun ensureWorker() {
        if (workerJob?.isActive == true) return
        workerJob = scope.launch {
            while (true) {
                val job = queue.poll() ?: break
                startNotifier(job.transcriptId)
                currentTranscriptId = job.transcriptId
                // Child job so a per-transcript cancel doesn't kill the queue.
                var succeeded = false
                val child = launch {
                    try {
                        runner.run(job.transcriptId, job.sourceUri)
                        succeeded = true
                    } catch (e: Exception) {
                        // Failure state is persisted by the runner; keep draining.
                    }
                }
                currentJob = child
                child.join()
                currentJob = null
                currentTranscriptId = -1
                stopNotifier()
                if (succeeded) {
                    maybeCompressAudio(job.transcriptId)
                    maybeAutoSummarize(job.transcriptId)
                }
            }
            wakeLock?.let { if (it.isHeld) it.release() }
            wakeLock = null
            ServiceCompat.stopForeground(this@TranscriptionService, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    /** Mirrors progress of the active job into the foreground notification. */
    private fun startNotifier(transcriptId: Long) {
        notifierJob = scope.launch {
            var lastUpdate = 0L
            progressHolder.progress.collect { map ->
                val p = map[transcriptId] ?: return@collect
                val now = System.currentTimeMillis()
                if (now - lastUpdate < NOTIFICATION_THROTTLE_MS) return@collect
                lastUpdate = now

                val text = if (p.converting) {
                    getString(R.string.notif_converting, (p.fraction * 100).toInt())
                } else if (p.totalChunks > 0) {
                    val eta = p.etaMs?.let { formatEta(it) } ?: "…"
                    getString(
                        R.string.notif_transcribing_progress,
                        (p.fraction * 100).toInt(),
                        eta,
                    )
                } else {
                    getString(R.string.notif_transcribing_starting)
                }
                getSystemService<android.app.NotificationManager>()?.notify(
                    Notifications.NOTIFICATION_ID_TRANSCRIPTION,
                    buildNotification(
                        text,
                        progressPercent = (p.fraction * 100).toInt(),
                        // Long job ahead: suggest plugging the phone in.
                        chargeHint = (p.etaMs ?: 0) > CHARGE_HINT_ETA_MS,
                        cancelId = transcriptId,
                    ),
                )
            }
        }
    }

    private fun stopNotifier() {
        notifierJob?.cancel()
        notifierJob = null
    }

    /** Optionally shrink the finished WAV to M4A (~85% smaller). */
    private suspend fun maybeCompressAudio(transcriptId: Long) {
        if (!userPrefs.current().compressAudio) return
        val entity = repository.getById(transcriptId) ?: return
        val wavPath = entity.audioPath ?: return
        if (!wavPath.endsWith(".wav")) return
        val wav = java.io.File(wavPath)
        if (!wav.exists()) return
        val m4a = java.io.File(wavPath.removeSuffix(".wav") + ".m4a")
        val ok = kotlinx.coroutines.withContext(Dispatchers.IO) {
            com.vocatim.app.data.audio.AudioCompressor.compressWavToM4a(wav, m4a)
        }
        if (ok) {
            repository.update(entity.copy(audioPath = m4a.absolutePath))
            wav.delete()
        }
    }

    /** Optionally kick off an AI summary once a transcript finishes (Pro). */
    private suspend fun maybeAutoSummarize(transcriptId: Long) {
        val settings = userPrefs.current()
        if (!settings.autoSummarize) return
        if (!quotaStore.currentIsPro()) return
        val localModel = com.vocatim.app.data.summary.SummaryModel.fromId(settings.summaryModel)
        val mode = when {
            cloudAiPrefs.config.first().isConfigured -> SummaryService.MODE_CLOUD
            summaryModelManager.state(localModel).value
                is com.vocatim.app.data.model.ModelState.Downloaded ->
                SummaryService.MODE_LOCAL
            else -> return
        }
        SummaryService.start(this, transcriptId, mode)
    }

    private fun formatEta(ms: Long): String {
        val totalMinutes = ms / 60_000
        return if (totalMinutes < 1) getString(R.string.eta_under_minute)
        else getString(R.string.eta_minutes, totalMinutes)
    }

    private fun buildNotification(
        text: String,
        progressPercent: Int? = null,
        chargeHint: Boolean = false,
        cancelId: Long? = null,
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_TRANSCRIPTION)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle(getString(R.string.notif_transcription_title))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .apply {
                if (progressPercent != null) setProgress(100, progressPercent, false)
                else setProgress(0, 0, true)
                if (chargeHint) setSubText(getString(R.string.notif_charge_hint))
                if (cancelId != null) {
                    val cancelIntent = PendingIntent.getService(
                        this@TranscriptionService,
                        cancelId.toInt(),
                        Intent(this@TranscriptionService, TranscriptionService::class.java)
                            .setAction(ACTION_CANCEL)
                            .putExtra(EXTRA_TRANSCRIPT_ID, cancelId),
                        PendingIntent.FLAG_IMMUTABLE,
                    )
                    addAction(0, getString(R.string.action_cancel), cancelIntent)
                }
            }
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_TRANSCRIPT_ID = "transcript_id"
        private const val ACTION_CANCEL = "com.vocatim.app.transcribe.CANCEL"
        private const val NOTIFICATION_THROTTLE_MS = 1_000L
        private const val CHARGE_HINT_ETA_MS = 30L * 60 * 1000
        /** 12h cap: covers 2-3h audio on slow devices with margin. */
        private const val MAX_WAKELOCK_MS = 12L * 60 * 60 * 1000

        fun enqueue(context: Context, transcriptId: Long, sourceUri: Uri?) {
            val intent = Intent(context, TranscriptionService::class.java)
                .putExtra(EXTRA_TRANSCRIPT_ID, transcriptId)
            if (sourceUri != null) {
                intent.data = sourceUri
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startForegroundService(intent)
        }

        fun cancel(context: Context, transcriptId: Long) {
            context.startService(
                Intent(context, TranscriptionService::class.java)
                    .setAction(ACTION_CANCEL)
                    .putExtra(EXTRA_TRANSCRIPT_ID, transcriptId)
            )
        }
    }
}
