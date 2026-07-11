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
import com.vocatim.app.data.cloud.CloudPrompts
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
    @Inject lateinit var quotaStore: com.vocatim.app.data.billing.QuotaStore
    @Inject lateinit var cloudAiPrefs: com.vocatim.app.data.cloud.CloudAiPrefs
    @Inject lateinit var cloudClient: com.vocatim.app.data.cloud.CloudAiClient

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(EXTRA_ID, -1) ?: -1
        if (intent?.action == ACTION_CANCEL) {
            summarizerFactory.cancelActive()
            job?.cancel()
            return START_NOT_STICKY
        }
        if (id <= 0) return START_NOT_STICKY
        val mode = intent?.getStringExtra(EXTRA_MODE) ?: MODE_LOCAL

        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(getString(R.string.summary_notif_running), id),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        acquireWake()
        enqueue(id, mode)
        return START_NOT_STICKY
    }

    private fun acquireWake() {
        if (wakeLock == null) {
            wakeLock = getSystemService<PowerManager>()
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vocatim:summary")
                ?.apply { acquire(30L * 60 * 1000) }
        }
    }

    private fun enqueue(transcriptId: Long, mode: String) {
        // Serialize instead of dropping: a minutes request made while a
        // summary is running must still execute afterwards.
        val previous = job
        job = scope.launch {
            previous?.join()
            try {
                // Server-side of the paywall: the UI gates all AI behind Pro,
                // but the service must not trust the caller.
                if (!quotaStore.currentIsPro()) {
                    notifyFailure(getString(R.string.ai_need_pro))
                    return@launch
                }
                val entity = repository.getById(transcriptId) ?: return@launch
                // "auto" carries no target language for the LLM; fall back to
                // the detected language, then Indonesian (the app's audience).
                // Whisper routinely mis-detects Indonesian as Malay ("ms") —
                // they're mutually intelligible — so normalise ms -> id.
                // Parakeet transcripts are always English regardless of the
                // user's whisper language setting stored on the row.
                val effectiveLanguage = when {
                    entity.modelId == com.vocatim.app.data.model.ParakeetModel.ID -> "en"
                    else -> (
                        entity.language.takeIf { it != "auto" }
                            ?: entity.detectedLanguage
                            ?: "id"
                        ).let { if (it == "ms") "id" else it }
                }

                when (mode) {
                    MODE_CLOUD -> {
                        progressHolder.set(transcriptId, 0.3f)
                        runCloudSummary(entity, effectiveLanguage)
                    }
                    // Minutes are NOT a summary: don't drive the summary
                    // card's progress; the notification carries the status.
                    // Cloud when configured, otherwise the on-device model.
                    MODE_MINUTES ->
                        if (cloudAiPrefs.current().isConfigured) {
                            runCloudMinutes(entity, effectiveLanguage)
                        } else {
                            runLocalMinutes(entity, effectiveLanguage)
                        }
                    else -> {
                        progressHolder.set(transcriptId, 0f)
                        runLocalSummary(entity, effectiveLanguage)
                    }
                }
            } catch (e: Exception) {
                // Surface cloud/provider errors; silence is undebuggable.
                notifyFailure(e.message ?: e.javaClass.simpleName)
            } finally {
                progressHolder.remove(transcriptId)
                if (job?.isActive != true) {
                    wakeLock?.let { if (it.isHeld) it.release() }
                    wakeLock = null
                    ServiceCompat.stopForeground(
                        this@SummaryService, ServiceCompat.STOP_FOREGROUND_REMOVE
                    )
                    stopSelf()
                }
            }
        }
    }

    private fun notifyFailure(message: String) {
        val notification = NotificationCompat.Builder(this, Notifications.CHANNEL_TRANSCRIPTION)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle(getString(R.string.summary_failed_title))
            .setContentText(message.take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.take(400)))
            .setAutoCancel(true)
            .build()
        getSystemService<android.app.NotificationManager>()?.notify(NOTIF_FAIL_ID, notification)
    }

    private suspend fun runLocalSummary(
        entity: com.vocatim.app.data.db.TranscriptEntity,
        language: String,
    ) {
        val settings = userPrefs.current()
        // Step-by-step crash tracing is a debug-build aid only; release
        // does no diagnostic file I/O.
        if (com.vocatim.app.BuildConfig.DEBUG) {
            summarizerFactory.useDiagDir(filesDir)
        }
        val summarizer = summarizerFactory.create(
            ThreadPolicy(this).threadsFor(settings.threads),
            com.vocatim.app.data.summary.SummaryModel.fromId(settings.summaryModel),
        )
        val summary = summarizer.summarize(
            text = entity.text,
            language = language,
            onPartial = { partial -> progressHolder.setPartial(entity.id, partial) },
        ) { fraction ->
            progressHolder.set(entity.id, fraction)
            updateNotification(
                getString(R.string.summary_notif_progress, (fraction * 100).toInt()),
                entity.id,
            )
        }
        repository.updateSummary(entity.id, summary, SUMMARY_SOURCE_LOCAL)
    }

    private suspend fun runCloudSummary(
        entity: com.vocatim.app.data.db.TranscriptEntity,
        language: String,
    ) {
        progressHolder.set(entity.id, 0.3f)
        val summary = cloudClient.chat(
            config = cloudAiPrefs.current(),
            system = CloudPrompts.summarySystem(language),
            user = entity.text.take(CloudPrompts.MAX_INPUT_CHARS),
            maxTokens = 2048,
        )
        repository.updateSummary(entity.id, summary, SUMMARY_SOURCE_CLOUD)
    }

    /** Formats the transcript into tidy meeting minutes, stored on the
     *  transcript itself so everything lives on one detail page. */
    private suspend fun runCloudMinutes(
        entity: com.vocatim.app.data.db.TranscriptEntity,
        language: String,
    ) {
        val settings = userPrefs.current()
        val minutes = cloudClient.chat(
            config = cloudAiPrefs.current(),
            system = CloudPrompts.minutesSystem(
                language, settings.minutesTemplate, settings.customMinutesPrompt
            ),
            user = entity.text.take(CloudPrompts.MAX_INPUT_CHARS),
            maxTokens = 8192,
        )
        repository.updateMinutes(entity.id, minutes)
        notifyMinutesReady(entity.id)
    }

    /** On-device minutes via the local LLM when no cloud key is set up. */
    private suspend fun runLocalMinutes(
        entity: com.vocatim.app.data.db.TranscriptEntity,
        language: String,
    ) {
        val settings = userPrefs.current()
        if (com.vocatim.app.BuildConfig.DEBUG) {
            summarizerFactory.useDiagDir(filesDir)
        }
        val summarizer = summarizerFactory.create(
            ThreadPolicy(this).threadsFor(settings.threads),
            com.vocatim.app.data.summary.SummaryModel.fromId(settings.summaryModel),
        )
        val minutes = summarizer.summarize(
            text = entity.text,
            language = language,
            minutes = true,
        ) { fraction ->
            updateNotification(
                getString(R.string.summary_notif_progress, (fraction * 100).toInt()),
                entity.id,
            )
        }
        repository.updateMinutes(entity.id, minutes)
        notifyMinutesReady(entity.id)
    }

    private fun notifyMinutesReady(noteId: Long) {
        val open = PendingIntent.getActivity(
            this, noteId.toInt(),
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TRANSCRIPT_ID, noteId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, Notifications.CHANNEL_TRANSCRIPTION)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle(getString(R.string.minutes_ready_title))
            .setContentText(getString(R.string.minutes_ready_body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        getSystemService<android.app.NotificationManager>()?.notify(NOTIF_MINUTES_ID, notification)
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
        private const val EXTRA_MODE = "mode"
        private const val ACTION_CANCEL = "com.vocatim.app.summary.CANCEL"
        private const val NOTIF_ID = 3
        private const val NOTIF_MINUTES_ID = 4
        private const val NOTIF_FAIL_ID = 5

        const val MODE_LOCAL = "local"
        const val MODE_CLOUD = "cloud"
        const val MODE_MINUTES = "minutes"

        /** Sentinel [TranscriptEntity.modelId] marking an AI meeting-minutes
         *  note, so the detail screen renders it as minutes, not a transcript. */
        const val MODEL_ID_MINUTES = "minutes"

        /** [TranscriptEntity.summarySource] values. */
        const val SUMMARY_SOURCE_LOCAL = "local"
        const val SUMMARY_SOURCE_CLOUD = "cloud"

        fun start(context: Context, transcriptId: Long, mode: String = MODE_LOCAL) {
            context.startForegroundService(
                Intent(context, SummaryService::class.java)
                    .putExtra(EXTRA_ID, transcriptId)
                    .putExtra(EXTRA_MODE, mode)
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
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val modelManager: com.vocatim.app.data.summary.SummaryModelManager,
) {
    @Volatile private var active: Summarizer? = null

    private var diagFile: java.io.File? = null

    /** Route diagnostics to [dir]/llm_diag.txt so it survives a native crash. */
    fun useDiagDir(dir: java.io.File) {
        diagFile = java.io.File(dir, "llm_diag.txt")
    }

    fun create(
        threads: Int,
        model: com.vocatim.app.data.summary.SummaryModel =
            com.vocatim.app.data.summary.SummaryModel.DEFAULT,
    ): Summarizer = Summarizer(
        modelManager, threads, diagFile, model,
        com.vocatim.app.data.summary.ContextBudget.ramCapTokens(context),
    ).also { active = it }

    fun cancelActive() {
        active?.cancel()
    }
}
