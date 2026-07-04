package com.vocatim.app.service

import com.vocatim.app.data.audio.WavDecoder
import com.vocatim.app.data.audio.WavStreamReader
import com.vocatim.app.data.model.ModelManager
import com.vocatim.app.data.model.WhisperModel
import com.vocatim.app.data.prefs.UserPrefs
import com.vocatim.app.data.transcribe.WhisperTranscriber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Periodically transcribes the tail of an in-progress recording WAV so the
 * record screen can show a live preview. Uses the smallest downloaded model.
 */
@Singleton
class LiveTranscriptionRunner @Inject constructor(
    private val transcriber: WhisperTranscriber,
    private val modelManager: ModelManager,
    private val userPrefs: UserPrefs,
    private val stateHolder: RecordingStateHolder,
) {
    private var job: Job? = null
    private val partialBuilder = StringBuilder()

    fun start(scope: CoroutineScope, wavFile: File, isPaused: () -> Boolean) {
        stop()
        partialBuilder.clear()
        job = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                if (isPaused()) continue
                runCatching { transcribeTail(wavFile) }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        partialBuilder.clear()
    }

    private suspend fun transcribeTail(file: File) {
        if (!file.exists() || file.length() < 44) return
        val model = pickModel() ?: return
        val settings = userPrefs.current()
        val reader = WavStreamReader(file)
        reader.use {
            val total = it.totalSamples
            val minSamples = MIN_CHUNK_SEC * WavDecoder.WHISPER_SAMPLE_RATE
            if (total < minSamples) return
            val chunkSamples = minOf(
                TAIL_SEC.toLong() * WavDecoder.WHISPER_SAMPLE_RATE,
                total,
            ).toInt()
            val start = total - chunkSamples
            val samples = it.read(start, chunkSamples)
            if (samples.isEmpty()) return
            val result = transcriber.transcribe(
                model = model,
                samples = samples,
                language = settings.language,
                translate = settings.translate,
                numThreads = 2,
            )
            val snippet = result.text.trim()
            if (snippet.isBlank()) return
            if (partialBuilder.isNotEmpty()) partialBuilder.append(' ')
            partialBuilder.append(snippet)
            pushPartial(partialBuilder.toString())
        }
    }

    private fun pickModel(): WhisperModel? {
        val preferred = listOf(WhisperModel.TINY, WhisperModel.BASE, WhisperModel.SMALL_Q5, WhisperModel.SMALL)
        return preferred.firstOrNull { modelManager.isDownloaded(it) }
    }

    private fun pushPartial(text: String) {
        val current = stateHolder.state.value
        if (current is RecordingState.Active) {
            stateHolder.set(current.copy(partialText = text))
        }
    }

    private companion object {
        const val TICK_MS = 18_000L
        const val MIN_CHUNK_SEC = 5
        const val TAIL_SEC = 20
    }
}
