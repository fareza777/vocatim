package com.vocatim.app

import android.app.Application
import android.os.StrictMode
import com.vocatim.app.service.Notifications
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class VocatimApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build()
            )
        }
    }
}
