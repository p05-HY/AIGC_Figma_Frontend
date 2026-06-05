package com.example.blueheartv.system

import com.example.blueheartv.chat.DeviceIdStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

class SystemWebSocketClient(
    private val scope: CoroutineScope,
    private val config: SystemConfig,
    private val handler: SystemProtocolHandler,
    private val onStatus: (String, String?) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var pingJob: Job? = null
    private val incomingBuffer = StringBuilder()

    @Volatile
    private var closedByClient = false

    fun connect() {
        closedByClient = false
        val request = Request.Builder()
            .url(buildSocketRequestUrl())
            .build()
        onStatus("连接中", request.url.toString())
        webSocket = client.newWebSocket(request, listener)
    }

    fun disconnect() {
        closedByClient = true
        pingJob?.cancel()
        webSocket?.close(1000, "client stop")
        webSocket = null
        client.dispatcher.executorService.shutdown()
    }

    private fun startHeartbeat() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (true) {
                delay(20_000.milliseconds)
                send(SystemProtocolHandler.request("ping", null, null))
            }
        }
    }

    private fun send(envelope: JSONObject) {
        webSocket?.send("$envelope\n")
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            onStatus("已连接", "等待系统接口请求")
            startHeartbeat()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            incomingBuffer.append(text)
            while (true) {
                val newlineIndex = incomingBuffer.indexOf("\n")
                if (newlineIndex < 0) break
                val raw = incomingBuffer.substring(0, newlineIndex).trim()
                incomingBuffer.delete(0, newlineIndex + 1)
                if (raw.isBlank()) continue
                scope.launch { handleLine(raw) }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            onStatus("连接关闭", "$code $reason")
            reconnectLater()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            onStatus("连接异常", t.message)
            reconnectLater()
        }
    }

    private suspend fun handleLine(raw: String) {
        val envelope = JSONObject(raw)
        val type = envelope.optString("type")
        val message = envelope.optString("message")
        if (type == "response" && message == "pong") {
            onStatus("心跳正常", null)
            return
        }
        if (type == "request") {
            val response = handler.handleRequest(envelope)
            send(response)
            onStatus("已响应", message)
        }
    }

    private fun reconnectLater() {
        if (closedByClient) return
        scope.launch {
            delay(3_000.milliseconds)
            connect()
        }
    }

    private fun buildSocketRequestUrl(): String {
        val base = config.serverBaseUrl.trim()
        require(base.isNotBlank()) { "System WebSocket URL 不能为空。" }
        val normalizedBase = when {
            base.startsWith("ws://") -> "http://${base.removePrefix("ws://")}"
            base.startsWith("wss://") -> "https://${base.removePrefix("wss://")}"
            base.startsWith("http://") || base.startsWith("https://") -> base
            else -> "http://$base"
        }
        val httpUrl = normalizedBase.toHttpUrl()
        // 协议：deviceId 作为 URL 路径段携带（ws://host/system/{deviceId}），后端据此区分设备。
        // 是否携带由 BuildConfig.DEVICE_ID_IN_PATH 开关控制；关闭或 deviceId 不可用时退化为无参 /system。
        val deviceId = DeviceIdStore.pathSegment()
        val path = if (deviceId.isBlank()) PROTOCOL_PATH else "$PROTOCOL_PATH/$deviceId"
        return httpUrl.newBuilder()
            .encodedPath(path)
            .build()
            .toString()
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
    }

    private companion object {
        private const val PROTOCOL_PATH = "/system"
    }
}
