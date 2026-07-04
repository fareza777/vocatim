package com.vocatim.app.data.transcribe

import android.net.Uri
import android.os.SystemClock
import com.vocatim.app.data.audio.AudioImporter
import com.vocatim.app.data.db.SegmentEntity
import com.vocatim.app.data.db.TranscriptStatus
import com.vocatim.app.data.model.WhisperModel
import com.vocatim.app.data.prefs.RtfStore
import com.vocatim.app.data.repository.TranscriptRepository
import com.vocatim.whisper.WhisperSegment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

/**
 * Runs one transcription job end to end: optional import conversion,
 * chunked whisper inference, incremental persistence, progress reporting,
 * and realtime-factor bookkeeping.
 */
class TranscriptionRunner(
    private val repository: TranscriptRepository,
    private val transcriber: WhisperTranscriber,
    private val importer: AudioImporter,
    private val rtfStore: RtfStore,
    private val progressHolder: TranscriptionProgressHolder,
    private val importDir: File,
) {

    /** @param sourceUri set for imported audio that still needs conversion. */
    suspend fun run(transcriptId: Long, sourceUri: Uri?) {
        try {
            runInner(transcriptId, sourceUri)
        } catch (e: CancellationException) {
            // Job cancelled (service stopped / user cancel): mark for retry.
            repository.updateStatus(transcriptId, TranscriptStatus.FAILED, "CANCELLED")
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

    private suspend fun runInner(transcriptId: Long, sourceUri: Uri?) {
        var entity = repository.getById(transcriptId)
            ?: throw IllegalStateException("Transcript $transcriptId not found")
        val model = WhisperModel.fromId(entity.modelId)
            ?: throw IllegalStateException("Unknown model ${entity.modelId}")

        if (entity.audioPath == null && sourceUri != null) {
            repository.updateStatus(transcriptId, TranscriptStatus.CONVERTING)
            progressHolder.update(
                TranscriptionProgress(transcriptId, converting = true)
            )
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

        val audioPath = entity.audioPath
            ?: throw FileNotFoundException("Audio file for this transcript is gone")
        val audioFile = File(audioPath)
        if (!audioFile.exists()) {
            throw FileNotFoundException("Audio file for this transcript is gone")
        }

        // Retry after failure starts clean.
        repository.clearSegments(transcriptId)
        repository.updateText(transcriptId, "")
        repository.updateStatus(transcriptId, TranscriptStatus.TRANSCRIBING)

        val startedAt = SystemClock.elapsedRealtime()
        val allSegments = mutableListOf<WhisperSegment>()

        withContext(Dispatchers.IO) {
            com.vocatim.app.data.audio.WavStreamReader(audioFile).use { reader ->
                val chunks = ChunkPlanner.plan(reader.totalSamples)
                val audioDurationMs = reader.durationMs
                if (entity.audioDurationMs != audioDurationMs) {
                    repository.update(entity.copy(audioDurationMs = audioDurationMs))
                }

                val initialRtf = rtfStore.rtfFor(model)
                progressHolder.update(
                    TranscriptionProgress(
                        transcriptId = transcriptId,
                        totalChunks = chunks.size,
                        etaMs = rtfStore.estimateMs(audioDurationMs, initialRtf),
                    )
                )

                var processedAudioMs = 0L
                for (chunk in chunks) {
                    val samples = reader.read(chunk.startSample, chunk.sampleCount)
                    val result = transcriber.transcribe(
                        model = model,
                        samples = samples,
                        language = entity.language,
                    )

                    val accepted = SegmentMerger.acceptSegments(chunk, result.segments)
                    allSegments.addAll(accepted)
                    repository.appendSegments(
                        transcriptId,
                        accepted.map {
                            SegmentEntity(
                                transcriptId = transcriptId,
                                startMs = it.startMs,
                                endMs = it.endMs,
                                text = it.text,
                            )
                        },
                    )
                    repository.updateText(transcriptId, SegmentMerger.mergeText(allSegments))

                    processedAudioMs += chunk.sampleCount * 1000L /
                        com.vocatim.app.data.audio.WavDecoder.WHISPER_SAMPLE_RATE
                    val elapsed = SystemClock.elapsedRealtime() - startedAt
                    // Live estimate from this job's own pace, more accurate
                    // than the stored value once a few chunks are in.
                    val liveRtf = elapsed.toFloat() / processedAudioMs
                    val remainingAudioMs =
                        (audioDurationMs - chunk.acceptToMs.coerceAtMost(audioDurationMs))
                            .coerceAtLeast(0)
                    progressHolder.update(
                        TranscriptionProgress(
                            transcriptId = transcriptId,
                            fraction = (chunk.index + 1).toFloat() / chunks.size,
                            chunksDone = chunk.index + 1,
                            totalChunks = chunks.size,
                            etaMs = (remainingAudioMs * liveRtf).toLong(),
                        )
                    )
                }

                val processingTimeMs = SystemClock.elapsedRealtime() - startedAt
                val finished = repository.getById(transcriptId) ?: return@use
                repository.update(
                    finished.copy(
                        status = TranscriptStatus.DONE,
                        processingTimeMs = processingTimeMs,
                        errorMessage = null,
                    )
                )
                if (audioDurationMs > 0) {
                    rtfStore.recordMeasurement(
                        model, processingTimeMs.toFloat() / audioDurationMs
                    )
                }
            }
        }
    }
}
