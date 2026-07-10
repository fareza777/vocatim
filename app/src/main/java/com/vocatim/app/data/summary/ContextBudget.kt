package com.vocatim.app.data.summary

import android.app.ActivityManager
import android.content.Context

/** Device-RAM-based ceilings for LLM context allocation. */
object ContextBudget {

    fun totalRamMb(context: Context): Int {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info)
        return (info.totalMem / (1024L * 1024L)).toInt()
    }

    /** Largest context (tokens) this device can afford for the quantized KV
     *  cache without risking a low-memory kill. */
    fun ramCapTokens(context: Context): Int {
        val totalMb = totalRamMb(context)
        return when {
            totalMb >= 7_000 -> 16_384
            totalMb >= 5_200 -> 12_288
            totalMb >= 3_500 -> 8_192
            else -> 4_096
        }
    }

    /** Whether [model] is offered on this device at all. */
    fun supports(context: Context, model: SummaryModel): Boolean =
        totalRamMb(context) >= model.minTotalRamMb
}
