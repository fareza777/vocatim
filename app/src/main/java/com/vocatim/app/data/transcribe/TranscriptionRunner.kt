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
class TranscriptionRunner(
    private val repository: TranscriptRepository,
    private val transcriber: WhisperTranscriber,
    private val importer: AudioImporter,
    private val rtfStore: RtfStore,
    private val progressHolder: TranscriptionProgressHolder,
    private val userPrefs: UserPrefs,
    private val threadPolicy: ThreadPolicy,
    private val importDir: File,
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
        val model = WhisperModel.fromId(entity.modelId)
            ?: throw IllegalStateException("Unknown model ${entity.modelId}")
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

        val resumeFrom = entity.completedChunks
        if (resumeFrom == 0) {
            // Fresh run (or restart before the first checkpoint): start clean.
            repository.clearSegments(transcriptId)
            repository.updateText(transcriptId, "")
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

        withContext(Dispatchers.IO) {
            WavStreamReader(audioFile).use { reader ->
                val chunks = ChunkPlanner.plan(reader.totalSamples)
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
                            rtfStore.rtfFor(model),
                        ),
                    )
                )

                var processedAudioMs = 0L
                var detectedLanguage: String? = null
                for (chunk in chunks) {
                    if (chunk.index < resumeFrom) continue

                    val settings = userPrefs.current()
                    val samples = reader.read(chunk.startSample, chunk.sampleCount)
                    val result = transcriber.transcribe(
                        model = model,
                        samples = samples,
                        language = entity.language,
                        translate = entity.translate,
                        numThreads = threadPolicy.threadsFor(settings.threads),
                    )

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
                            )
                        },
                        text = SegmentMerger.mergeText(allSegments),
                        completedChunks = chunk.index + 1,
                    )

                    processedAudioMs += chunk.sampleCount * 1000L / WavDecoder.WHISPER_SAMPLE_RATE
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
                repository.update(
                    finished.copy(
                        status = TranscriptStatus.DONE,
                        // Accumulates across resumed runs.
                        processingTimeMs = finished.processingTimeMs + elapsed,
                        errorMessage = null,
                        title = autoTitle ?: finished.title,
                        detectedLanguage = detectedLanguage ?: finished.detectedLanguage,
                    )
                )
                if (processedAudioMs > 0) {
                    rtfStore.recordMeasurement(model, elapsed.toFloat() / processedAudioMs)
                }
            }
        }
    }

    suspend fun markCancelled(transcriptId: Long) {
        repository.updateStatus(transcriptId, TranscriptStatus.FAILED, "CANCELLED")
    }

    private fun remainingAudioMs(chunks: List<Chunk>, fromChunk: Int, audioDurationMs: Long): Long =
        ChunkPlanner.remainingAudioMs(chunks, fromChunk, audioDurationMs)
}
