package com.example.blueheartv.control

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.blueheartv.BuildConfig
import com.example.blueheartv.R
import com.example.blueheartv.chat.AgentServerClient
import com.example.blueheartv.chat.AgentServerConfigStore
import com.example.blueheartv.chat.DeviceIdStore
import com.example.blueheartv.floating.FloatingBallService
import com.example.blueheartv.system.SystemService
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

class AdbWebSocketService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)  // TCP 层心跳，防止 NAT/路由器超时断连
        .build()

    private val socketTracker = ActiveSocketTracker<WebSocket>()
    private var closedByClient = false
    private var isConnecting = false
    private var connectSent = false
    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private val actionExecutionMutex = Mutex()
    private val activeActionJobs = mutableMapOf<String, MutableSet<Job>>()
    private var wakeLock: android.os.PowerManager.WakeLock? = null
    private val incomingBuffer = StringBuilder()
    private lateinit var executor: ShizukuAdbExecutor
    private lateinit var collector: AdbSnapshotCollector
    private lateinit var overlay: AdbOverlayController
    private lateinit var actionLedger: PhoneActionLedger

    override fun onCreate() {
        super.onCreate()
        executor = ShizukuAdbExecutor(packageName)
        collector = AdbSnapshotCollector(this, executor)
        overlay = AdbOverlayController(this)
        actionLedger = PhoneActionLedger(
            SharedPreferencesPhoneActionLedgerStore(
                getSharedPreferences(ACTION_LEDGER_PREFS, MODE_PRIVATE),
            ),
        )
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.assist_notification_starting)))
        // WiFi 锁：防止屏幕关闭后系统断开 WebSocket
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerManager.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,
            "BlueHeartV:AdbWebSocket"
        ).apply { acquire() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALL) {
            FloatingBallService.stop(this)
            startService(SystemService.createStopIntent(this))
            overlay.hide()
            stopSelf()
            return START_NOT_STICKY
        }
        connect()
        return START_STICKY
    }

    override fun onDestroy() {
        closedByClient = true
        activeActionJobs.keys.toList().forEach { runId ->
            actionLedger.cancelRun(runId)
            cancelActiveActions(runId)
            Log.i(TAG, "phone_run_cancelled runId=$runId")
        }
        heartbeatJob?.cancel()
        heartbeatJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        socketTracker.current?.let { socket ->
            socketTracker.clearIfCurrent(socket)
            socket.close(1000, "client stop")
        }
        overlay.hide()
        runBlocking(Dispatchers.IO) { runCatching { executor.destroy() } }
        serviceScope.cancel()
        client.dispatcher.executorService.shutdown()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun connect() {
        val config = AgentServerConfigStore.snapshot()
        if (!config.isConfigured) {
            updateNotification(getString(R.string.assist_notification_config_required))
            stopSelf()
            return
        }

        if (isConnecting || socketTracker.current != null) {
            Log.d(TAG, "connect() skipped: connection already active")
            return
        }

        val url = runCatching {
            AgentServerClient(configProvider = { AgentServerConfigStore.snapshot() }).adbWebSocketUrl()
        }.getOrElse {
            updateNotification(userVisibleStatus(getString(R.string.assist_notification_url_invalid), it.message))
            return
        }

        closedByClient = false
        isConnecting = true
        connectSent = false
        heartbeatJob?.cancel()
        heartbeatJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        val request = Request.Builder()
            .url(url)
            .addDeviceIdHeader()
            .build()
        Log.d(TAG, "connect() opening websocket: $url")
        updateNotification(userVisibleStatus(getString(R.string.assist_notification_connecting), url))
        socketTracker.replace(client.newWebSocket(request, listener))
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            if (socketTracker.current !== webSocket) {
                webSocket.close(1000, "stale socket")
                return
            }
            isConnecting = false
            Log.d(TAG, "onOpen: websocket connected")
            updateNotification(getString(R.string.assist_notification_connected))
            showDebugOverlay("手机协助能力已连接", "等待指令")
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
            if (socketTracker.current !== webSocket) return
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
            if (!socketTracker.clearIfCurrent(webSocket)) {
                Log.d(TAG, "onClosed: ignored stale socket code=$code reason=$reason")
                return
            }
            isConnecting = false
            connectSent = false
            heartbeatJob?.cancel()
            heartbeatJob = null
            Log.d(TAG, "onClosed: code=$code reason=$reason")
            updateNotification(userVisibleStatus(getString(R.string.assist_notification_closed), "$code $reason"))
            showDebugOverlay("手机协助能力已断开", "$code $reason")
            reconnectLater()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (!socketTracker.clearIfCurrent(webSocket)) {
                Log.d(TAG, "onFailure: ignored stale socket: ${t.message}")
                return
            }
            isConnecting = false
            connectSent = false
            heartbeatJob?.cancel()
            heartbeatJob = null
            Log.d(TAG, "onFailure: ${t.message}")
            updateNotification(userVisibleStatus(getString(R.string.assist_notification_error), t.message))
            showDebugOverlay("手机协助能力连接异常", t.message)
            reconnectLater()
        }
    }

    private suspend fun sendConnect() {
        // 先采集快照（内部会降采样并更新 ScreenScaleState），再据此上报降采样后分辨率。
        // 方案 A：connect 上报的分辨率必须与截图口径一致，模型坐标即基于该分辨率。
        val snapshot = snapshotJson()
        val scale = ScreenScaleState.current()
        if (scale != null) {
            snapshot.put("width", scale.scaledWidth)
            snapshot.put("height", scale.scaledHeight)
        } else {
            val display = displayInfo()
            snapshot.put("width", display.first)
            snapshot.put("height", display.second)
        }
        DeviceIdStore.deviceId()?.trim()?.takeIf { it.isNotBlank() }?.let { deviceId ->
            snapshot.put("deviceId", deviceId)
            snapshot.put("token", deviceId)
        }
        // deviceId 主要由 WebSocket 路径段 /adb/{deviceId} 和 X-Device-Id 承载；
        // connect data 冗余携带 deviceId/token，便于后端平滑兼容旧协议或调试。
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
                delay(10_000.milliseconds)  // 10 秒心跳，更快发现断连
                send(JSONObject().put("type", "request").put("message", "ping").put("data", JSONObject.NULL))
            }
        }
    }

    private suspend fun handleLine(raw: String) {
        val envelope = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val type = envelope.optString("type")
        val message = envelope.optString("message")
        if (type == "response" && message == "pong") {
            showDebugOverlay("连接状态正常")
            return
        }
        if (type != "request") return

        val requestId = if (envelope.has("requestId")) envelope.optInt("requestId") else null
        if (message == "ping") {
            send(JSONObject().put("type", "response").put("message", "pong").put("data", JSONObject.NULL))
            return
        }

        if (message == "cancel") {
            val runId = envelope.optJSONObject("data")?.optString("runId")?.trim().orEmpty()
            if (runId.isBlank()) {
                send(errorResult(requestId, "取消请求缺少 runId。"))
                return
            }
            actionLedger.cancelRun(runId)
            cancelActiveActions(runId)
            send(
                JSONObject()
                    .put("type", "response")
                    .put("message", "cancelled")
                    .put("requestId", requestId ?: JSONObject.NULL)
                    .put("data", JSONObject().put("runId", runId).put("status", "cancelled")),
            )
            return
        }

        val data = envelope.optJSONObject("data")
        val control = data.phoneActionControl()
        if (control == null) {
            send(errorResult(requestId, "拒绝执行缺少 runId/actionId 的手机操作。"))
            return
        }
        if (control.deadlineEpochMs < System.currentTimeMillis()) {
            actionLedger.complete(control.runId, control.actionId, PhoneActionState.FAILED)
            send(errorResult(requestId, "手机操作指令已过期。"))
            return
        }

        showDebugOverlay("正在执行指令", message)
        val response = when (actionLedger.admit(control.runId, control.actionId)) {
            PhoneActionAdmission.Accepted -> {
                Log.i(TAG, "phone_action_accepted runId=${control.runId} actionId=${control.actionId} command=$message")
                executeControlledAction(
                    requestId = requestId,
                    message = message,
                    data = data,
                    control = control,
                )
            }

            PhoneActionAdmission.DuplicateCompleted -> actionResult(requestId).also { response ->
                response.getJSONObject("data").put("deduplicated", true)
            }

            PhoneActionAdmission.RunCancelled -> errorResult(requestId, "该任务已取消，拒绝执行手机操作。")
            PhoneActionAdmission.InFlight -> errorResult(requestId, "相同手机操作正在执行，拒绝重复执行。")
            PhoneActionAdmission.IndeterminateReplay -> errorResult(requestId, "操作状态不确定，拒绝在重启后重放。")
            PhoneActionAdmission.DuplicateRejected -> errorResult(requestId, "该手机操作已失败或取消，拒绝重放。")
            PhoneActionAdmission.RunLimitReached -> errorResult(requestId, "本任务手机操作次数达到安全上限。")
        }
        send(response)
    }

    private suspend fun executeControlledAction(
        requestId: Int?,
        message: String,
        data: JSONObject?,
        control: PhoneActionControl,
    ): JSONObject {
        val job = currentCoroutineContext()[Job]
        job?.let { registerActiveAction(control.runId, it) }
        return try {
            actionExecutionMutex.withLock {
                ensureActionStillAllowed(control)
                if (!actionLedger.markRunning(control.runId, control.actionId)) {
                    return@withLock errorResult(requestId, "手机操作已取消或已被替代。")
                }
                try {
                    ensureActionStillAllowed(control)
                    executeRequest(message, data)
                    ensureActionStillAllowed(control)
                    actionLedger.complete(control.runId, control.actionId, PhoneActionState.SUCCEEDED)
                    Log.i(TAG, "phone_action_finished runId=${control.runId} actionId=${control.actionId} status=succeeded")
                    actionResult(requestId)
                } catch (_: CancellationException) {
                    actionLedger.complete(control.runId, control.actionId, PhoneActionState.CANCELLED)
                    Log.i(TAG, "phone_action_finished runId=${control.runId} actionId=${control.actionId} status=cancelled")
                    errorResult(requestId, "手机操作已取消。")
                } catch (error: Exception) {
                    actionLedger.complete(control.runId, control.actionId, PhoneActionState.FAILED)
                    Log.w(TAG, "phone_action_finished runId=${control.runId} actionId=${control.actionId} status=failed")
                    errorResult(requestId, error.message ?: "执行失败")
                }
            }
        } catch (_: CancellationException) {
            actionLedger.complete(control.runId, control.actionId, PhoneActionState.CANCELLED)
            Log.i(TAG, "phone_action_finished runId=${control.runId} actionId=${control.actionId} status=cancelled")
            errorResult(requestId, "手机操作已取消。")
        } finally {
            job?.let { unregisterActiveAction(control.runId, it) }
        }
    }

    /**
     * 进入设备互斥区、调用外部副作用前后都执行同一检查。shell 本身不能被
     * 强制回滚，但此处保证取消或过期后不会再发起下一条操作。
     */
    private suspend fun ensureActionStillAllowed(control: PhoneActionControl) {
        currentCoroutineContext().ensureActive()
        if (actionLedger.isRunCancelled(control.runId)) {
            throw CancellationException("phone run cancelled: ${control.runId}")
        }
        if (control.deadlineEpochMs < System.currentTimeMillis()) {
            error("手机操作指令已过期。")
        }
    }

    private fun registerActiveAction(runId: String, job: Job) {
        activeActionJobs.getOrPut(runId) { linkedSetOf() }.add(job)
    }

    private fun unregisterActiveAction(runId: String, job: Job) {
        val jobs = activeActionJobs[runId] ?: return
        jobs.remove(job)
        if (jobs.isEmpty()) activeActionJobs.remove(runId)
    }

    private fun cancelActiveActions(runId: String) {
        activeActionJobs[runId]?.toList()?.forEach { job ->
            job.cancel(CancellationException("phone run cancelled: $runId"))
        }
    }

    private suspend fun executeRequest(message: String, data: JSONObject?) {
        currentCoroutineContext().ensureActive()
        when (message) {
            "observe" -> Unit
            "launch" -> runShell("monkey -p ${data.requiredString("package")} -c android.intent.category.LAUNCHER 1")
            "tap" -> {
                val x = ScreenScaleState.toRealX(data.requiredInt("x"))
                val y = ScreenScaleState.toRealY(data.requiredInt("y"))
                runShell("input tap $x $y")
            }
            "type" -> typeText(data.requiredString("text"))
            "swipe" -> {
                val startX = ScreenScaleState.toRealX(data.requiredInt("startX"))
                val startY = ScreenScaleState.toRealY(data.requiredInt("startY"))
                val endX = ScreenScaleState.toRealX(data.requiredInt("endX"))
                val endY = ScreenScaleState.toRealY(data.requiredInt("endY"))
                runShell("input swipe $startX $startY $endX $endY 300")
            }

            "longPress" -> {
                val x = ScreenScaleState.toRealX(data.requiredInt("x"))
                val y = ScreenScaleState.toRealY(data.requiredInt("y"))
                runShell("input swipe $x $y $x $y 700")
            }

            "doubleTap" -> {
                val x = ScreenScaleState.toRealX(data.requiredInt("x"))
                val y = ScreenScaleState.toRealY(data.requiredInt("y"))
                runShell("input tap $x $y")
                delay(100.milliseconds)
                currentCoroutineContext().ensureActive()
                runShell("input tap $x $y")
            }

            "keyevent" -> runShell("input keyevent ${data.requiredInt("keyevent")}")
            "interact" -> waitForUserInteraction(data?.optString("message"))
            else -> error("未知手机协助指令: $message")
        }
    }

    private suspend fun typeText(text: String) {
        currentCoroutineContext().ensureActive()
        if (text.isEmpty()) return
        val beforeUi = AdbAccessibilityService.dumpUiTree()
        val diagnostics = mutableListOf<String>()

        if (isAsciiFriendlyInput(text)) {
            val encoded = encodeForAdbInputText(text)
            val asciiResult = runShellForResult("input text ${shellQuote(encoded)}")
            currentCoroutineContext().ensureActive()
            if (asciiResult.isSuccess) {
                if (verifyTypedText(beforeUi, text)) return
                diagnostics += "input text 执行成功但未观测到输入结果"
            } else {
                diagnostics += asciiResult.stderr.ifBlank { "input text 执行失败" }
            }
        }

        ensureAdbKeyboardActive()
        val broadcastResult = runShellForResult("am broadcast -a ADB_INPUT_TEXT --es msg ${shellQuote(text)}")
        currentCoroutineContext().ensureActive()
        if (!broadcastResult.isSuccess) {
            diagnostics += broadcastResult.stderr.ifBlank { "输入通道发送失败" }
        } else if (verifyTypedText(beforeUi, text)) {
            return
        } else {
            diagnostics += "输入通道已发送但未观测到输入结果"
        }

        error(diagnostics.joinToString("；").ifBlank { "输入未生效" })
    }

    private suspend fun ensureAdbKeyboardActive() {
        val imeResult = runShellForResult("settings get secure default_input_method")
        if (!imeResult.isSuccess) {
            error(imeResult.stderr.ifBlank { "无法检查当前输入法。" })
        }
        val currentIme = imeResult.stdout.trim()
        if (!currentIme.contains(ADB_KEYBOARD_IME_ID)) {
            error(
                "当前输入方式暂不支持这段文本。" +
                        "请先切换到 Echo 支持的输入方式后重试。",
            )
        }
    }

    private fun isAsciiFriendlyInput(text: String): Boolean {
        return text.all { it == '\n' || it == '\t' || (it.code in 0x20..0x7E) }
    }

    private fun encodeForAdbInputText(text: String): String {
        return buildString(text.length) {
            text.forEach { ch ->
                when (ch) {
                    ' ', '\n', '\t' -> append("%s")
                    '%' -> append("\\%")
                    else -> append(ch)
                }
            }
        }
    }

    private fun shellQuote(value: String): String {
        return "'" + value.replace("'", "'\"'\"'") + "'"
    }

    private suspend fun verifyTypedText(beforeUi: String?, expectedText: String): Boolean {
        if (expectedText.isBlank()) return true
        delay(250.milliseconds)
        val afterUi = AdbAccessibilityService.dumpUiTree() ?: return false
        val expectedEscaped = xmlEscape(expectedText)
        if (afterUi.contains("text=\"$expectedEscaped\"")) return true

        val beforeFocusedText = focusedNodeText(beforeUi)
        val afterFocusedText = focusedNodeText(afterUi)
        return beforeFocusedText != null &&
                afterFocusedText != null &&
                beforeFocusedText != afterFocusedText
    }

    private fun focusedNodeText(uiXml: String?): String? {
        if (uiXml.isNullOrBlank()) return null
        val match = FOCUSED_NODE_TEXT_REGEX.find(uiXml) ?: return null
        return match.groupValues.getOrNull(1)
    }

    private fun xmlEscape(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "&#10;")
    }

    private suspend fun runShell(command: String) {
        currentCoroutineContext().ensureActive()
        val result = runShellForResult(command)
        currentCoroutineContext().ensureActive()
        if (!result.isSuccess) {
            error(result.stderr.ifBlank { "命令执行失败(${result.exitCode})" })
        }
    }

    private suspend fun runShellForResult(command: String) =
        withContext(Dispatchers.IO) { executor.execute(command) }

    private suspend fun waitForUserInteraction(message: String?) {
        if (!overlay.show()) {
            error("悬浮窗权限未授权，无法显示用户交互提示。")
        }
        val deferred = CompletableDeferred<Unit>()
        overlay.waitForInteraction(message) {
            deferred.complete(Unit)
        }
        try {
            deferred.await()
        } finally {
            overlay.hide()
        }
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
                    .put("screenshotMimeType", JSONObject.NULL)
                    .put("ui", AdbAccessibilityService.dumpUiTree() ?: JSONObject.NULL)
                    .put("currentPackage", AdbAccessibilityService.currentPackageName() ?: JSONObject.NULL)
                    .put("activity", AdbAccessibilityService.currentActivityName() ?: JSONObject.NULL)
                    .put("deviceId", DeviceIdStore.deviceId() ?: JSONObject.NULL)
            }
    }

    private fun send(envelope: JSONObject) {
        socketTracker.current?.send("$envelope\n")
    }

    private fun reconnectLater() {
        if (closedByClient || reconnectJob?.isActive == true) return
        reconnectJob = serviceScope.launch {
            delay(3_000.milliseconds)
            reconnectJob = null
            if (closedByClient) return@launch
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

    private fun showDebugOverlay(status: String, detail: String? = null) {
        if (!BuildConfig.SHOW_TECH_DEBUG_UI) return
        overlay.update(status, detail)
        overlay.showBriefly(3000)
    }

    private fun userVisibleStatus(status: String, detail: String?): String {
        if (!BuildConfig.SHOW_TECH_DEBUG_UI || detail.isNullOrBlank()) return status
        return "$status: $detail"
    }

    private fun buildNotification(content: String): Notification {
        val stopIntent = Intent(this, AdbWebSocketService::class.java).apply {
            action = ACTION_STOP_ALL
        }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_echo_face)
            .setContentTitle(getString(R.string.assist_notification_title))
            .setContentText(content)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, getString(R.string.assist_notification_stop), stopPending)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.assist_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val NOTIFICATION_ID = 3001
        private const val NOTIFICATION_CHANNEL_ID = "adb_tool_connection"
        private const val ACTION_STOP_ALL = "com.example.blueheartv.STOP_ALL_SERVICES"
        private const val ACTION_LEDGER_PREFS = "phone_action_ledger"
        private const val ADB_KEYBOARD_IME_ID = "com.android.adbkeyboard/.AdbIME"
        private const val TAG = "AdbWebSocketService"
        private val FOCUSED_NODE_TEXT_REGEX =
            Regex("""<node(?=[^>]*focused=\"true\")[^>]*text=\"([^\"]*)\"[^>]*>""")

        fun start(context: Context) {
            context.startForegroundService(Intent(context, AdbWebSocketService::class.java))
        }
    }
}

private data class PhoneActionControl(
    val runId: String,
    val actionId: String,
    val deadlineEpochMs: Long,
)

private fun JSONObject?.phoneActionControl(): PhoneActionControl? {
    if (this == null) return null
    val runId = optString("runId").trim()
    val actionId = optString("actionId").trim()
    if (runId.isBlank() || actionId.isBlank() || !has("deadlineEpochMs") || isNull("deadlineEpochMs")) {
        return null
    }
    val deadline = optLong("deadlineEpochMs", 0L)
    return PhoneActionControl(runId, actionId, deadline.takeIf { it > 0L } ?: return null)
}

private fun JSONObject?.requiredString(key: String): String {
    require(this != null && has(key) && !isNull(key)) { "$key 缺失。" }
    return getString(key)
}

private fun JSONObject?.requiredInt(key: String): Int {
    require(this != null && has(key) && !isNull(key)) { "$key 缺失。" }
    return getInt(key)
}

private fun Request.Builder.addDeviceIdHeader(): Request.Builder {
    DeviceIdStore.deviceId()?.trim()?.takeIf { it.isNotBlank() }?.let {
        addHeader("X-Device-Id", it)
    }
    return this
}
