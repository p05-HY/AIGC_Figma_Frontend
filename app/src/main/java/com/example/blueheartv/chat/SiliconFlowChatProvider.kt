package com.example.blueheartv.chat

import java.io.IOException
import com.example.blueheartv.telemetry.AppEventLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class SiliconFlowChatProvider(
    private val apiKeyProvider: () -> String?,
    private val modelProvider: () -> String,
    private val client: OkHttpClient = OkHttpClient.Builder().build(),
) : ChatProvider {

    override suspend fun streamReply(
        prompt: String,
        onEvent: (ChatStreamEvent) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val normalizedPrompt = prompt.trim()
        if (normalizedPrompt.isEmpty()) {
            onEvent(ChatStreamEvent.Error("输入为空", retryable = false))
            return@withContext
        }

        val apiKey = apiKeyProvider()?.trim().orEmpty()
        if (apiKey.isEmpty()) {
            onEvent(ChatStreamEvent.Error("缺少 SiliconFlow API Key", retryable = false))
            return@withContext
        }

        val model = modelProvider().trim().ifEmpty { DEFAULT_MODEL }
        AppEventLogger.info("chat_request_start", "provider=siliconflow model=$model")

        val requestBody = JSONObject().apply {
            put("model", model)
            put("stream", true)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("content", normalizedPrompt)
                    },
                ),
            )
        }

        val request = Request.Builder()
            .url(CHAT_COMPLETIONS_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(
                requestBody
                    .toString()
                    .toRequestBody("application/json; charset=utf-8".toMediaType()),
            )
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorText = response.body?.string().orEmpty()
                    val retryable = response.code >= 500
                    val message = if (errorText.isNotBlank()) {
                        "SiliconFlow 请求失败(${response.code})：$errorText"
                    } else {
                        "SiliconFlow 请求失败(${response.code})"
                    }
                    AppEventLogger.warning("chat_request_http_error", message)
                    onEvent(ChatStreamEvent.Error(message, retryable = retryable))
                    return@use
                }

                val source = response.body?.source()
                if (source == null) {
                    onEvent(ChatStreamEvent.Error("SiliconFlow 响应为空"))
                    return@use
                }

                var hasDelta = false
                while (!source.exhausted()) {
                    val rawLine = source.readUtf8Line() ?: break
                    val line = rawLine.trim()
                    if (!line.startsWith("data:")) continue

                    val payload = line.removePrefix("data:").trim()
                    if (payload.isEmpty()) continue

                    if (payload == "[DONE]") {
                        AppEventLogger.info("chat_request_complete", "provider=siliconflow via_done=true")
                        onEvent(ChatStreamEvent.Completed)
                        return@use
                    }

                    val json = runCatching { JSONObject(payload) }.getOrNull() ?: continue
                    val choices = json.optJSONArray("choices") ?: continue
                    if (choices.length() == 0) continue

                    val choice = choices.optJSONObject(0) ?: continue
                    val delta = choice.optJSONObject("delta")
                    val content = delta?.optString("content").orEmpty()
                    if (content.isNotEmpty()) {
                        hasDelta = true
                        onEvent(ChatStreamEvent.TextDelta(content))
                    }

                    val finishReason = choice.optString("finish_reason")
                    if (finishReason.isNotEmpty() && finishReason != "null") {
                        AppEventLogger.info(
                            "chat_request_complete",
                            "provider=siliconflow finishReason=$finishReason",
                        )
                        onEvent(ChatStreamEvent.Completed)
                        return@use
                    }
                }

                if (hasDelta) {
                    AppEventLogger.info("chat_request_complete", "provider=siliconflow via_stream_end=true")
                    onEvent(ChatStreamEvent.Completed)
                } else {
                    onEvent(ChatStreamEvent.Error("SiliconFlow 未返回有效文本"))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (io: IOException) {
            AppEventLogger.error("chat_request_network_error", io.message ?: "network_error", io)
            onEvent(ChatStreamEvent.Error("网络异常：${io.message ?: "连接失败"}"))
        } catch (error: Exception) {
            AppEventLogger.error("chat_request_exception", error.message ?: "unknown_error", error)
            onEvent(ChatStreamEvent.Error("请求异常：${error.message ?: "未知错误"}"))
        }
    }

    companion object {
        private const val CHAT_COMPLETIONS_URL = "https://api.siliconflow.cn/v1/chat/completions"
        private const val DEFAULT_MODEL = "deepseek-ai/DeepSeek-R1-Distill-Qwen-32B"

        fun createOrNull(): ChatProvider? {
            val snapshot = SiliconFlowConfigStore.snapshot()
            if (snapshot.apiKey.isNullOrBlank()) return null
            return SiliconFlowChatProvider(
                apiKeyProvider = { SiliconFlowConfigStore.snapshot().apiKey },
                modelProvider = { SiliconFlowConfigStore.snapshot().model },
            )
        }
    }
}
