package com.vocatim.app.data.transcribe

import android.content.Context
import android.os.PowerManager
import androidx.core.content.getSystemService
import com.vocatim.whisper.WhisperCpuConfig

/**
 * Decides the whisper thread count per chunk: the user's setting (0 = auto),
 * halved under severe thermal throttling so long jobs slow down gracefully
 * instead of overheating or being killed.
 */
class ThreadPolicy(private val context: Context) {

    fun threadsFor(userSetting: Int): Int {
        val base = if (userSetting > 0) userSetting else WhisperCpuConfig.preferredThreadCount
        return if (isThermalThrottled()) (base / 2).coerceAtLeast(1) else base
    }

    private fun isThermalThrottled(): Boolean {
        val pm = context.getSystemService<PowerManager>() ?: return false
        return if (android.os.Build.VERSION.SDK_INT >= 29) {
            pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
        } else {
            false
        }
    }
}
