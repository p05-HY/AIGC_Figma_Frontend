package com.example.blueheartv.chat

import android.util.Log
import com.example.blueheartv.model.Message
import com.example.blueheartv.model.MessageDeliveryState
import com.example.blueheartv.viewmodel.DEFAULT_SESSION_TITLE
import com.example.blueheartv.viewmodel.truncateTitle
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit

class AgentServerClient(
    private val configProvider: () -> AgentServerConfig,
    private val client: OkHttpClient = sharedClient,
) {
    fun adbWebSocketUrl(): String {
        val base = normalizedBaseUrl()
        // 协议：deviceId 作为 URL 路径段携带（ws://host/adb/{deviceId}），后端据此区分设备。
        // 是否携带由 BuildConfig.DEVICE_ID_IN_PATH 开关控制；关闭或 deviceId 不可用时
        // pathSegment() 返回空串，joinPath 自动过滤，URL 退化为无参 /adb。
        val deviceId = DeviceIdStore.pathSegment()
        val builder = base.newBuilder()
            .encodedPath(joinPath(base.encodedPath, ApiPaths.ADB_WS, deviceId))
        return builder.build().toString()
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")
    }

    fun createThread(titleHint: String?): RemoteChatThread {
        val body = JSONObject().apply {
            put(
                "metadata",
                JSONObject().apply {
                    put("graph_id", AgentServerConfigStore.ASSISTANT_ID)
                    titleHint?.trim()?.takeIf { it.isNotBlank() }?.let { put("title", truncateTitle(it)) }
                },
            )
        }
        val json = postJson(url(ApiPaths.THREADS), body)
        val id = json.optString("thread_id")
            .ifBlank { json.optString("threadId") }
            .ifBlank { error("服务未返回会话编号") }
        return RemoteChatThread(
            id = id,
            title = titleHint?.let { truncateTitle(it) } ?: DEFAULT_SESSION_TITLE,
            updatedAtMillis = System.currentTimeMillis(),
            messages = emptyList(),
        )
    }

    fun searchThreads(limit: Int): List<RemoteChatThread> {
        val body = JSONObject().apply {
            put("limit", limit)
            put("offset", 0)
        }
        val raw = executeString(postRequest(url(ApiPaths.THREADS, ApiPaths.SEARCH), body))
        val threads = parseThreadSearch(raw)
            .filter { thread ->
                thread.metadata.optString("graph_id") in setOf(
                    "",
                    AgentServerConfigStore.ASSISTANT_ID
                )
            }
            .take(limit)

        return threads.map { thread ->
            val stateMessages = runCatching { getThreadStateMessages(thread.id) }.getOrDefault(emptyList())
            RemoteChatThread(
                id = thread.id,
                title = thread.title(stateMessages),
                updatedAtMillis = thread.updatedAtMillis,
                messages = stateMessages,
            )
        }
    }

    fun getThread(threadId: String): RemoteChatThread {
        val state = getThreadState(threadId)
        val messages = parseMessages(state.optJSONObject("values")?.optJSONArray("messages"))
        return RemoteChatThread(
            id = threadId,
            title = titleFromMessages(messages, threadId),
            updatedAtMillis = System.currentTimeMillis(),
            messages = messages,
        )
    }

    fun updateThreadTitle(threadId: String, title: String) {
        val body = JSONObject().apply {
            put("metadata", JSONObject().put("title", truncateTitle(title)))
        }
        runCatching { patchJson(url(ApiPaths.THREADS, threadId), body) }
    }

    fun deleteThread(threadId: String) {
        runCatching { executeString(requestBuilder(url(ApiPaths.THREADS, threadId)).delete().build()) }
    }

    fun transcribeVoice(
        audio: ByteArray,
        audioFormat: String = "pcm",
        sampleRate: Int = 16000,
        language: String = "zh-CN",
    ): VoiceTranscriptionResult {
        require(audio.isNotEmpty()) { "录音内容为空" }
        val normalizedFormat = audioFormat.trim().ifBlank { "pcm" }.lowercase()
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("format", normalizedFormat)
            .addFormDataPart("sampleRate", sampleRate.toString())
            .addFormDataPart("language", language)
            .addFormDataPart(
                "audio",
                "speech.$normalizedFormat",
                audio.toRequestBody("audio/$normalizedFormat".toMediaType()),
            )
            .build()
        val request = requestBuilder(
            url(ApiPaths.MOBILE, ApiPaths.ASR, ApiPaths.TRANSCRIBE),
            jsonContentType = false,
        )
            .post(body)
            .build()
        val payload = JSONObject(executeString(request))
        return VoiceTranscriptionResult(
            text = payload.optString("text").trim(),
            provider = payload.optString("provider").ifBlank { "unknown" },
            requestId = payload.optString("requestId").ifBlank { null },
            durationMs = if (payload.isNull("durationMs")) null else payload.optLong("durationMs"),
        )
    }

    fun streamRun(
        threadId: String,
        prompt: ChatPrompt,
        mobileRunId: String? = null,
        onEvent: (ChatStreamEvent) -> Unit,
    ) {
        val body = JSONObject().apply {
            put("assistant_id", AgentServerConfigStore.ASSISTANT_ID)
            put("input", JSONObject().put("messages", JSONArray().put(prompt.toHumanMessageJson())))
            put(
                "stream_mode",
                JSONArray().apply { ApiPaths.STREAM_MODES.forEach { put(it) } },
            )
        }

        val requestBuilder = postRequest(
            url(ApiPaths.MOBILE, ApiPaths.THREADS, threadId, ApiPaths.RUNS, ApiPaths.STREAM),
            body,
        ).newBuilder()
        mobileRunId?.trim()?.takeIf { it.isNotBlank() }?.let {
            requestBuilder.header("X-Mobile-Run-Id", it)
        }
        val request = requestBuilder.build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val retryable = response.code == 408 || response.code == 429 || response.code in 500..599
                val message = response.body.string().take(MAX_ERROR_BODY)
                    .ifBlank { "服务请求失败(${response.code})" }
                onEvent(ChatStreamEvent.Error(message, retryable = retryable))
                return
            }
            parseSse(response.body.source(), onEvent)
        }
    }

    private fun getThreadStateMessages(threadId: String): List<Message> {
        val state = getThreadState(threadId)
        return parseMessages(state.optJSONObject("values")?.optJSONArray("messages"))
    }

    private fun getThreadState(threadId: String): JSONObject = getJson(url(ApiPaths.THREADS, threadId, ApiPaths.STATE))

    private fun parseSse(source: BufferedSource, onEvent: (ChatStreamEvent) -> Unit) {
        var eventName: String? = null
        val data = StringBuilder()
        var parseErrorCount = 0
        var shouldStop = false

        fun flush() {
            if (shouldStop) return
            val payload = data.toString().trim()
            if (payload.isNotEmpty() && payload != "[DONE]") {
                val safeEvent = parseSafeStreamEvent(eventName.orEmpty(), payload)
                if (safeEvent != null) {
                    onEvent(safeEvent)
                    if (safeEvent is ChatStreamEvent.StreamEof || safeEvent is ChatStreamEvent.Error) {
                        shouldStop = true
                    }
                } else if (eventName.isSafeFacadeEvent()) {
                    parseErrorCount += 1
                    logSseParseError(parseErrorCount, eventName)
                    if (parseErrorCount >= MAX_SSE_PARSE_ERRORS) {
                        onEvent(
                            ChatStreamEvent.Error(
                                "SSE 解析失败次数过多，请重试。",
                                retryable = true,
                            )
                        )
                        shouldStop = true
                    }
                }
            }
            eventName = null
            data.clear()
        }

        while (!source.exhausted() && !shouldStop) {
            val line = source.readUtf8Line() ?: break
            when {
                line.isEmpty() -> flush()
                line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                line.startsWith("data:") -> {
                    if (data.isNotEmpty()) data.append('\n')
                    data.append(line.removePrefix("data:").trim())
                }
            }
        }
        flush()
        if (!shouldStop) {
            // 网络 EOF 没有业务终态；ViewModel 会对已有 Trace 标记 interrupted。
            onEvent(ChatStreamEvent.StreamEof())
        }
    }

    fun cancelRun(threadId: String, runId: String, cancelSource: String = "user"): MobileRunCancellation {
        val request = postRequest(
            url(ApiPaths.MOBILE, ApiPaths.THREADS, threadId, ApiPaths.RUNS, runId, "cancel"),
            JSONObject().put("cancelSource", cancelSource),
        )
        val payload = JSONObject(executeString(request))
        val returnedRunId = payload.optString("runId").ifBlank { runId }
        val status = payload.optString("status").ifBlank { "unknown" }
        val accepted = status in setOf(
            "canceled_confirmed",
            "local_fenced_only",
            "backend_still_running",
            "backend_run_not_bound",
            "cancel_unavailable",
            "cancel_request_failed",
            "device_cancel_failed",
            "cancellation_requested",
            "not_bound_but_fenced",
            "already_terminal",
        )
        return MobileRunCancellation(
            runId = returnedRunId,
            accepted = accepted,
            status = status,
            backendStatus = payload.optString("backendStatus").ifBlank { "unknown" },
            deviceStatus = payload.optString("deviceStatus").ifBlank { null },
            localFenced = payload.optBoolean("localFenced", false),
            retryable = payload.optBoolean("retryable", false),
            cancelSource = payload.optString("cancelSource").ifBlank { cancelSource },
            terminalReason = payload.optString("terminalReason").ifBlank { null },
        )
    }

    fun getRunStatus(threadId: String, runId: String): MobileRunStatus {
        val request = requestBuilder(
            url(ApiPaths.MOBILE, ApiPaths.THREADS, threadId, ApiPaths.RUNS, runId, ApiPaths.STATUS),
        ).get().build()
        val payload = JSONObject(executeString(request))
        return MobileRunStatus(
            runId = payload.optString("runId").ifBlank { runId },
            localStatus = payload.optString("status").ifBlank { "unknown" },
            backendStatus = payload.optString("backendStatus").ifBlank { "unknown" },
            terminal = payload.optBoolean("terminal", false),
        )
    }

    fun confirmConfirmation(confirmationId: String): List<ChatStreamEvent> =
        postConfirmationAction(confirmationId, ApiPaths.CONFIRM)

    fun rejectConfirmation(confirmationId: String): List<ChatStreamEvent> =
        postConfirmationAction(confirmationId, ApiPaths.REJECT)

    fun takeOverConfirmation(confirmationId: String): List<ChatStreamEvent> =
        postConfirmationAction(confirmationId, ApiPaths.TAKE_OVER)

    fun confirmScenario3Demo(confirmationId: String): List<ChatStreamEvent> =
        postScenario3DemoAction(ApiPaths.CONFIRM, confirmationId)

    fun rejectScenario3Demo(confirmationId: String): List<ChatStreamEvent> =
        postScenario3DemoAction(ApiPaths.REJECT, confirmationId)

    fun takeOverScenario3Demo(confirmationId: String): List<ChatStreamEvent> =
        postScenario3DemoAction(ApiPaths.TAKE_OVER, confirmationId)

    private fun postConfirmationAction(confirmationId: String, action: String): List<ChatStreamEvent> {
        val payload = postJson(
            url(ApiPaths.MOBILE, ApiPaths.CONFIRMATIONS, confirmationId, action),
            JSONObject().put("confirmationId", confirmationId),
        )
        return payload.safeTaskProgressEvents()
    }

    private fun postScenario3DemoAction(action: String, confirmationId: String): List<ChatStreamEvent> {
        val payload = postJson(
            url(ApiPaths.MOBILE, ApiPaths.DEMO, ApiPaths.SCENARIO3, action),
            JSONObject().put("confirmationId", confirmationId),
        )
        return payload.safeTaskProgressEvents()
    }

    private fun String?.isSafeFacadeEvent(): Boolean =
        this?.lowercase() in setOf(
            "assistant.delta",
            "trace.v1",
            "task_progress",
            "needs_confirmation",
            "task_complexity",
            "stream.started",
            "stream.heartbeat",
            "stream.eof",
            "stream.error",
        )

    /** 诊断日志不能让一条本应被丢弃的坏 SSE 帧中断整个流。 */
    private fun logSseParseError(count: Int, eventName: String?) {
        runCatching {
            Log.w(TAG, "SSE parse error count=$count event=$eventName")
        }
    }

    private fun parseMessages(array: JSONArray?): List<Message> {
        if (array == null) return emptyList()
        return buildList {
            var pendingAssistant: Message? = null
            fun flushPendingAssistant() {
                pendingAssistant?.let(::add)
                pendingAssistant = null
            }

            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val type = obj.optString("type").ifBlank { obj.optString("role") }
                val isUser = type in setOf("human", "user")
                val isAssistant = type in setOf("ai", "assistant")
                if (!isUser && !isAssistant) continue
                val text = contentToText(obj.opt("content")).orEmpty()
                if (text.isBlank() && isAssistant) continue
                val message = Message(
                    id = obj.optString("id").ifBlank { "remote-${UUID.randomUUID()}" },
                    content = text,
                    isUser = isUser,
                    deliveryState = MessageDeliveryState.COMPLETED,
                )
                if (isUser) {
                    flushPendingAssistant()
                    add(message)
                } else {
                    pendingAssistant = message
                }
            }
            flushPendingAssistant()
        }
    }

    private fun ChatPrompt.toHumanMessageJson(): JSONObject {
        val text = text.trim()
        if (attachments.isEmpty()) {
            return JSONObject().put("type", "human").put("content", text)
        }
        return JSONObject()
            .put("type", "human")
            .put(
                "content",
                JSONArray().apply {
                    if (text.isNotBlank()) {
                        put(JSONObject().put("type", "text").put("text", text))
                    }
                    attachments.forEach { attachment ->
                        put(
                            JSONObject()
                                .put("type", "image_url")
                                .put(
                                    "image_url",
                                    JSONObject().put(
                                        "url",
                                        "data:${attachment.mimeType};base64,${attachment.base64Data}",
                                    ),
                                ),
                        )
                    }
                },
            )
    }

    private fun contentToText(content: Any?): String? {
        return when (content) {
            null, JSONObject.NULL -> null
            is String -> content
            is JSONArray -> buildString {
                for (i in 0 until content.length()) {
                    when (val item = content.opt(i)) {
                        is String -> append(item)
                        is JSONObject -> append(
                            item.optString("text")
                                .ifBlank { item.optString("content") },
                        )
                    }
                }
            }

            is JSONObject -> content.optString("text").ifBlank { content.optString("content") }
            else -> content.toString()
        }
    }

    private fun titleFromMessages(messages: List<Message>, fallbackId: String): String {
        val firstUser = messages.firstOrNull { it.isUser }?.content
        return firstUser?.let { truncateTitle(it) } ?: "对话 ${fallbackId.takeLast(8)}"
    }

    private fun RemoteThreadHeader.title(messages: List<Message>): String {
        return metadata.optString("title")
            .ifBlank { titleFromMessages(messages, id) }
    }

    private fun parseThreadSearch(raw: String): List<RemoteThreadHeader> {
        val array = when (val json = parseJson(raw)) {
            is JSONArray -> json
            is JSONObject -> json.optJSONArray("threads")
                ?: json.optJSONArray("items")
                ?: json.optJSONArray("results")
                ?: JSONArray()

            else -> JSONArray()
        }
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("thread_id")
                    .ifBlank { obj.optString("threadId") }
                if (id.isBlank()) continue
                add(
                    RemoteThreadHeader(
                        id = id,
                        metadata = obj.optJSONObject("metadata") ?: JSONObject(),
                        updatedAtMillis = parseMillis(
                            obj.optString("updated_at")
                                .ifBlank { obj.optString("updatedAt") }
                                .ifBlank { obj.optString("created_at") },
                        ),
                    ),
                )
            }
        }
    }

    private fun JSONObject.safeTaskProgressEvents(): List<ChatStreamEvent> {
        val array = optJSONArray("events") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val event = parseSafeStreamEvent("task_progress", item.toString())
                if (event != null) add(event)
            }
        }
    }

    private fun parseJson(raw: String): Any {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)
    }

    private fun parseMillis(value: String): Long {
        return runCatching { Instant.parse(value).toEpochMilli() }
            .getOrDefault(System.currentTimeMillis())
    }

    private fun getJson(url: HttpUrl): JSONObject = JSONObject(executeString(requestBuilder(url).get().build()))

    private fun postJson(url: HttpUrl, body: JSONObject): JSONObject = JSONObject(executeString(postRequest(url, body)))

    private fun patchJson(url: HttpUrl, body: JSONObject): JSONObject {
        val request = requestBuilder(url)
            .patch(body.toString().toRequestBody(JSON))
            .build()
        return JSONObject(executeString(request))
    }

    private fun postRequest(url: HttpUrl, body: JSONObject): Request {
        return requestBuilder(url)
            .post(body.toString().toRequestBody(JSON))
            .build()
    }

    private fun requestBuilder(url: HttpUrl, jsonContentType: Boolean = true): Request.Builder {
        val builder = Request.Builder()
            .url(url)
        if (jsonContentType) {
            builder.addHeader("Content-Type", "application/json")
        }
        configProvider().apiKey.takeIf { it.isNotBlank() }?.let {
            builder.addHeader("X-Api-Key", it)
        }
        DeviceIdStore.deviceId()?.trim()?.takeIf { it.isNotBlank() }?.let {
            builder.addHeader("X-Device-Id", it)
        }
        return builder
    }

    private fun executeString(request: Request): String {
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw response.toApiException()
                response.body.string()
            }
        } catch (error: IOException) {
            throw error.toApiException()
        }
    }

    private fun Response.toApiException(): IOException {
        val detail = body.string().take(MAX_ERROR_BODY)
        return IOException(detail.ifBlank { "HTTP $code" })
    }

    private fun IOException.toApiException(): IOException {
        val retryable = this is SocketTimeoutException ||
                this is ConnectException ||
                this is UnknownHostException ||
                message?.contains("timeout", ignoreCase = true) == true
        return IOException(if (retryable) "服务网络异常：${message}" else message)
    }

    private fun normalizedBaseUrl(): HttpUrl {
        val raw = configProvider().baseUrl.trim()
        require(raw.isNotBlank()) { "请先配置服务地址" }
        val normalized = when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            raw.startsWith("ws://") -> "http://${raw.removePrefix("ws://")}"
            raw.startsWith("wss://") -> "https://${raw.removePrefix("wss://")}"
            else -> "http://$raw"
        }
        return normalized.trimEnd('/').toHttpUrl()
    }

    private fun url(vararg segments: String): HttpUrl {
        val base = normalizedBaseUrl()
        return base.newBuilder()
            .encodedPath(joinPath(base.encodedPath, *segments))
            .build()
    }

    private fun joinPath(basePath: String, vararg segments: String): String {
        val prefix = basePath.trimEnd('/').takeIf { it.isNotEmpty() && it != "/" }.orEmpty()
        return (listOf(prefix) + segments.map { it.trim('/') })
            .filter { it.isNotBlank() }
            .joinToString(prefix = "/", separator = "/")
    }

    private data class RemoteThreadHeader(
        val id: String,
        val metadata: JSONObject,
        val updatedAtMillis: Long,
    )

    private data class MessageDelta(
        val chunk: String,
        val invocationId: String?,
    )

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val MAX_ERROR_BODY = 500
        private const val MAX_SSE_PARSE_ERRORS = 3
        private const val TAG = "AgentServerClient"

        private val sharedClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
