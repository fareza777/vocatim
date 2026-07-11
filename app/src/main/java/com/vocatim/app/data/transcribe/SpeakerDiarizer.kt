package com.vocatim.app.data.transcribe

import com.k2fsa.sherpa.onnx.FastClusteringConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarizationConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationModelConfig
import com.k2fsa.sherpa.onnx.OfflineSpeakerSegmentationPyannoteModelConfig
import com.k2fsa.sherpa.onnx.SpeakerEmbeddingExtractorConfig
import com.vocatim.app.data.model.DiarizationModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

/** One diarized stretch: who spoke from when to when. */
data class SpeakerTurn(val startMs: Long, val endMs: Long, val speaker: Int)

/**
 * Who-spoke-when over a full recording (pyannote segmentation + TitaNet
 * embeddings + clustering, all via sherpa-onnx). One-shot per call: models
 * load, process, release — diarization is occasional, not resident.
 */
class SpeakerDiarizer(
    private val modelsDir: File,
) {
    private val mutex = Mutex()

    /**
     * @param onProgress 0f..1f across the diarization chunks.
     * @return speaker turns with 1-based speaker indices ordered by first
     *  appearance, so "Speaker 1" is whoever talked first.
     */
    suspend fun diarize(
        samples: FloatArray,
        onProgress: (Float) -> Unit,
    ): List<SpeakerTurn> = mutex.withLock {
        withContext(Dispatchers.Default) {
            if (!DiarizationModel.isDownloaded(modelsDir)) {
                throw FileNotFoundException("Speaker detection model is not downloaded")
            }
            val dir = DiarizationModel.dir(modelsDir)
            val config = OfflineSpeakerDiarizationConfig().apply {
                segmentation = OfflineSpeakerSegmentationModelConfig().apply {
                    pyannote = OfflineSpeakerSegmentationPyannoteModelConfig().apply {
                        model = File(dir, DiarizationModel.SEGMENTATION_FILE).absolutePath
                    }
                    numThreads = THREADS
                }
                embedding = SpeakerEmbeddingExtractorConfig(
                    File(dir, DiarizationModel.EMBEDDING_FILE).absolutePath,
                    THREADS,
                    false,
                    "cpu",
                )
                clustering = FastClusteringConfig().apply {
                    // Unknown speaker count: cluster by similarity threshold.
                    numClusters = -1
                    threshold = CLUSTER_THRESHOLD
                }
            }
            val sd = OfflineSpeakerDiarization(null, config)
            try {
                val raw = sd.processWithCallback(
                    samples,
                    { processed, total, _ ->
                        onProgress(
                            if (total > 0) processed.toFloat() / total else 0f
                        )
                        0 // continue
                    },
                    0L,
                )
                // Renumber to 1-based by first appearance for stable labels.
                val order = mutableMapOf<Int, Int>()
                raw.sortedBy { it.start }.map { seg ->
                    val label = order.getOrPut(seg.speaker) { order.size + 1 }
                    SpeakerTurn(
                        startMs = (seg.start * 1000).toLong(),
                        endMs = (seg.end * 1000).toLong(),
                        speaker = label,
                    )
                }
            } finally {
                sd.release()
            }
        }
    }

    private companion object {
        const val THREADS = 2

        /** sherpa default; lower merges voices, higher splits one voice. */
        const val CLUSTER_THRESHOLD = 0.5f
    }
}
