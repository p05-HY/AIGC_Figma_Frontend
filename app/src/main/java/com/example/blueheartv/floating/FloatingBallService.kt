package com.example.blueheartv.floating

import android.app.PendingIntent
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
import com.example.blueheartv.util.toImageAttachment
import com.example.blueheartv.viewmodel.ChatViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.koin.java.KoinJavaComponent.get

enum class FloatingState {
    STATE0,
    STATE1,
    STATE2,
    STATE3,
}

class FloatingBallService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_CLOSE = "com.example.blueheartv.CLOSE_FLOATING_BALL"
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
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var ballView: FloatingBallView? = null
    private var chatWindow: FloatingChatWindow? = null
    private var bubbleInput: FloatingBubbleInput? = null
    private var bubbleNotification: FloatingBubbleNotification? = null
    private lateinit var windowManager: WindowManager

    private var currentState: FloatingState = FloatingState.STATE0
    private var state2ListenerJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        startForeground(NOTIFICATION_ID, buildNotification())

        ballView = FloatingBallView(
            context = this,
            windowManager = windowManager,
            onClick = { handleBallClick() },
            onLongPress = { stopSelf() },
        )
        ballView?.onPositionChanged = { _, _ ->
            bubbleInput?.syncPosition()
            bubbleNotification?.syncPosition()
        }
        ballView?.attach()

        collectHelperResults()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CLOSE) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()
        mainHandler.post {
            bubbleInput?.dismiss()
            bubbleInput = null
            bubbleNotification?.dismiss()
            bubbleNotification = null
            chatWindow?.dismiss()
            chatWindow = null
            ballView?.detach()
            ballView = null
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @MainThread
    private fun handleBallClick() {
        when (currentState) {
            FloatingState.STATE0 -> transitionToState1()
            FloatingState.STATE1 -> transitionToState0()
            FloatingState.STATE2 -> { /* ball hidden in state2, ignore */ }
            FloatingState.STATE3 -> return
        }
    }

    private fun transitionToState0() {
        state2ListenerJob?.cancel()
        state2ListenerJob = null
        bubbleInput?.dismiss()
        bubbleInput = null
        bubbleNotification?.dismiss()
        bubbleNotification = null
        currentState = FloatingState.STATE0
    }

    private fun transitionToState1() {
        val ball = ballView ?: return
        bubbleInput = FloatingBubbleInput(
            context = this,
            windowManager = windowManager,
            ballLayoutParams = ball.getLayoutParams(),
            onSend = { input -> onMessageSent(input) },
            onBackPressed = { transitionToState0() },
            onAttachClick = { handleFloatingAttach() },
            onMicClick = { handleFloatingMic() },
        )
        bubbleInput?.show()
        currentState = FloatingState.STATE1
    }

    private fun transitionToState2() {
        bubbleInput?.dismiss()
        bubbleInput = null
        ballView?.setVisible(false)
        chatWindow = FloatingChatWindow(
            context = this,
            windowManager = windowManager,
            onClose = {
                chatWindow = null
                ballView?.setVisible(true)
                state2ListenerJob?.cancel()
                state2ListenerJob = null
                currentState = FloatingState.STATE0
            },
            onExpandToFull = { sessionId -> openMainActivity(sessionId) },
            onAttachClick = { handleFloatingAttach() },
            onMicClick = { handleFloatingMic() },
        )
        chatWindow?.show()
        currentState = FloatingState.STATE2
        startState2Listener()
    }

    private fun transitionToState3() {
        state2ListenerJob?.cancel()
        state2ListenerJob = null
        bubbleInput?.dismiss()
        bubbleInput = null
        if (chatWindow?.isShowing == true) {
            chatWindow?.dismiss()
            chatWindow = null
            ballView?.setVisible(true)
        }
        currentState = FloatingState.STATE3
    }

    private fun startState2Listener() {
        state2ListenerJob?.cancel()
        val chatViewModel: ChatViewModel = get(ChatViewModel::class.java)
        state2ListenerJob = serviceScope.launch {
            chatViewModel.messageSentEvent.collectLatest { input ->
                if (currentState != FloatingState.STATE2) return@collectLatest
                val type = classifyTask(input)
                mainHandler.post {
                    if (currentState != FloatingState.STATE2) return@post
                    when (type) {
                        "simple" -> { /* stay in state2 */ }
                        "complex" -> {
                            transitionToState3()
                            simulateComplexTask()
                        }
                    }
                }
            }
        }
    }

    private fun onMessageSent(input: String) {
        bubbleInput?.setInputEnabled(false)

        val chatViewModel: ChatViewModel = get(ChatViewModel::class.java)
        chatViewModel.onInputChanged(input)
        chatViewModel.sendMessage()

        serviceScope.launch {
            val type = classifyTask(input)

            mainHandler.post {
                when (type) {
                    "simple" -> transitionToState2()
                    "complex" -> {
                        transitionToState3()
                        simulateComplexTask()
                    }
                }
            }
        }
    }

    private fun simulateComplexTask() {
        serviceScope.launch {
            delay(2000)
            mainHandler.post { onComplexTaskCompleted() }
        }
    }

    @MainThread
    private fun onComplexTaskCompleted() {
        if (currentState != FloatingState.STATE3) return

        val ball = ballView ?: return
        bubbleNotification = FloatingBubbleNotification(
            context = this,
            windowManager = windowManager,
            ballLayoutParams = ball.getLayoutParams(),
            onDismiss = {
                bubbleNotification = null
                currentState = FloatingState.STATE0
            },
        )
        bubbleNotification?.show()
    }

    private fun openMainActivity(sessionId: String?) {
        state2ListenerJob?.cancel()
        state2ListenerJob = null
        chatWindow?.dismiss()
        chatWindow = null
        ballView?.setVisible(true)
        currentState = FloatingState.STATE0

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            sessionId?.let { putExtra(EXTRA_SESSION_ID, it) }
        }
        startActivity(intent)
    }

    private fun handleFloatingAttach() {
        FloatingHelperActivity.launchImagePick(this)
    }

    private fun handleFloatingMic() {
        FloatingHelperActivity.launchSpeech(this)
    }

    private fun collectHelperResults() {
        serviceScope.launch {
            FloatingHelperActivity.imageResult.collect { uri ->
                if (uri != null) {
                    val attachment = runCatching { uri.toImageAttachment(this@FloatingBallService) }.getOrNull()
                    if (attachment != null) {
                        val chatViewModel: ChatViewModel = get(ChatViewModel::class.java)
                        chatViewModel.addImageAttachment(attachment)
                    }
                }
            }
        }
        serviceScope.launch {
            FloatingHelperActivity.speechResult.collect { text ->
                if (!text.isNullOrBlank()) {
                    val chatViewModel: ChatViewModel = get(ChatViewModel::class.java)
                    val current = chatViewModel.uiState.value.inputText
                    chatViewModel.onInputChanged(current + text)
                }
            }
        }
    }

    private fun buildNotification(): android.app.Notification {
        val closeIntent = Intent(this, FloatingBallService::class.java).apply {
            action = ACTION_CLOSE
        }
        val closePending = PendingIntent.getService(
            this, 0, closeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, BlueHeartVApplication.CHANNEL_FLOATING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.floating_ball_running))
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, "关闭悬浮球", closePending)
            .build()
    }
}

// Mock — replace with real API call
private suspend fun classifyTask(input: String): String {
    delay(500)
    return if (input.length > 10) "complex" else "simple"
}
