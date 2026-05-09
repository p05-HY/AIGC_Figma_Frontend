package com.example.blueheartv.floating

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.WindowManager
import androidx.annotation.MainThread
import androidx.core.app.NotificationCompat
import com.example.blueheartv.BlueHeartVApplication
import com.example.blueheartv.MainActivity
import com.example.blueheartv.R

class FloatingBallService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        const val EXTRA_SESSION_ID = "extra_session_id"

        @Volatile
        var isRunning = false
            private set

        fun start(context: Context) {
            if (isRunning) return
            val intent = Intent(context, FloatingBallService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingBallService::class.java))
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var ballView: FloatingBallView? = null
    private var chatWindow: FloatingChatWindow? = null
    private lateinit var windowManager: WindowManager

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        startForeground(NOTIFICATION_ID, buildNotification())

        ballView = FloatingBallView(
            context = this,
            windowManager = windowManager,
            onClick = { toggleChatWindow() },
        )
        ballView?.attach()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        mainHandler.post {
            chatWindow?.dismiss()
            chatWindow = null
            ballView?.detach()
            ballView = null
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @MainThread
    private fun toggleChatWindow() {
        if (chatWindow?.isShowing == true) {
            chatWindow?.dismiss()
            chatWindow = null
            ballView?.setVisible(true)
        } else {
            ballView?.setVisible(false)
            chatWindow = FloatingChatWindow(
                context = this,
                windowManager = windowManager,
                onClose = {
                    chatWindow = null
                    ballView?.setVisible(true)
                },
                onExpandToFull = { sessionId -> openMainActivity(sessionId) },
            )
            chatWindow?.show()
        }
    }

    private fun openMainActivity(sessionId: String?) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            sessionId?.let { putExtra(EXTRA_SESSION_ID, it) }
        }
        startActivity(intent)
    }

    private fun buildNotification() =
        NotificationCompat.Builder(this, BlueHeartVApplication.CHANNEL_FLOATING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.floating_ball_running))
            .setOngoing(true)
            .setSilent(true)
            .build()
}
