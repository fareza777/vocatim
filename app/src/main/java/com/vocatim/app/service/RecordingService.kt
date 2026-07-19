package com.vocatim.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import com.vocatim.app.MainActivity
import com.vocatim.app.R
import com.vocatim.app.data.audio.WavDecoder
import com.vocatim.app.data.audio.WavFileWriter
import com.vocatim.app.data.db.TranscriptEntity
import com.vocatim.app.data.db.TranscriptStatus
import com.vocatim.app.data.prefs.UserPrefs
import com.vocatim.app.data.repository.TranscriptRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Records 16kHz mono PCM16 WAV as a foreground (microphone) service so
 * recording survives screen-off and backgrounding. Auto-pauses on audio
 * focus loss (incoming calls) and does not auto-resume.
 */
@AndroidEntryPoint
class RecordingService : Service() {

    @Inject lateinit var stateHolder: RecordingStateHolder
    @Inject lateinit var repository: TranscriptRepository
    @Inject lateinit var userPrefs: UserPrefs
    @Inject lateinit var transcriber: com.vocatim.app.data.transcribe.WhisperTranscriber

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var recordJob: Job? = null
    private var setupJob: Job? = null
    private var tickJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null

    @Volatile private var paused = false
    @Volatile private var stopping = false
    @Volatile private var activeRecord: AudioRecord? = null
    @Volatile private var lastBufferAtMs = 0L
    /** Bumps on every Start so stale jobs never own the mic/UI. */
    @Volatile private var sessionGen = 0

    private var accumulatedMs = 0L
    private var resumedAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission") // UI checks RECORD_AUDIO before starting.
    private fun startRecording() {
        ServiceCompat.startForeground(
            this,
            Notifications.NOTIFICATION_ID_RECORDING,
            buildNotification(paused = false),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )

        val gen = ++sessionGen
        setupJob?.cancel()
        tickJob?.cancel()
        setupJob = scope.launch {
            // UI feedback immediately — never look "stuck Idle" during teardown.
            stateHolder.set(
                RecordingState.Active(paused = false, elapsedMs = 0, amplitude = 0f)
            )

            awaitPreviousSessionGone()
            if (gen != sessionGen) return@launch

            stopping = false
            paused = false
            accumulatedMs = 0
            resumedAt = SystemClock.elapsedRealtime()
            lastBufferAtMs = SystemClock.elapsedRealtime()

            wakeLock = getSystemService<PowerManager>()
                ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vocatim:recording")
                ?.apply { acquire(MAX_WAKELOCK_MS) }

            requestAudioFocus()
            // Intentionally NO Bluetooth SCO auto-routing. Paired earbuds often
            // steal the input path and leave AudioRecord wedged — a common
            // cause of "timer stuck / record hangs" on real devices.

            val minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val bufferBytes = maxOf(minBuffer * 2, BUFFER_SAMPLES * 4)
            val record = openMic(bufferBytes) ?: run {
                fail(getString(R.string.record_error_mic))
                return@launch
            }
            if (gen != sessionGen) {
                forceReleaseMic(record)
                return@launch
            }

            val effects = attachEffects(record)
            val dir = File(filesDir, "recordings").apply { mkdirs() }
            val outFile = File(dir, "rec_${System.currentTimeMillis()}.wav")

            scope.launch {
                runCatching {
                    com.vocatim.app.data.model.WhisperModel
                        .fromId(userPrefs.current().selectedModelId)
                        ?.let { transcriber.preload(it) }
                }
            }

            activeRecord = record
            startTicker(gen)

            val watchdog = scope.launch {
                while (gen == sessionGen && activeRecord === record && !stopping) {
                    kotlinx.coroutines.delay(WATCHDOG_TICK_MS)
                    if (gen == sessionGen && activeRecord === record && !stopping && !paused &&
                        SystemClock.elapsedRealtime() - lastBufferAtMs > MIC_STALL_MS
                    ) {
                        stopping = true
                        forceReleaseMic(record)
                        stateHolder.set(
                            RecordingState.Error(getString(R.string.record_error_mic_busy))
                        )
                        break
                    }
                }
            }

            recordJob = scope.launch {
                var writer: WavFileWriter? = null
                try {
                    writer = WavFileWriter(outFile)
                    record.startRecording()
                    if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                        throw IllegalStateException(getString(R.string.record_error_mic))
                    }
                    val buffer = ShortArray(BUFFER_SAMPLES)
                    stateHolder.previewBuffer.clear()
                    resumedAt = SystemClock.elapsedRealtime()
                    stateHolder.set(
                        RecordingState.Active(paused = false, elapsedMs = 0, amplitude = 0f)
                    )

                    while (!stopping && gen == sessionGen) {
                        val read = record.read(buffer, 0, buffer.size)
                        if (read <= 0) {
                            kotlinx.coroutines.delay(20)
                            continue
                        }
                        lastBufferAtMs = SystemClock.elapsedRealtime()
                        if (paused) continue

                        writer.write(buffer, read)
                        stateHolder.previewBuffer.write(buffer, read)
                        stateHolder.emitLiveAudio(buffer, read)
                        var sumSq = 0.0
                        for (i in 0 until read) {
                            val s = buffer[i].toDouble()
                            sumSq += s * s
                        }
                        val rms = kotlin.math.sqrt(sumSq / read) / 32768.0
                        stateHolder.set(
                            RecordingState.Active(
                                paused = false,
                                elapsedMs = currentElapsed(),
                                amplitude = rms.toFloat(),
                            )
                        )
                    }
                } catch (e: Exception) {
                    if (gen == sessionGen) {
                        stateHolder.set(
                            RecordingState.Error(
                                e.message ?: getString(R.string.record_error_generic)
                            )
                        )
                    }
                } finally {
                    tickJob?.cancel()
                    watchdog.cancel()
                    forceReleaseMic(record)
                    if (activeRecord === record) activeRecord = null
                    effects.forEach { runCatching { it.release() } }
                    runCatching { writer?.close() }
                }

                // isActive=false => torn down for a newer Start; don't stopSelf.
                if (isActive && gen == sessionGen) {
                    if (stopping && outFile.exists() && (writer?.durationMs ?: 0) > 0) {
                        finalizeRecording(outFile, writer!!.durationMs)
                    } else {
                        outFile.delete()
                        if (stateHolder.state.value !is RecordingState.Error) {
                            stateHolder.set(RecordingState.Idle)
                        }
                    }
                    cleanupAndStop()
                } else {
                    outFile.delete()
                }
            }
        }
    }

    /**
     * MIC first — most reliable for a working timer + WAV capture.
     * VOICE_RECOGNITION is nicer for speech but broken on some OEMs.
     */
    @SuppressLint("MissingPermission")
    private fun openMic(bufferBytes: Int): AudioRecord? {
        val sources = intArrayOf(
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.CAMCORDER,
            MediaRecorder.AudioSource.DEFAULT,
        )
        for (source in sources) {
            val record = try {
                AudioRecord(
                    source,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferBytes,
                )
            } catch (_: Exception) {
                null
            } ?: continue
            if (record.state == AudioRecord.STATE_INITIALIZED) return record
            runCatching { record.release() }
        }
        return null
    }

    private fun attachEffects(record: AudioRecord): List<android.media.audiofx.AudioEffect> =
        buildList {
            if (android.media.audiofx.NoiseSuppressor.isAvailable()) {
                runCatching {
                    android.media.audiofx.NoiseSuppressor.create(record.audioSessionId)
                        ?.apply { enabled = true }
                }.getOrNull()?.let(::add)
            }
            if (android.media.audiofx.AutomaticGainControl.isAvailable()) {
                runCatching {
                    android.media.audiofx.AutomaticGainControl.create(record.audioSessionId)
                        ?.apply { enabled = true }
                }.getOrNull()?.let(::add)
            }
        }

    private fun forceReleaseMic(record: AudioRecord?) {
        if (record == null) return
        runCatching { record.stop() }
        runCatching { record.release() }
    }

    private suspend fun awaitPreviousSessionGone() {
        stopping = true
        forceReleaseMic(activeRecord)
        activeRecord = null
        val joined = withTimeoutOrNull(TEARDOWN_TIMEOUT_MS) {
            recordJob?.cancelAndJoin()
        }
        if (joined == null) recordJob?.cancel()
        recordJob = null
        tickJob?.cancel()
        tickJob = null
    }

    /** Wall-clock UI timer — advances even if a mic buffer stalls. */
    private fun startTicker(gen: Int) {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (gen == sessionGen && isActive) {
                kotlinx.coroutines.delay(TICK_UI_MS)
                if (gen != sessionGen || stopping) break
                val current = stateHolder.state.value
                if (current is RecordingState.Active && !current.paused) {
                    stateHolder.set(current.copy(elapsedMs = currentElapsed()))
                }
            }
        }
    }

    private suspend fun finalizeRecording(file: File, durationMs: Long) {
        try {
            val title = getString(
                R.string.record_default_title,
                SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date()),
            )
            val settings = userPrefs.current()
            val draft = stateHolder.liveDraft.orEmpty()
            stateHolder.liveDraft = null
            val id = repository.create(
                TranscriptEntity(
                    title = title,
                    text = draft,
                    language = settings.language,
                    modelId = settings.selectedModelId,
                    audioDurationMs = durationMs,
                    audioPath = file.absolutePath,
                    status = TranscriptStatus.PENDING,
                    translate = settings.translate,
                )
            )
            TranscriptionService.enqueue(this, id, sourceUri = null)
            stateHolder.set(RecordingState.Idle)
            stateHolder.emitFinished(id)
        } catch (e: Exception) {
            stateHolder.set(
                RecordingState.Error(e.message ?: getString(R.string.record_error_generic))
            )
        }
    }

    private fun pause() {
        if (recordJob == null || paused) return
        paused = true
        accumulatedMs += SystemClock.elapsedRealtime() - resumedAt
        stateHolder.set(
            RecordingState.Active(paused = true, elapsedMs = accumulatedMs, amplitude = 0f)
        )
        updateNotification(buildNotification(paused = true))
    }

    private fun resume() {
        if (recordJob == null || !paused) return
        paused = false
        resumedAt = SystemClock.elapsedRealtime()
        lastBufferAtMs = SystemClock.elapsedRealtime()
        stateHolder.set(
            RecordingState.Active(paused = false, elapsedMs = accumulatedMs, amplitude = 0f)
        )
        updateNotification(buildNotification(paused = false))
    }

    private fun stopRecording() {
        stopping = true
        forceReleaseMic(activeRecord)
        if (recordJob == null && setupJob?.isActive != true) cleanupAndStop()
    }

    private fun currentElapsed(): Long =
        if (paused) accumulatedMs
        else accumulatedMs + (SystemClock.elapsedRealtime() - resumedAt)

    private fun requestAudioFocus() {
        val audioManager = getSystemService<AudioManager>() ?: return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setOnAudioFocusChangeListener { change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
                    -> pause()
                }
            }
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
    }

    private fun fail(message: String) {
        stateHolder.set(RecordingState.Error(message))
        cleanupAndStop()
    }

    private fun cleanupAndStop() {
        focusRequest?.let { getSystemService<AudioManager>()?.abandonAudioFocusRequest(it) }
        focusRequest = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        recordJob = null
        tickJob?.cancel()
        tickJob = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(paused: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val toggleIntent = PendingIntent.getService(
            this, 2,
            Intent(this, RecordingService::class.java)
                .setAction(if (paused) ACTION_RESUME else ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_RECORDING)
            .setSmallIcon(R.drawable.ic_stat_mic)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(
                getString(
                    if (paused) R.string.notif_recording_paused else R.string.notif_recording
                )
            )
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(
                0,
                getString(if (paused) R.string.record_resume else R.string.record_pause),
                toggleIntent,
            )
            .addAction(0, getString(R.string.record_stop), stopIntent)
            .build()
    }

    private fun updateNotification(notification: Notification) {
        getSystemService<android.app.NotificationManager>()
            ?.notify(Notifications.NOTIFICATION_ID_RECORDING, notification)
    }

    override fun onDestroy() {
        sessionGen++
        stopping = true
        forceReleaseMic(activeRecord)
        activeRecord = null
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "com.vocatim.app.record.START"
        private const val ACTION_PAUSE = "com.vocatim.app.record.PAUSE"
        private const val ACTION_RESUME = "com.vocatim.app.record.RESUME"
        private const val ACTION_STOP = "com.vocatim.app.record.STOP"

        private const val SAMPLE_RATE = WavDecoder.WHISPER_SAMPLE_RATE
        private const val BUFFER_SAMPLES = SAMPLE_RATE / 5
        private const val MAX_WAKELOCK_MS = 12L * 60 * 60 * 1000
        private const val MIC_STALL_MS = 6_000L
        private const val WATCHDOG_TICK_MS = 2_000L
        private const val TEARDOWN_TIMEOUT_MS = 2_000L
        private const val TICK_UI_MS = 200L

        fun start(context: Context) = command(context, ACTION_START)
        fun pause(context: Context) = command(context, ACTION_PAUSE)
        fun resume(context: Context) = command(context, ACTION_RESUME)
        fun stop(context: Context) = command(context, ACTION_STOP)

        private fun command(context: Context, action: String) {
            context.startForegroundService(
                Intent(context, RecordingService::class.java).setAction(action)
            )
        }
    }
}
