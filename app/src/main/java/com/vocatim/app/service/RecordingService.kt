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
import kotlinx.coroutines.launch
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
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null

    @Volatile private var paused = false
    @Volatile private var stopping = false
    /** The live AudioRecord, held so stop can force a blocked read() out. */
    @Volatile private var activeRecord: AudioRecord? = null
    /** Set by the loop's first successful read; the watchdog checks it. */
    @Volatile private var firstBufferSeen = false
    private var btRouted = false
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

    /** Records from a connected Bluetooth headset mic instead of the phone's. */
    private fun routeToBluetoothIfAvailable() {
        val am = getSystemService<android.media.AudioManager>() ?: return
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                val device = am.availableCommunicationDevices.firstOrNull {
                    it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                }
                if (device != null && am.setCommunicationDevice(device)) {
                    btRouted = true
                }
            } else if (am.isBluetoothScoAvailableOffCall) {
                @Suppress("DEPRECATION") am.startBluetoothSco()
                @Suppress("DEPRECATION") am.isBluetoothScoOn = true
                btRouted = true
            }
        }
    }

    private fun clearBluetoothRouting() {
        if (!btRouted) return
        btRouted = false
        val am = getSystemService<android.media.AudioManager>() ?: return
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                am.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION") am.stopBluetoothSco()
                @Suppress("DEPRECATION") am.isBluetoothScoOn = false
            }
        }
    }

    @SuppressLint("MissingPermission") // UI checks RECORD_AUDIO before starting.
    private fun startRecording() {
        if (recordJob != null) return
        stopping = false
        paused = false
        accumulatedMs = 0
        resumedAt = SystemClock.elapsedRealtime()

        ServiceCompat.startForeground(
            this,
            Notifications.NOTIFICATION_ID_RECORDING,
            buildNotification(paused = false),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
        )

        wakeLock = getSystemService<PowerManager>()
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vocatim:recording")
            ?.apply { acquire(MAX_WAKELOCK_MS) }

        requestAudioFocus()
        routeToBluetoothIfAvailable()

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val record = try {
            AudioRecord(
                // Speech-tuned input path; the OS applies its own filtering.
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuffer * 2, BUFFER_SAMPLES * 4),
            )
        } catch (e: Exception) {
            fail(getString(R.string.record_error_mic))
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            fail(getString(R.string.record_error_mic))
            return
        }
        // Hardware DSP where the device offers it: cleaner input -> better
        // transcription. Effects die with the session; keep refs to release.
        val effects = buildList {
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
            if (android.media.audiofx.AcousticEchoCanceler.isAvailable()) {
                runCatching {
                    android.media.audiofx.AcousticEchoCanceler.create(record.audioSessionId)
                        ?.apply { enabled = true }
                }.getOrNull()?.let(::add)
            }
        }

        val dir = File(filesDir, "recordings").apply { mkdirs() }
        val outFile = File(dir, "rec_${System.currentTimeMillis()}.wav")

        // Warm the whisper model in parallel so transcription starts the
        // moment recording stops instead of paying seconds of load time.
        // Non-whisper engines (Parakeet) load fast enough to skip this.
        scope.launch {
            runCatching {
                com.vocatim.app.data.model.WhisperModel
                    .fromId(userPrefs.current().selectedModelId)
                    ?.let { transcriber.preload(it) }
            }
        }

        activeRecord = record
        firstBufferSeen = false
        // Watchdog: if the mic never delivers a single buffer (held by
        // another app / privacy toggle), read() blocks forever and the whole
        // session looks frozen. Surface it as an error instead.
        scope.launch {
            kotlinx.coroutines.delay(MIC_WATCHDOG_MS)
            if (!firstBufferSeen && recordJob?.isActive == true && !stopping) {
                stopping = true
                runCatching { activeRecord?.stop() }
                stateHolder.set(RecordingState.Error(getString(R.string.record_error_mic_busy)))
            }
        }

        recordJob = scope.launch {
            var writer: WavFileWriter? = null
            try {
                writer = WavFileWriter(outFile)
                record.startRecording()
                val buffer = ShortArray(BUFFER_SAMPLES)
                stateHolder.previewBuffer.clear()
                stateHolder.set(RecordingState.Active(paused = false, elapsedMs = 0, amplitude = 0f))

                while (!stopping) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read <= 0) continue
                    firstBufferSeen = true
                    if (paused) continue

                    writer.write(buffer, read)
                    stateHolder.previewBuffer.write(buffer, read)
                    stateHolder.emitLiveAudio(buffer, read)
                    // RMS, not peak: AGC pins the peak of every speech buffer
                    // near the same level, which made the level bars look flat.
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
                stateHolder.set(
                    RecordingState.Error(e.message ?: getString(R.string.record_error_generic))
                )
            } finally {
                runCatching { record.stop() }
                record.release()
                activeRecord = null
                effects.forEach { runCatching { it.release() } }
                clearBluetoothRouting()
                runCatching { writer?.close() }
            }

            if (stopping && outFile.exists() && (writer?.durationMs ?: 0) > 0) {
                finalizeRecording(outFile, writer!!.durationMs)
            } else {
                outFile.delete()
                stateHolder.set(RecordingState.Idle)
            }
            cleanupAndStop()
        }
    }

    private suspend fun finalizeRecording(file: File, durationMs: Long) {
        try {
            val title = getString(
                R.string.record_default_title,
                SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date()),
            )
            val settings = userPrefs.current()
            // Live sessions open with the captions as an instant draft; the
            // full transcription replaces it as chunks commit.
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
        stateHolder.set(
            RecordingState.Active(paused = false, elapsedMs = accumulatedMs, amplitude = 0f)
        )
        updateNotification(buildNotification(paused = false))
    }

    private fun stopRecording() {
        stopping = true
        // A read() blocked on a silent/held mic never re-checks the flag;
        // stopping the AudioRecord from here forces that read to return, so
        // the Stop button can never be held hostage by the mic.
        runCatching { activeRecord?.stop() }
        if (recordJob == null) cleanupAndStop()
    }

    private fun currentElapsed(): Long =
        if (paused) accumulatedMs
        else accumulatedMs + (SystemClock.elapsedRealtime() - resumedAt)

    private fun requestAudioFocus() {
        // Recording doesn't need focus per se, but holding it means the
        // system tells us (LOSS_TRANSIENT) when a call takes over the mic.
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
        stopping = true
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START = "com.vocatim.app.record.START"
        private const val ACTION_PAUSE = "com.vocatim.app.record.PAUSE"
        private const val ACTION_RESUME = "com.vocatim.app.record.RESUME"
        private const val ACTION_STOP = "com.vocatim.app.record.STOP"

        private const val SAMPLE_RATE = WavDecoder.WHISPER_SAMPLE_RATE
        /** 200ms per buffer: responsive amplitude UI without busy looping. */
        private const val BUFFER_SAMPLES = SAMPLE_RATE / 5
        /** Safety cap; recording longer than 12h is out of scope. */
        private const val MAX_WAKELOCK_MS = 12L * 60 * 60 * 1000

        /** If the mic delivers nothing for this long, report it as busy. */
        private const val MIC_WATCHDOG_MS = 5_000L

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
