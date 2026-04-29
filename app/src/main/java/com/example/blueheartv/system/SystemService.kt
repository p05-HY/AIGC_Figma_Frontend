package com.example.blueheartv.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.blueheartv.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class SystemService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var client: SystemWebSocketClient? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("System API 服务启动中"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val config = SystemConfig(
            serverBaseUrl = intent?.getStringExtra(EXTRA_SERVER_URL).orEmpty()
        )
        if (config.serverBaseUrl.isBlank()) {
            notifyStatus("启动失败", "连接参数缺失")
            stopSelf()
            return START_NOT_STICKY
        }

        client?.disconnect()
        val handler = SystemProtocolHandler(SystemApi(this))
        client = SystemWebSocketClient(
            scope = serviceScope,
            config = config,
            handler = handler,
            onStatus = ::notifyStatus
        ).also { it.connect() }

        return START_STICKY
    }

    override fun onDestroy() {
        client?.disconnect()
        client = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notifyStatus(status: String, detail: String?) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification("$status ${detail.orEmpty()}".trim()))
    }

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(content)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "System API",
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val ACTION_START = "com.example.blueheartv.system.START"
        private const val ACTION_STOP = "com.example.blueheartv.system.STOP"
        private const val EXTRA_SERVER_URL = "server_url"
        private const val NOTIFICATION_ID = 2001
        private const val NOTIFICATION_CHANNEL_ID = "system_connection"

        fun createStartIntent(context: Context, config: SystemConfig): Intent {
            return Intent(context, SystemService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SERVER_URL, config.serverBaseUrl)
            }
        }

        fun createStopIntent(context: Context): Intent {
            return Intent(context, SystemService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }
}
