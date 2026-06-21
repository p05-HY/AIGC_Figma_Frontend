package com.example.blueheartv.chat

import android.util.Log
import com.example.blueheartv.model.Message
import com.example.blueheartv.model.MessageDeliveryState
import com.example.blueheartv.viewmodel.DEFAULT_SESSION_TITLE
import com.example.blueheartv.viewmodel.truncateTitle
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
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

    fun streamRun(
        threadId: String,
        prompt: ChatPrompt,
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

        val request = postRequest(url(ApiPaths.THREADS, threadId, ApiPaths.RUNS, ApiPaths.STREAM), body)
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
        val activeNodes = linkedSetOf<String>()
        var parseErrorCount = 0
        var shouldStop = false

        fun flush() {
            if (shouldStop) return
            val payload = data.toString().trim()
            if (payload.isNotEmpty() && payload != "[DONE]") {
                val ok = handleSseEvent(eventName.orEmpty(), payload, activeNodes, onEvent)
                if (!ok) {
                    parseErrorCount += 1
                    Log.w(TAG, "SSE parse error count=$parseErrorCount event=$eventName")
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
            onEvent(ChatStreamEvent.Completed)
        }
    }

    private fun handleSseEvent(
        eventName: String,
        payload: String,
        activeNodes: MutableSet<String>,
        onEvent: (ChatStreamEvent) -> Unit,
    ): Boolean {
        if (eventName.isBlank()) return true
        val json = runCatching { parseJson(payload) }.getOrNull() ?: return false
        when {
            eventName.contains("messages", ignoreCase = true) -> {
                extractMessageDelta(json)?.takeIf { it.chunk.isNotEmpty() }?.let {
                    onEvent(ChatStreamEvent.TextDelta(it.chunk, it.invocationId))
                }
                // AIMessage 携带的工具调用（入参）
                extractToolCalls(json).forEach { (name, args) ->
                    activeNodes.add(name)
                    onEvent(ChatStreamEvent.ToolCallStarted(name, args))
                }
                // ToolMessage 携带的工具执行结果（出参/错误）
                extractToolResult(json)?.let { result ->
                    activeNodes.remove(result.name)
                    if (result.isError) {
                        onEvent(ChatStreamEvent.ToolCallFailed(result.name, result.content))
                    } else {
                        onEvent(ChatStreamEvent.ToolCallCompleted(result.name, result.content))
                    }
                }
                extractNodeName(json)?.let { markNodeActive(it, activeNodes, onEvent) }
            }

            eventName.contains("tasks", ignoreCase = true) -> {
                val node = extractNodeName(json) ?: return true
                val taskResult = extractTaskOutcome(json)
                when {
                    taskResult?.isError == true -> {
                        activeNodes.remove(node)
                        onEvent(ChatStreamEvent.ToolCallFailed(node, taskResult.content))
                    }

                    taskResult != null -> {
                        activeNodes.remove(node)
                        onEvent(ChatStreamEvent.ToolCallCompleted(node, taskResult.content))
                    }

                    payload.contains("\"result\"") || payload.contains("\"error\"") -> {
                        activeNodes.remove(node)
                        onEvent(ChatStreamEvent.ToolCallCompleted(node))
                    }

                    else -> markNodeActive(node, activeNodes, onEvent)
                }
            }

            eventName.contains("updates", ignoreCase = true) -> {
                val obj = json as? JSONObject ?: return true
                for (key in obj.keys()) {
                    if (key !in activeNodes) onEvent(ChatStreamEvent.ToolCallStarted(key))
                    activeNodes.remove(key)
                    onEvent(ChatStreamEvent.ToolCallCompleted(key))
                }
            }

            eventName.contains("custom", ignoreCase = true) -> {
                val obj = json as? JSONObject ?: return true
                when (obj.optString("type")) {
                    "task_complexity" -> {
                        onEvent(
                            ChatStreamEvent.TaskComplexity(
                                complexity = obj.optString("complexity").ifBlank { "simple" },
                                trackSteps = obj.optBoolean("trackSteps", false),
                                reason = obj.optString("reason"),
                                message = obj.optString("message").ifBlank { null },
                            )
                        )
                        return true
                    }

                    "task_progress" -> {
                        val label = obj.optString("label")
                            .ifBlank { obj.optString("toolName") }
                            .ifBlank { obj.optString("progressKey") }
                            .ifBlank { return true }
                        onEvent(
                            ChatStreamEvent.TaskProgress(
                                label = label,
                                status = obj.optString("status").ifBlank { "running" },
                                phase = obj.optString("phase").ifBlank { "agent" },
                                message = obj.optString("message").ifBlank { null },
                                toolName = obj.optString("toolName").ifBlank { null },
                                progressKey = obj.optString("progressKey").ifBlank { null },
                                currentStep = obj.optionalInt("currentStep"),
                                totalSteps = obj.optionalInt("totalSteps"),
                                completedSteps = obj.optJSONArray("completedSteps").toProgressSteps(),
                                error = obj.optString("error").ifBlank { null },
                            )
                        )
                        return true
                    }
                }
                val label = obj.optString("label").ifBlank { obj.optString("node") }.ifBlank { return true }
                when (obj.optString("status")) {
                    "completed", "done", "end" -> {
                        activeNodes.remove(label)
                        onEvent(ChatStreamEvent.ToolCallCompleted(label))
                    }

                    "failed", "error" -> {
                        activeNodes.remove(label)
                        onEvent(ChatStreamEvent.ToolCallFailed(label, obj.optString("error").ifBlank { null }))
                    }

                    else -> markNodeActive(label, activeNodes, onEvent)
                }
            }
        }
        return true
    }

    private fun markNodeActive(
        node: String,
        activeNodes: MutableSet<String>,
        onEvent: (ChatStreamEvent) -> Unit,
    ) {
        if (activeNodes.add(node)) {
            onEvent(ChatStreamEvent.ToolCallStarted(node))
        }
    }

    private fun extractMessageDelta(json: Any): MessageDelta? {
        if (json is JSONArray && json.length() > 0) {
            val chunk = json.optJSONObject(0) ?: return extractMessageDelta(json.opt(0))
            val content = chunk.opt("content")
                ?: chunk.optJSONObject("kwargs")?.opt("content")
                ?: chunk.optJSONObject("message")?.opt("content")
            val text = contentToText(content) ?: return null
            return MessageDelta(
                chunk = text,
                invocationId = extractInvocationId(chunk, json.optJSONObject(1)),
            )
        }
        val obj = json as? JSONObject ?: return null
        val content = obj.opt("content")
            ?: obj.optJSONObject("kwargs")?.opt("content")
            ?: obj.optJSONObject("message")?.opt("content")
        return contentToText(content)?.let {
            MessageDelta(
                chunk = it,
                invocationId = extractInvocationId(obj, obj.optJSONObject("metadata")),
            )
        }
    }

    private fun extractInvocationId(chunk: JSONObject, metadata: JSONObject?): String? {
        return chunk.optString("id")
            .ifBlank { chunk.optJSONObject("message")?.optString("id").orEmpty() }
            .ifBlank { metadata?.optString("checkpoint_ns").orEmpty() }
            .ifBlank { metadata?.optString("langgraph_checkpoint_ns").orEmpty() }
            .ifBlank {
                metadata?.let {
                    val node = it.optString("langgraph_node")
                    val step = it.optString("langgraph_step")
                    if (node.isNotBlank() && step.isNotBlank()) "$node:$step" else ""
                }.orEmpty()
            }
            .ifBlank { null }
    }

    private fun extractNodeName(json: Any): String? {
        if (json is JSONArray) {
            for (i in 0 until json.length()) {
                extractNodeName(json.opt(i))?.let { return it }
            }
        }
        val obj = json as? JSONObject ?: return null
        return obj.optString("langgraph_node")
            .ifBlank { obj.optString("node") }
            .ifBlank { obj.optString("name") }
            .ifBlank { obj.optJSONObject("metadata")?.optString("langgraph_node").orEmpty() }
            .ifBlank { null }
    }

    private fun JSONObject.optionalInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optInt(key)
    }

    private fun JSONArray?.toProgressSteps(): List<ChatStreamEvent.TaskProgressStep> {
        if (this == null || length() == 0) return emptyList()
        val steps = mutableListOf<ChatStreamEvent.TaskProgressStep>()
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            val name = obj.optString("name")
                .ifBlank { obj.optString("label") }
                .ifBlank { continue }
            steps += ChatStreamEvent.TaskProgressStep(
                index = obj.optionalInt("index"),
                name = name,
                status = obj.optString("status").ifBlank { "completed" },
            )
        }
        return steps
    }

    private data class ToolResult(
        val name: String,
        val content: String?,
        val isError: Boolean,
    )

    /** 取 messages-tuple 元组中的消息体（数组首元素或对象本身）。 */
    private fun messageObject(json: Any): JSONObject? {
        if (json is JSONArray) {
            return json.optJSONObject(0) ?: json.opt(0) as? JSONObject
        }
        return json as? JSONObject
    }

    /** 从 AIMessage(chunk) 提取工具调用入参，返回 (工具名, 入参JSON文本) 列表。防御式多路径兜底。 */
    private fun extractToolCalls(json: Any): List<Pair<String, String?>> {
        val obj = messageObject(json) ?: return emptyList()
        val kwargs = obj.optJSONObject("kwargs")
        val calls = obj.optJSONArray("tool_calls")
            ?: kwargs?.optJSONArray("tool_calls")
            ?: obj.optJSONArray("tool_call_chunks")
            ?: kwargs?.optJSONArray("tool_call_chunks")
            ?: return emptyList()
        return buildList {
            for (i in 0 until calls.length()) {
                val call = calls.optJSONObject(i) ?: continue
                val name = call.optString("name").ifBlank { call.optString("function") }
                if (name.isBlank()) continue
                val argsText = when (val args = call.opt("args")) {
                    null, JSONObject.NULL -> null
                    is String -> args.ifBlank { null }
                    else -> args.toString()
                }
                add(name to argsText)
            }
        }
    }

    /** 从 ToolMessage 提取工具执行结果（出参/错误）。仅当消息类型为 tool 时返回。 */
    private fun extractToolResult(json: Any): ToolResult? {
        val obj = messageObject(json) ?: return null
        val kwargs = obj.optJSONObject("kwargs")
        val type = obj.optString("type")
            .ifBlank { obj.optString("role") }
            .ifBlank { kwargs?.optString("type").orEmpty() }
            .lowercase()
        if (type != "tool" && !type.contains("toolmessage")) return null
        val name = obj.optString("name")
            .ifBlank { kwargs?.optString("name").orEmpty() }
            .ifBlank { "tool" }
        val content = contentToText(obj.opt("content") ?: kwargs?.opt("content"))?.ifBlank { null }
        val status = obj.optString("status").ifBlank { kwargs?.optString("status").orEmpty() }
        return ToolResult(name, content, status.equals("error", ignoreCase = true))
    }

    /** 从 tasks 流提取任务结果/错误内容。 */
    private fun extractTaskOutcome(json: Any): ToolResult? {
        fun jsonToText(value: Any?): String? = when (value) {
            null, JSONObject.NULL -> null
            is String -> value.ifBlank { null }
            else -> contentToText(value)?.ifBlank { null } ?: value.toString()
        }

        fun scan(obj: JSONObject): ToolResult? {
            val name = obj.optString("name")
                .ifBlank { obj.optString("langgraph_node") }
                .ifBlank { "task" }
            if (obj.has("error") && !obj.isNull("error")) {
                return ToolResult(name, jsonToText(obj.opt("error")), true)
            }
            if (obj.has("result") && !obj.isNull("result")) {
                return ToolResult(name, jsonToText(obj.opt("result")), false)
            }
            return null
        }

        return when (json) {
            is JSONObject -> scan(json)
            is JSONArray -> {
                for (i in 0 until json.length()) {
                    (json.opt(i) as? JSONObject)?.let { scan(it)?.let { r -> return r } }
                }
                null
            }

            else -> null
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

    private fun requestBuilder(url: HttpUrl): Request.Builder {
        val builder = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
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
