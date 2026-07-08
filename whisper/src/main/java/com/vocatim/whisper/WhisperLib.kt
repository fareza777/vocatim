package com.vocatim.whisper

import android.os.Build
import android.util.Log
import java.io.File

private const val LOG_TAG = "WhisperLib"

internal class WhisperLib {
    companion object {
        init {
            Log.d(LOG_TAG, "Primary ABI: ${Build.SUPPORTED_ABIS[0]}")
            // ARMv8.2a fp16 arithmetic needs runtime detection: -march is baked
            // into a separate .so, loading it on an older core would SIGILL.
            var loadV8fp16 = false
            if (Build.SUPPORTED_ABIS[0] == "arm64-v8a") {
                val cpuInfo = cpuInfo()
                cpuInfo?.let {
                    if (it.contains("fphp")) {
                        Log.d(LOG_TAG, "CPU supports fp16 arithmetic")
                        loadV8fp16 = true
                    }
                }
            }

            if (loadV8fp16) {
                Log.d(LOG_TAG, "Loading libwhisper_v8fp16_va.so")
                System.loadLibrary("whisper_v8fp16_va")
            } else {
                Log.d(LOG_TAG, "Loading libwhisper.so")
                System.loadLibrary("whisper")
            }
        }

        private fun cpuInfo(): String? = try {
            File("/proc/cpuinfo").inputStream().bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Couldn't read /proc/cpuinfo", e)
            null
        }

        external fun initContext(modelPath: String): Long
        external fun freeContext(contextPtr: Long)
        external fun fullTranscribe(
            contextPtr: Long,
            numThreads: Int,
            language: String,
            translate: Boolean,
            audioData: FloatArray,
            initialPrompt: String?,
            beamSize: Int,
        ): Int
        external fun getTextSegmentCount(contextPtr: Long): Int
        external fun getTextSegment(contextPtr: Long, index: Int): String
        external fun getTextSegmentT0(contextPtr: Long, index: Int): Long
        external fun getTextSegmentT1(contextPtr: Long, index: Int): Long
        external fun getTokenCount(contextPtr: Long, segment: Int): Int
        external fun getTokenText(contextPtr: Long, segment: Int, token: Int): String
        external fun getTokenT0(contextPtr: Long, segment: Int, token: Int): Long
        external fun getTokenT1(contextPtr: Long, segment: Int, token: Int): Long
        external fun getDetectedLanguage(contextPtr: Long): String?
        external fun getSystemInfo(): String
    }
}
