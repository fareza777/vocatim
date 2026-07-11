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
import com.vocatim.app.data.audio.AudioImporter
import com.vocatim.app.data.audio.WavStreamReader
import com.vocatim.app.data.repository.TranscriptRepository
import com.vocatim.app.data.transcribe.DiarizationProgressHolder
import com.vocatim.app.data.transcribe.SpeakerDiarizer
import com.vocatim.app.data.transcribe.SpeakerTurn
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Runs one speaker-detection job as a foreground (dataSync) service: loads
 * the audio, diarizes it, and writes speaker indices onto the transcript's
 * segments. CPU-heavy and minutes-long on long audio, hence the wakelock.
 */
@AndroidEntryPoint
class DiarizationService : Service() {

    @Inject lateinit var repository: TranscriptRepository
    @Inject lateinit var diarizer: SpeakerDiarizer
    @Inject lateinit var progressHolder: DiarizationProgressHolder
    @Inject lateinit var importer: AudioImporter
    @Inject lateinit var quotaStore: com.vocatim.app.data.billing.QuotaStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getLongExtra(EXTRA_ID, -1) ?: -1
        if (id <= 0) return START_NOT_STICKY

        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            buildNotification(getString(R.string.diarize_notif_running), id),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (wakeLock == null) {
            wakeLock = getSystemService<PowerManager>()
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vocatim:diarize")
                ?.apply { acquire(30L * 60 * 1000) }
        }
        val previous = job
        job = scope.launch {
            previous?.join()
            try {
                run(id)
            } catch (e: Throwable) {
                // Throwable, not Exception: an OutOfMemoryError on a long
                // file or a linkage error must fail the job, not the app.
                notifyFailure(e.message ?: e.javaClass.simpleName)
            } finally {
                progressHolder.remove(id)
                if (job?.isActive != true) {
                    wakeLock?.let { if (it.isHeld) it.release() }
                    wakeLock = null
                    ServiceCompat.stopForeground(
                        this@DiarizationService, ServiceCompat.STOP_FOREGROUND_REMOVE
                    )
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private suspend fun run(transcriptId: Long) {
        // Server-side of the paywall, like every other AI feature.
        if (!quotaStore.currentIsPro()) {
            notifyFailure(getString(R.string.ai_need_pro))
            return
        }
        val entity = repository.getById(transcriptId) ?: return
        val audioPath = entity.audioPath
            ?: run { notifyFailure(getString(R.string.diarize_no_audio)); return }
        if (entity.audioDurationMs > MAX_DURATION_MS) {
            notifyFailure(getString(R.string.diarize_too_long))
            return
        }

        progressHolder.set(transcriptId, 0.02f)
        val samples = loadSamples(File(audioPath))
            ?: run { notifyFailure(getString(R.string.diarize_no_audio)); return }

        // No granular progress from the engine; the UI shows an
        // indeterminate bar while any value is present.
        progressHolder.set(transcriptId, 0.5f)
        val turns = diarizer.diarize(samples)
        if (turns.isEmpty()) {
            notifyFailure(getString(R.string.diarize_none_found))
            return
        }

        val segments = repository.getSegments(transcriptId)
        val assignments = mutableMapOf<Long, Int>()
        for (segment in segments) {
            speakerFor(segment.startMs, segment.endMs, turns)?.let { spk ->
                assignments[segment.id] = spk
            }
        }
        repository.setSegmentSpeakers(assignments)
        progressHolder.set(transcriptId, 1f)

        val speakerCount = turns.maxOf { it.speaker }
        notifyDone(transcriptId, speakerCount)
    }

    /** The whole file as 16kHz mono floats; compressed audio is decoded via
     *  the import pipeline into a temp WAV first. */
    private suspend fun loadSamples(file: File): FloatArray? =
        withContext(Dispatchers.IO) {
            runCatching {
                if (file.extension.equals("wav", ignoreCase = true)) {
                    readWav(file)
                } else {
                    val tmp = File(cacheDir, "diarize_${System.currentTimeMillis()}.wav")
                    try {
                        importer.import(Uri.fromFile(file), tmp) { }
                        readWav(tmp)
                    } finally {
                        tmp.delete()
                    }
                }
            }.getOrNull()
        }

    /** Chunked read into one preallocated array: peak memory stays at the
     *  4 bytes/sample of the result instead of tripling via byte buffers. */
    private fun readWav(file: File): FloatArray =
        WavStreamReader(file).use { reader ->
            val total = reader.totalSamples.toInt()
            val out = FloatArray(total)
            var offset = 0
            while (offset < total) {
                val chunk = reader.read(offset.toLong(), minOf(READ_CHUNK, total - offset))
                if (chunk.isEmpty()) break
                chunk.copyInto(out, offset)
                offset += chunk.size
            }
            out
        }

    /** Turn with the largest overlap against [startMs, endMs]. */
    private fun speakerFor(startMs: Long, endMs: Long, turns: List<SpeakerTurn>): Int? =
        turns
            .map { it.speaker to (minOf(endMs, it.endMs) - maxOf(startMs, it.startMs)) }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first

    private fun notifyDone(noteId: Long, speakers: Int) {
        val open = PendingIntent.getActivity(
            this, noteId.toInt(),
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TRANSCRIPT_ID, noteId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, Notifications.CHANNEL_TRANSCRIPTION)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle(getString(R.string.diarize_done_title))
            .setContentText(getString(R.string.diarize_done_body, speakers))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        getSystemService<android.app.NotificationManager>()?.notify(NOTIF_DONE_ID, notification)
    }

    private fun notifyFailure(message: String) {
        val notification = NotificationCompat.Builder(this, Notifications.CHANNEL_TRANSCRIPTION)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle(getString(R.string.diarize_failed_title))
            .setContentText(message.take(120))
            .setAutoCancel(true)
            .build()
        getSystemService<android.app.NotificationManager>()?.notify(NOTIF_FAIL_ID, notification)
    }

    private fun buildNotification(text: String, id: Long): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TRANSCRIPT_ID, id),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_TRANSCRIPTION)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle(getString(R.string.diarize_notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ID = "transcript_id"
        private const val NOTIF_ID = 6
        private const val NOTIF_DONE_ID = 7
        private const val NOTIF_FAIL_ID = 8

        /** ~230 MB of float samples at 16kHz; longer risks an OOM kill. */
        const val MAX_DURATION_MS = 60L * 60 * 1000

        /** 30s of samples per read step. */
        private const val READ_CHUNK = 30 * 16_000

        fun start(context: Context, transcriptId: Long) {
            context.startForegroundService(
                Intent(context, DiarizationService::class.java)
                    .putExtra(EXTRA_ID, transcriptId)
            )
        }
    }
}
