package com.example.blueheartv.control

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.blueheartv.R
import com.example.blueheartv.chat.AgentServerClient
import com.example.blueheartv.chat.AgentServerConfigStore
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

class AdbWebSocketService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var closedByClient = false
    private var isConnecting = false
    private var connectSent = false
    private var heartbeatJob: Job? = null
    private val incomingBuffer = StringBuilder()
    private lateinit var executor: ShizukuAdbExecutor
    private lateinit var collector: AdbSnapshotCollector
    private lateinit var overlay: AdbOverlayController

    override fun onCreate() {
        super.onCreate()
        executor = ShizukuAdbExecutor(packageName)
        collector = AdbSnapshotCollector(this, executor)
        overlay = AdbOverlayController(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("ADB 工具服务启动中"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        connect()
        return START_STICKY
    }

    override fun onDestroy() {
        closedByClient = true
        heartbeatJob?.cancel()
        heartbeatJob = null
        webSocket?.close(1000, "client stop")
        webSocket = null
        overlay.hide()
        runBlocking(Dispatchers.IO) { runCatching { executor.destroy() } }
        serviceScope.cancel()
        client.dispatcher.executorService.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun connect() {
        val config = AgentServerConfigStore.snapshot()
        if (!config.isConfigured) {
            updateNotification("请先配置 Agent Server")
            stopSelf()
            return
        }

        if (isConnecting) {
            Log.d(TAG, "connect() skipped: already connecting")
            return
        }

        val url = runCatching {
            AgentServerClient(configProvider = { AgentServerConfigStore.snapshot() }).adbWebSocketUrl()
        }.getOrElse {
            updateNotification("ADB 连接地址无效: ${it.message}")
            return
        }

        closedByClient = false
        isConnecting = true
        connectSent = false
        heartbeatJob?.cancel()
        heartbeatJob = null
        webSocket?.close(1000, "reconnect")
        val request = Request.Builder().url(url).build()
        Log.d(TAG, "connect() opening websocket: $url")
        updateNotification("ADB 连接中: $url")
        webSocket = client.newWebSocket(request, listener)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            isConnecting = false
            Log.d(TAG, "onOpen: websocket connected")
            updateNotification("ADB 已连接")
            overlay.show()
            overlay.update("ADB 已连接", "等待 Agent 指令")
            serviceScope.launch {
                if (!connectSent) {
                    connectSent = true
                    Log.d(TAG, "sendConnect()")
                    sendConnect()
                }
                startHeartbeat()
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            incomingBuffer.append(text)
            while (true) {
                val newlineIndex = incomingBuffer.indexOf("\n")
                if (newlineIndex < 0) break
                val raw = incomingBuffer.substring(0, newlineIndex).trim()
                incomingBuffer.delete(0, newlineIndex + 1)
                if (raw.isBlank()) continue
                serviceScope.launch { handleLine(raw) }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            isConnecting = false
            connectSent = false
            heartbeatJob?.cancel()
            heartbeatJob = null
            Log.d(TAG, "onClosed: code=$code reason=$reason")
            updateNotification("ADB 连接关闭: $code $reason")
            overlay.update("ADB 连接关闭", "$code $reason")
            reconnectLater()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            isConnecting = false
            connectSent = false
            heartbeatJob?.cancel()
            heartbeatJob = null
            Log.d(TAG, "onFailure: ${t.message}")
            updateNotification("ADB 连接异常: ${t.message}")
            overlay.show()
            overlay.update("ADB 连接异常", t.message)
            reconnectLater()
        }
    }

    private suspend fun sendConnect() {
        val display = displayInfo()
        val snapshot = snapshotJson()
        snapshot.put("width", display.first)
        snapshot.put("height", display.second)
        send(
            JSONObject()
                .put("type", "request")
                .put("message", "connect")
                .put("requestId", 1)
                .put("data", snapshot),
        )
    }

    private suspend fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = serviceScope.launch {
            while (!closedByClient) {
                delay(20_000.milliseconds)
                send(JSONObject().put("type", "request").put("message", "ping").put("data", JSONObject.NULL))
            }
        }
    }

    private suspend fun handleLine(raw: String) {
        val envelope = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val type = envelope.optString("type")
        val message = envelope.optString("message")
        if (type == "response" && message == "pong") {
            overlay.update("ADB 心跳正常")
            return
        }
        if (type != "request") return

        val requestId = if (envelope.has("requestId")) envelope.optInt("requestId") else null
        if (message == "ping") {
            send(JSONObject().put("type", "response").put("message", "pong").put("data", JSONObject.NULL))
            return
        }

        overlay.show()
        overlay.update("执行 $message")
        runCatching {
            executeRequest(message, envelope.optJSONObject("data"))
            actionResult(requestId)
        }.getOrElse { error ->
            errorResult(requestId, error.message ?: "执行失败")
        }.let { response ->
            send(response)
        }
    }

    private suspend fun executeRequest(message: String, data: JSONObject?) {
        when (message) {
            "observe" -> Unit
            "launch" -> runShell("monkey -p ${data.requiredString("package")} -c android.intent.category.LAUNCHER 1")
            "tap" -> runShell("input tap ${data.requiredInt("x")} ${data.requiredInt("y")}")
            "type" -> typeText(data.requiredString("text"))
            "swipe" -> runShell(
                "input swipe ${data.requiredInt("startX")} ${data.requiredInt("startY")} " +
                        "${data.requiredInt("endX")} ${data.requiredInt("endY")} 300",
            )

            "longPress" -> runShell(
                "input swipe ${data.requiredInt("x")} ${data.requiredInt("y")} ${data.requiredInt("x")} ${
                    data.requiredInt(
                        "y"
                    )
                } 700"
            )

            "doubleTap" -> {
                runShell("input tap ${data.requiredInt("x")} ${data.requiredInt("y")}")
                delay(100.milliseconds)
                runShell("input tap ${data.requiredInt("x")} ${data.requiredInt("y")}")
            }

            "keyevent" -> runShell("input keyevent ${data.requiredInt("keyevent")}")
            "interact" -> waitForUserInteraction(data?.optString("message"))
            else -> error("未知 ADB 指令: $message")
        }
    }

    private suspend fun typeText(text: String) {
        val escaped = text.replace("'", "\\'")
        runShell("am broadcast -a ADB_INPUT_TEXT --es msg '$escaped'")
    }

    private suspend fun runShell(command: String) {
        val result = withContext(Dispatchers.IO) { executor.execute(command) }
        if (!result.isSuccess) {
            error(result.stderr.ifBlank { "shell exitCode=${result.exitCode}" })
        }
    }

    private suspend fun waitForUserInteraction(message: String?) {
        if (!overlay.show()) {
            error("悬浮窗权限未授权，无法显示用户交互提示。")
        }
        val deferred = CompletableDeferred<Unit>()
        overlay.waitForInteraction(message) {
            deferred.complete(Unit)
        }
        deferred.await()
    }

    private suspend fun actionResult(requestId: Int?): JSONObject {
        return JSONObject()
            .put("type", "response")
            .put("message", "actionResult")
            .put("requestId", requestId ?: JSONObject.NULL)
            .put("data", snapshotJson())
    }

    private suspend fun errorResult(requestId: Int?, message: String): JSONObject {
        return JSONObject()
            .put("type", "response")
            .put("message", "error")
            .put("requestId", requestId ?: JSONObject.NULL)
            .put(
                "data",
                snapshotJson().put("message", message),
            )
    }

    private suspend fun snapshotJson(): JSONObject {
        return runCatching { collector.collect().toJson() }
            .getOrElse {
                JSONObject()
                    .put("screenshot", JSONObject.NULL)
                    .put("ui", AdbAccessibilityService.dumpUiTree() ?: JSONObject.NULL)
                    .put("currentPackage", AdbAccessibilityService.currentPackageName() ?: JSONObject.NULL)
                    .put("activity", AdbAccessibilityService.currentActivityName() ?: JSONObject.NULL)
            }
    }

    private fun send(envelope: JSONObject) {
        webSocket?.send("$envelope\n")
    }

    private fun reconnectLater() {
        if (closedByClient) return
        serviceScope.launch {
            delay(3_000.milliseconds)
            Log.d(TAG, "reconnectLater()")
            connect()
        }
    }

    private fun displayInfo(): Pair<Int, Int> {
        val windowManager = getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics().also { windowManager.defaultDisplay.getRealMetrics(it) }
            metrics.widthPixels to metrics.heightPixels
        }
    }

    private fun updateNotification(content: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(content))
    }

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Echo ADB")
            .setContentText(content)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "ADB 工具连接",
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val NOTIFICATION_ID = 3001
        private const val NOTIFICATION_CHANNEL_ID = "adb_tool_connection"
        private const val TAG = "AdbWebSocketService"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, AdbWebSocketService::class.java))
        }
    }
}

private fun JSONObject?.requiredString(key: String): String {
    require(this != null && has(key) && !isNull(key)) { "$key 缺失。" }
    return getString(key)
}

private fun JSONObject?.requiredInt(key: String): Int {
    require(this != null && has(key) && !isNull(key)) { "$key 缺失。" }
    return getInt(key)
}
