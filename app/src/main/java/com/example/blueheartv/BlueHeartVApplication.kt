package com.example.blueheartv

import android.app.Application
import com.example.blueheartv.telemetry.AppEventLogger

class BlueHeartVApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppEventLogger.error(
                event = "app_crash",
                message = "Uncaught exception on thread=${thread.name}",
                throwable = throwable,
            )
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
