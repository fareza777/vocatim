package com.vocatim.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import com.vocatim.app.R

object Notifications {
    const val CHANNEL_RECORDING = "recording"
    const val CHANNEL_TRANSCRIPTION = "transcription"
    const val NOTIFICATION_ID_RECORDING = 1
    const val NOTIFICATION_ID_TRANSCRIPTION = 2

    fun createChannels(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RECORDING,
                context.getString(R.string.channel_recording),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TRANSCRIPTION,
                context.getString(R.string.channel_transcription),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }
}
