package com.vocatim.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import com.vocatim.app.data.model.WhisperModel
import kotlinx.coroutines.flow.first

/**
 * Persists the measured realtime factor (processing time / audio duration)
 * per model, used to estimate how long a transcription will take.
 */
class RtfStore(private val context: Context) {

    suspend fun rtfFor(model: WhisperModel): Float {
        val stored = context.prefsDataStore.data.first()[key(model)]
        return stored ?: DEFAULT_RTF[model] ?: FALLBACK_RTF
    }

    /** Blends the new measurement into the stored value (EMA) to damp outliers. */
    suspend fun recordMeasurement(model: WhisperModel, rtf: Float) {
        if (rtf <= 0f || !rtf.isFinite()) return
        context.prefsDataStore.edit { prefs ->
            val previous = prefs[key(model)]
            prefs[key(model)] = if (previous == null) rtf else previous * 0.6f + rtf * 0.4f
        }
    }

    fun estimateMs(audioDurationMs: Long, rtf: Float): Long =
        (audioDurationMs * rtf).toLong()

    private fun key(model: WhisperModel) = floatPreferencesKey("rtf_${model.id}")

    private companion object {
        // Conservative first-run guesses; replaced by measurements after one job.
        val DEFAULT_RTF = mapOf(
            WhisperModel.TINY to 0.6f,
            WhisperModel.BASE to 1.4f,
            WhisperModel.SMALL to 2.5f,
            WhisperModel.SMALL_Q5 to 1.7f,
        )
        // A model missing from the map must never fail the job.
        const val FALLBACK_RTF = 1.5f
    }
}
