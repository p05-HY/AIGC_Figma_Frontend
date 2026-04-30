package com.example.blueheartv.chat

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
        val builder = base.newBuilder()
            .encodedPath(joinPath(base.encodedPath, "adb"))
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
        val json = postJson(url("threads"), body)
        val id = json.optString("thread_id")
            .ifBlank { json.optString("threadId") }
            .ifBlank { error("Agent Server 未返回 thread_id") }
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
        val raw = executeString(postRequest(url("threads", "search"), body))
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
        runCatching { patchJson(url("threads", threadId), body) }
    }

    fun deleteThread(threadId: String) {
        runCatching { executeString(requestBuilder(url("threads", threadId)).delete().build()) }
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
                JSONArray()
                    .put("messages-tuple")
                    .put("updates")
                    .put("tasks")
                    .put("custom"),
            )
        }

        val request = postRequest(url("threads", threadId, "runs", "stream"), body)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val retryable = response.code == 408 || response.code == 429 || response.code in 500..599
                val message = response.body.string().take(MAX_ERROR_BODY)
                    .ifBlank { "Agent Server 请求失败(${response.code})" }
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

    private fun getThreadState(threadId: String): JSONObject = getJson(url("threads", threadId, "state"))

    private fun parseSse(source: BufferedSource, onEvent: (ChatStreamEvent) -> Unit) {
        var eventName: String? = null
        val data = StringBuilder()
        val activeNodes = linkedSetOf<String>()

        fun flush() {
            val payload = data.toString().trim()
            if (payload.isNotEmpty() && payload != "[DONE]") {
                handleSseEvent(eventName.orEmpty(), payload, activeNodes, onEvent)
            }
            eventName = null
            data.clear()
        }

        while (!source.exhausted()) {
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
        onEvent(ChatStreamEvent.Completed)
    }

    private fun handleSseEvent(
        eventName: String,
        payload: String,
        activeNodes: MutableSet<String>,
        onEvent: (ChatStreamEvent) -> Unit,
    ) {
        val json = runCatching { parseJson(payload) }.getOrNull() ?: return
        when {
            eventName.contains("messages", ignoreCase = true) -> {
                extractMessageDelta(json)?.takeIf { it.isNotEmpty() }?.let {
                    onEvent(ChatStreamEvent.TextDelta(it))
                }
                extractNodeName(json)?.let { markNodeActive(it, activeNodes, onEvent) }
            }

            eventName.contains("tasks", ignoreCase = true) -> {
                val node = extractNodeName(json) ?: return
                val completed = payload.contains("\"result\"") || payload.contains("\"error\"")
                if (completed) {
                    if (activeNodes.remove(node)) onEvent(ChatStreamEvent.ToolCallCompleted(node))
                } else {
                    markNodeActive(node, activeNodes, onEvent)
                }
            }

            eventName.contains("updates", ignoreCase = true) -> {
                val obj = json as? JSONObject ?: return
                for (key in obj.keys()) {
                    if (key !in activeNodes) onEvent(ChatStreamEvent.ToolCallStarted(key))
                    activeNodes.remove(key)
                    onEvent(ChatStreamEvent.ToolCallCompleted(key))
                }
            }

            eventName.contains("custom", ignoreCase = true) -> {
                val obj = json as? JSONObject ?: return
                val label = obj.optString("label").ifBlank { obj.optString("node") }.ifBlank { return }
                when (obj.optString("status")) {
                    "completed", "done", "end" -> {
                        activeNodes.remove(label)
                        onEvent(ChatStreamEvent.ToolCallCompleted(label))
                    }

                    else -> markNodeActive(label, activeNodes, onEvent)
                }
            }
        }
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

    private fun extractMessageDelta(json: Any): String? {
        if (json is JSONArray && json.length() > 0) {
            return extractMessageDelta(json.opt(0))
        }
        val obj = json as? JSONObject ?: return null
        val content = obj.opt("content")
            ?: obj.optJSONObject("kwargs")?.opt("content")
            ?: obj.optJSONObject("message")?.opt("content")
        return contentToText(content)
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

    private fun parseMessages(array: JSONArray?): List<Message> {
        if (array == null) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val type = obj.optString("type").ifBlank { obj.optString("role") }
                val isUser = type in setOf("human", "user")
                val isAssistant = type in setOf("ai", "assistant")
                if (!isUser && !isAssistant) continue
                val text = contentToText(obj.opt("content")).orEmpty()
                if (text.isBlank() && isAssistant) continue
                add(
                    Message(
                        id = obj.optString("id").ifBlank { "remote-${UUID.randomUUID()}" },
                        content = text,
                        isUser = isUser,
                        deliveryState = MessageDeliveryState.COMPLETED,
                    ),
                )
            }
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
        return IOException(if (retryable) "Agent Server 网络异常：${message}" else message)
    }

    private fun normalizedBaseUrl(): HttpUrl {
        val raw = configProvider().baseUrl.trim()
        require(raw.isNotBlank()) { "请先配置 Agent Server 地址" }
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

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private const val MAX_ERROR_BODY = 500

        private val sharedClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
