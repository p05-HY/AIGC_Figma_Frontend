package com.example.blueheartv

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.blueheartv.chat.AppContextHolder
import com.example.blueheartv.di.appModule
import com.example.blueheartv.telemetry.AppEventLogger
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class BlueHeartVApplication : Application() {

    companion object {
        const val CHANNEL_FLOATING = "floating_ball_channel"
    }

    override fun onCreate() {
        super.onCreate()

        AppContextHolder.install(applicationContext)

        startKoin {
            androidContext(this@BlueHeartVApplication)
            modules(appModule)
        }

        createNotificationChannels()

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

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_FLOATING,
                getString(R.string.floating_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.floating_channel_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }
}
