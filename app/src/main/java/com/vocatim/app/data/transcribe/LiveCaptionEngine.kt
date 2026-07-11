package com.vocatim.app.data.transcribe

import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import com.vocatim.app.data.audio.WavDecoder
import com.vocatim.app.data.model.LiveCaptionModel
import java.io.File
import java.io.FileNotFoundException

/** Finalized caption lines plus the sentence currently being spoken. */
data class CaptionState(
    val lines: List<String> = emptyList(),
    val partial: String = "",
)

/**
 * True streaming ASR for the Live recording mode: audio buffers go in as
 * they're captured, words come out with sub-second latency. Endpointing
 * (a long-enough pause) finalizes the current line and resets the decoder.
 *
 * NOT thread-safe — the caller confines start/feed/stop to one worker.
 */
class LiveCaptionEngine(
    private val modelsDir: File,
) {
    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private val lines = mutableListOf<String>()

    fun start(numThreads: Int) {
        if (recognizer != null) return
        if (!LiveCaptionModel.isDownloaded(modelsDir)) {
            throw FileNotFoundException("Live caption model is not downloaded")
        }
        val dir = LiveCaptionModel.dir(modelsDir)
        val config = OnlineRecognizerConfig().apply {
            modelConfig = OnlineModelConfig().apply {
                transducer = OnlineTransducerModelConfig().apply {
                    encoder = File(dir, "encoder-epoch-99-avg-1.int8.onnx").absolutePath
                    decoder = File(dir, "decoder-epoch-99-avg-1.int8.onnx").absolutePath
                    joiner = File(dir, "joiner-epoch-99-avg-1.int8.onnx").absolutePath
                }
                tokens = File(dir, "tokens.txt").absolutePath
                modelType = "zipformer"
                this.numThreads = LIVE_THREADS
            }
            decodingMethod = "greedy_search"
            // A pause finalizes the line; defaults (~2.4s / 1.2s rules) feel
            // right for dictation and meetings alike.
            enableEndpoint = true
        }
        lines.clear()
        recognizer = OnlineRecognizer(null, config)
        stream = recognizer?.createStream("")
    }

    /** Feeds one captured buffer; returns the caption state after decoding. */
    fun feed(samples: FloatArray): CaptionState {
        val rec = recognizer ?: return CaptionState(lines.toList(), "")
        val st = stream ?: return CaptionState(lines.toList(), "")
        st.acceptWaveform(samples, WavDecoder.WHISPER_SAMPLE_RATE)
        while (rec.isReady(st)) {
            rec.decode(st)
        }
        var partial = rec.getResult(st).text.trim()
        if (rec.isEndpoint(st)) {
            if (partial.isNotEmpty()) {
                lines.add(partial)
                if (lines.size > MAX_LINES) lines.removeAt(0)
                partial = ""
            }
            rec.reset(st)
        }
        return CaptionState(lines.toList(), partial)
    }

    fun stop() {
        stream?.release()
        stream = null
        recognizer?.release()
        recognizer = null
        lines.clear()
    }

    private companion object {
        /** Captions are a preview; never compete with the recorder. */
        const val LIVE_THREADS = 2

        /** Keep the on-screen history bounded; the WAV holds the real thing. */
        const val MAX_LINES = 12
    }
}
