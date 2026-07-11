package com.vocatim.app.data.transcribe

import android.net.Uri
import android.os.SystemClock
import com.vocatim.app.data.audio.AudioImporter
import com.vocatim.app.data.audio.WavDecoder
import com.vocatim.app.data.audio.WavStreamReader
import com.vocatim.app.data.db.SegmentEntity
import com.vocatim.app.data.db.TranscriptStatus
import com.vocatim.app.data.model.WhisperModel
import com.vocatim.app.data.prefs.RtfStore
import com.vocatim.app.data.prefs.UserPrefs
import com.vocatim.app.data.repository.TranscriptRepository
import com.vocatim.whisper.WhisperSegment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

/**
 * Runs one transcription job end to end: optional import conversion,
 * chunked whisper inference with a per-chunk checkpoint (resume after
 * kill continues from the last committed chunk), progress reporting,
 * and realtime-factor bookkeeping.
 */
class QuotaExceededException : Exception("QUOTA_EXCEEDED")

class TranscriptionRunner(
    private val repository: TranscriptRepository,
    private val transcriber: WhisperTranscriber,
    private val parakeetTranscriber: ParakeetTranscriber,
    private val importer: AudioImporter,
    private val rtfStore: RtfStore,
    private val progressHolder: TranscriptionProgressHolder,
    private val userPrefs: UserPrefs,
    private val threadPolicy: ThreadPolicy,
    private val quotaStore: com.vocatim.app.data.billing.QuotaStore,
    private val importDir: File,
    private val modelsDir: File,
    private val httpClient: okhttp3.OkHttpClient,
) {

    /** @param sourceUri set for imported audio that still needs conversion. */
    suspend fun run(transcriptId: Long, sourceUri: Uri?) {
        try {
            runInner(transcriptId, sourceUri)
        } catch (e: CancellationException) {
            // Job cancelled (service stopped / user cancel). The checkpoint
            // is already persisted; retry resumes from it. NonCancellable:
            // suspend calls inside a cancelled coroutine throw immediately.
            withContext(kotlinx.coroutines.NonCancellable) {
                markCancelled(transcriptId)
            }
            throw e
        } catch (e: QuotaExceededException) {
            repository.updateStatus(transcriptId, TranscriptStatus.FAILED, "QUOTA_EXCEEDED")
            throw e
        } catch (e: Exception) {
            repository.updateStatus(
                transcriptId, TranscriptStatus.FAILED, e.message ?: e.javaClass.simpleName
            )
            throw e
        } finally {
            progressHolder.remove(transcriptId)
        }
    }

    private suspend fun runInner(transcriptId: Long, sourceUriParam: Uri?) {
        var entity = repository.getById(transcriptId)
            ?: throw IllegalStateException("Transcript $transcriptId not found")
        // Parakeet is a parallel engine, not a whisper model; route around
        // the whisper path entirely so it stays untouched.
        val useParakeet = entity.modelId == com.vocatim.app.data.model.ParakeetModel.ID
        val model = if (useParakeet) null else {
            WhisperModel.fromId(entity.modelId)
                ?: throw IllegalStateException("Unknown model ${entity.modelId}")
        }
        // URI from the intent, or persisted on the row (recovery after kill).
        val sourceUri = sourceUriParam ?: entity.sourceUri?.let(Uri::parse)

        if (entity.audioPath == null && sourceUri != null) {
            repository.updateStatus(transcriptId, TranscriptStatus.CONVERTING)
            progressHolder.update(TranscriptionProgress(transcriptId, converting = true))
            importDir.mkdirs()
            val result = importer.import(
                uri = sourceUri,
                outFile = File(importDir, "import_$transcriptId.wav"),
            ) { fraction ->
                progressHolder.update(
                    TranscriptionProgress(transcriptId, converting = true, fraction = fraction)
                )
            }
            entity = entity.copy(
                audioPath = result.wavFile.absolutePath,
                audioDurationMs = result.durationMs,
            )
            repository.update(entity)
        }

        val audioFile = File(
            entity.audioPath
                ?: throw FileNotFoundException("Audio file for this transcript is gone")
        )
        if (!audioFile.exists()) {
            throw FileNotFoundException("Audio file for this transcript is gone")
        }

        // Free tier: a job may only START while under the 30-minute total.
        // Once started it always finishes — no mid-file cutoffs.
        val isPro = quotaStore.currentIsPro()
        if (!isPro && entity.completedChunks == 0 &&
            quotaStore.currentUsedMs() >= com.vocatim.app.data.billing.QuotaStore.FREE_LIMIT_MS
        ) {
            throw QuotaExceededException()
        }

        val resumeFrom = entity.completedChunks
        if (resumeFrom == 0) {
            // Fresh run (or restart before the first checkpoint): start with
            // clean segments. The text is left alone on purpose — a live
            // session's caption draft stays readable until the first chunk
            // commits and overwrites it with the real transcription.
            repository.clearSegments(transcriptId)
        }
        repository.updateStatus(transcriptId, TranscriptStatus.TRANSCRIBING)

        val startedAt = SystemClock.elapsedRealtime()
        // Resume must rebuild the merged text from already-persisted segments.
        val allSegments = mutableListOf<WhisperSegment>()
        if (resumeFrom > 0) {
            allSegments.addAll(
                repository.getSegments(transcriptId).map {
                    WhisperSegment(it.startMs, it.endMs, it.text)
                }
            )
        }

        // Silero VAD pre-filter (opt-out in Settings): fetched lazily, and a
        // failed fetch (offline) just means this run goes without VAD.
        val vadModelPath = if (userPrefs.current().vadEnabled) {
            com.vocatim.app.data.model.VadModel.ensure(modelsDir, httpClient)?.absolutePath
        } else null

        withContext(Dispatchers.IO) {
            WavStreamReader(audioFile).use { reader ->
                // Cuts land in silence instead of mid-word; deterministic per
                // file, so resume re-derives the exact same chunks.
                val chunks = SilenceAligner.align(
                    ChunkPlanner.plan(reader.totalSamples),
                    reader.totalSamples,
                ) { start, count -> reader.read(start, count) }
                val audioDurationMs = reader.durationMs
                if (entity.audioDurationMs != audioDurationMs) {
                    entity = entity.copy(audioDurationMs = audioDurationMs)
                    repository.update(entity)
                }

                progressHolder.update(
                    TranscriptionProgress(
                        transcriptId = transcriptId,
                        fraction = resumeFrom.toFloat() / chunks.size,
                        chunksDone = resumeFrom,
                        totalChunks = chunks.size,
                        etaMs = rtfStore.estimateMs(
                            remainingAudioMs(chunks, resumeFrom, audioDurationMs),
                            if (model != null) rtfStore.rtfFor(model) else PARAKEET_RTF_GUESS,
                        ),
                    )
                )

                var processedAudioMs = 0L
                var detectedLanguage: String? = null
                for (chunk in chunks) {
                    if (chunk.index < resumeFrom) continue

                    val settings = userPrefs.current()
                    val samples = reader.read(chunk.startSample, chunk.sampleCount)
                    // Near-silent chunks produce nothing but hallucinated
                    // filler; skip inference entirely (also a big speed-up).
                    val result = when {
                        isNearSilent(samples) -> TranscriptionResult(emptyList(), 0L, null)
                        model == null -> parakeetTranscriber.transcribe(
                            samples = samples,
                            numThreads = threadPolicy.threadsFor(settings.threads),
                        )
                        else -> transcriber.transcribe(
                            model = model,
                            samples = samples,
                            language = entity.language,
                            translate = entity.translate,
                            numThreads = threadPolicy.threadsFor(settings.threads),
                            initialPrompt = settings.customVocab.trim().ifBlank { null },
                            beamSize = if (settings.highAccuracy) 5 else 0,
                            vadModelPath = vadModelPath,
                        )
                    }

                    if (detectedLanguage == null) {
                        detectedLanguage = result.detectedLanguage
                    }
                    val accepted = SegmentMerger.acceptSegments(chunk, result.segments)
                    allSegments.addAll(accepted)
                    repository.commitChunk(
                        transcriptId = transcriptId,
                        segments = accepted.map {
                            SegmentEntity(
                                transcriptId = transcriptId,
                                startMs = it.startMs,
                                endMs = it.endMs,
                                text = it.text,
                                words = WordTimings.encode(it.words),
                            )
                        },
                        text = SegmentMerger.mergeText(allSegments),
                        completedChunks = chunk.index + 1,
                    )

                    val chunkMs = chunk.sampleCount * 1000L / WavDecoder.WHISPER_SAMPLE_RATE
                    // Free quota charges SPEECH time, not wall-clock audio:
                    // silence (skipped chunks, VAD-trimmed stretches) is free.
                    if (!isPro) {
                        val speechMs = accepted.sumOf { it.endMs - it.startMs }
                            .coerceIn(0L, chunkMs)
                        quotaStore.addUsage(speechMs)
                    }
                    processedAudioMs += chunkMs
                    val elapsed = SystemClock.elapsedRealtime() - startedAt
                    // Live estimate from this run's own pace.
                    val liveRtf = elapsed.toFloat() / processedAudioMs
                    progressHolder.update(
                        TranscriptionProgress(
                            transcriptId = transcriptId,
                            fraction = (chunk.index + 1).toFloat() / chunks.size,
                            chunksDone = chunk.index + 1,
                            totalChunks = chunks.size,
                            etaMs = (remainingAudioMs(chunks, chunk.index + 1, audioDurationMs) * liveRtf).toLong(),
                        )
                    )
                }

                val elapsed = SystemClock.elapsedRealtime() - startedAt
                val finished = repository.getById(transcriptId) ?: return@use
                // Recordings get a title from their content; imports keep the
                // file name and renamed items are never touched.
                val autoTitle =
                    if (!finished.customTitle && finished.sourceName == null) {
                        TitleGenerator.fromText(finished.text)
                    } else null
                // Reflow the raw text into paragraphs at speaker pauses.
                val segments = repository.getSegments(transcriptId)
                val readableText =
                    if (segments.isNotEmpty()) TranscriptFormatter.paragraphed(segments)
                    else finished.text
                repository.update(
                    finished.copy(
                        status = TranscriptStatus.DONE,
                        text = readableText,
                        // Accumulates across resumed runs.
                        processingTimeMs = finished.processingTimeMs + elapsed,
                        errorMessage = null,
                        title = autoTitle ?: finished.title,
                        detectedLanguage = detectedLanguage ?: finished.detectedLanguage,
                    )
                )
                if (processedAudioMs > 0 && model != null) {
                    rtfStore.recordMeasurement(model, elapsed.toFloat() / processedAudioMs)
                }
            }
        }
    }

    suspend fun markCancelled(transcriptId: Long) {
        repository.updateStatus(transcriptId, TranscriptStatus.FAILED, "CANCELLED")
    }

    private companion object {
        /** First-run ETA guess for Parakeet — it runs well under realtime. */
        const val PARAKEET_RTF_GUESS = 0.3f
    }

    private fun remainingAudioMs(chunks: List<Chunk>, fromChunk: Int, audioDurationMs: Long): Long =
        ChunkPlanner.remainingAudioMs(chunks, fromChunk, audioDurationMs)

    /** True when a chunk is quiet enough that Whisper would only hallucinate. */
    private fun isNearSilent(samples: FloatArray): Boolean {
        if (samples.isEmpty()) return true
        var sumSq = 0.0
        var peak = 0f
        // Stride-sample long chunks: 16kHz * 30s is 480k floats.
        val step = (samples.size / 8000).coerceAtLeast(1)
        var n = 0
        var i = 0
        while (i < samples.size) {
            val a = kotlin.math.abs(samples[i])
            if (a > peak) peak = a
            sumSq += (a * a).toDouble()
            n++
            i += step
        }
        val rms = kotlin.math.sqrt(sumSq / n)
        return peak < 0.02f && rms < 0.005
    }
}
