package com.vocatim.app.data.transcribe

import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.vocatim.app.data.audio.WavDecoder
import com.vocatim.app.data.model.ParakeetModel
import com.vocatim.whisper.WhisperSegment
import com.vocatim.whisper.WhisperWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import kotlin.random.Random

/**
 * English-only transcription via NVIDIA Parakeet TDT (sherpa-onnx transducer).
 * A parallel engine beside [WhisperTranscriber] — the whisper path is never
 * touched. Produces the same [TranscriptionResult] shape, with words rebuilt
 * from SentencePiece tokens and segments split at speech gaps.
 */
class ParakeetTranscriber(
    private val modelsDir: File,
) {
    private val mutex = Mutex()
    private var recognizer: OfflineRecognizer? = null

    suspend fun transcribe(
        samples: FloatArray,
        numThreads: Int,
    ): TranscriptionResult = mutex.withLock {
        withContext(Dispatchers.Default) {
            val rec = loadLocked(numThreads)
            val startedAt = SystemClock.elapsedRealtime()
            val stream = rec.createStream()
            try {
                // Parakeet/NeMo via sherpa-onnx often returns empty text when
                // the feature pipeline sees perfectly clean PCM (dither=0).
                // Known issue: k2-fsa/sherpa-onnx#2258. Apply a tiny dither
                // both in FeatureConfig and on the waveform as a belt-and-
                // suspenders fix for AARs that still hardcode dither=0.
                stream.acceptWaveform(
                    withDither(samples),
                    WavDecoder.WHISPER_SAMPLE_RATE,
                )
                rec.decode(stream)
                val result = rec.getResult(stream)
                val segments = toSegments(result)
                if (segments.isEmpty() && result.text.isBlank()) {
                    Log.w(
                        TAG,
                        "Parakeet returned empty text " +
                            "(samples=${samples.size}, tokens=${result.tokens.size})",
                    )
                }
                TranscriptionResult(
                    segments = segments,
                    processingTimeMs = SystemClock.elapsedRealtime() - startedAt,
                    detectedLanguage = "en",
                )
            } finally {
                stream.release()
            }
        }
    }

    suspend fun release() = mutex.withLock {
        recognizer?.release()
        recognizer = null
    }

    private fun loadLocked(numThreads: Int): OfflineRecognizer {
        recognizer?.let { return it }
        if (!ParakeetModel.isDownloaded(modelsDir)) {
            throw FileNotFoundException("Parakeet model is not downloaded")
        }
        val dir = ParakeetModel.dir(modelsDir)
        val config = OfflineRecognizerConfig().apply {
            featConfig = FeatureConfig().apply {
                sampleRate = WavDecoder.WHISPER_SAMPLE_RATE
                featureDim = 80
                // Required for Parakeet TDT — default 0 yields empty transcripts
                // on many files (sherpa-onnx#2258).
                dither = DITHER
            }
            modelConfig = OfflineModelConfig().apply {
                transducer = OfflineTransducerModelConfig().apply {
                    encoder = File(dir, "encoder.int8.onnx").absolutePath
                    decoder = File(dir, "decoder.int8.onnx").absolutePath
                    joiner = File(dir, "joiner.int8.onnx").absolutePath
                }
                tokens = File(dir, "tokens.txt").absolutePath
                modelType = "nemo_transducer"
                this.numThreads = numThreads.coerceIn(1, 8)
            }
            // greedy_search is required for TDT; beam search hallucinates /
            // returns empty ~20% of the time (sherpa-onnx#3267).
            decodingMethod = "greedy_search"
        }
        // null AssetManager -> load from absolute file paths.
        return OfflineRecognizer(null, config).also { recognizer = it }
    }

    /** Adds tiny Gaussian noise so NeMo FBANK features are not all-zero-edge. */
    private fun withDither(samples: FloatArray): FloatArray {
        if (samples.isEmpty()) return samples
        val out = samples.copyOf()
        val rng = Random.Default
        for (i in out.indices) {
            // Box-Muller-ish single sample; amplitude matches FeatureConfig dither.
            out[i] += ((rng.nextFloat() + rng.nextFloat() - 1f) * DITHER)
        }
        return out
    }

    /**
     * Tokens are SentencePiece pieces ("▁" marks a word start) with a
     * timestamp (and TDT duration) each. Rebuild words, then split into
     * segments at pauses so timestamped views and SRT export stay useful.
     */
    private fun toSegments(result: OfflineRecognizerResult): List<WhisperSegment> {
        val tokens = result.tokens
        if (tokens.isEmpty()) {
            val text = result.text.trim()
            return if (text.isEmpty()) emptyList()
            else listOf(WhisperSegment(0L, estimateEndMs(result), " $text", emptyList()))
        }

        val words = mutableListOf<WhisperWord>()
        val current = StringBuilder()
        var wordStartMs = 0L
        var wordEndMs = 0L
        for (i in tokens.indices) {
            val raw = tokens[i]
            // Skip blank / special tokens that carry no printable text.
            if (raw.isBlank() || raw == "<blk>" || raw == "<blank>" || raw == "<eps>") continue
            val token = raw
            val startMs = ((result.timestamps.getOrNull(i) ?: 0f) * 1000).toLong()
            val durMs = ((result.durations.getOrNull(i) ?: 0.08f) * 1000).toLong().coerceAtLeast(20L)
            if (token.startsWith("▁") && current.isNotEmpty()) {
                words.add(WhisperWord(wordStartMs, wordEndMs, current.toString()))
                current.setLength(0)
            }
            if (current.isEmpty()) wordStartMs = startMs
            current.append(token.removePrefix("▁"))
            wordEndMs = startMs + durMs
        }
        if (current.isNotEmpty()) {
            words.add(WhisperWord(wordStartMs, wordEndMs, current.toString()))
        }

        if (words.isEmpty()) {
            val text = result.text.trim()
            return if (text.isEmpty()) emptyList()
            else listOf(WhisperSegment(0L, estimateEndMs(result), " $text", emptyList()))
        }

        // Segment at speech gaps (or a max word count as a backstop).
        val segments = mutableListOf<WhisperSegment>()
        var segWords = mutableListOf<WhisperWord>()
        fun flush() {
            if (segWords.isEmpty()) return
            segments.add(
                WhisperSegment(
                    startMs = segWords.first().startMs,
                    endMs = segWords.last().endMs,
                    text = " " + segWords.joinToString(" ") { it.text },
                    words = segWords.toList(),
                )
            )
            segWords = mutableListOf()
        }
        for (word in words) {
            val gap = segWords.lastOrNull()?.let { word.startMs - it.endMs } ?: 0L
            if (segWords.isNotEmpty() && (gap > SEGMENT_GAP_MS || segWords.size >= MAX_SEGMENT_WORDS)) {
                flush()
            }
            segWords.add(word)
        }
        flush()
        return segments
    }

    private fun estimateEndMs(result: OfflineRecognizerResult): Long {
        val lastTs = result.timestamps.lastOrNull() ?: 0f
        val lastDur = result.durations.lastOrNull() ?: 0f
        val ms = ((lastTs + lastDur) * 1000).toLong()
        return if (ms > 0) ms else 1_000L
    }

    private companion object {
        private const val TAG = "ParakeetTranscriber"
        const val SEGMENT_GAP_MS = 900L
        const val MAX_SEGMENT_WORDS = 24
        /** Matches sherpa-onnx recommended --dither=0.001 for Parakeet. */
        const val DITHER = 0.001f
    }
}
