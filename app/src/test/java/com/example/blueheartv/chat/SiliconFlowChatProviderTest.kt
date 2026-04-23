package com.example.blueheartv.chat

import com.example.blueheartv.model.Message
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test

class SiliconFlowChatProviderTest {

    @Test
    fun streamReply_emptyPrompt_returnsNonRetryableError() = runTest {
        val events = mutableListOf<ChatStreamEvent>()
        val provider = SiliconFlowChatProvider(
            apiKeyProvider = { "key" },
            modelProvider = { "model" },
            client = passthroughClient(),
        )

        provider.streamReply(listOf(Message("1", "   ", isUser = true))) { events.add(it) }

        val error = events.single() as ChatStreamEvent.Error
        assertEquals(false, error.retryable)
        assertTrue(error.message.contains("输入为空"))
    }

    @Test
    fun streamReply_missingKey_returnsNonRetryableError() = runTest {
        val events = mutableListOf<ChatStreamEvent>()
        val provider = SiliconFlowChatProvider(
            apiKeyProvider = { "   " },
            modelProvider = { "model" },
            client = passthroughClient(),
        )

        provider.streamReply(userMessages("hello")) { events.add(it) }

        val error = events.single() as ChatStreamEvent.Error
        assertEquals(false, error.retryable)
        assertTrue(error.message.contains("缺少 SiliconFlow API Key"))
    }

    @Test
    fun streamReply_timeoutError_isRetryable() = runTest {
        val events = mutableListOf<ChatStreamEvent>()
        val client = OkHttpClient.Builder()
            .addInterceptor { throw SocketTimeoutException("timeout") }
            .build()
        val provider = SiliconFlowChatProvider(
            apiKeyProvider = { "key" },
            modelProvider = { "model" },
            client = client,
        )

        provider.streamReply(userMessages("hello")) { events.add(it) }

        val error = events.single() as ChatStreamEvent.Error
        assertEquals(true, error.retryable)
        assertTrue(error.message.contains("超时"))
    }

    @Test
    fun streamReply_unknownHost_isRetryable() = runTest {
        val events = mutableListOf<ChatStreamEvent>()
        val client = OkHttpClient.Builder()
            .addInterceptor { throw UnknownHostException("dns fail") }
            .build()
        val provider = SiliconFlowChatProvider(
            apiKeyProvider = { "key" },
            modelProvider = { "model" },
            client = client,
        )

        provider.streamReply(userMessages("hello")) { events.add(it) }

        val error = events.single() as ChatStreamEvent.Error
        assertEquals(true, error.retryable)
        assertTrue(error.message.contains("网络异常"))
    }

    @Test
    fun streamReply_http429_isRetryable() = runTest {
        val events = mutableListOf<ChatStreamEvent>()
        val client = OkHttpClient.Builder()
            .addInterceptor(httpResponseInterceptor(code = 429, body = "rate limited"))
            .build()
        val provider = SiliconFlowChatProvider(
            apiKeyProvider = { "key" },
            modelProvider = { "model" },
            client = client,
        )

        provider.streamReply(userMessages("hello")) { events.add(it) }

        val error = events.single() as ChatStreamEvent.Error
        assertEquals(true, error.retryable)
        assertTrue(error.message.contains("429"))
    }

    @Test
    fun streamReply_http400_isNotRetryable() = runTest {
        val events = mutableListOf<ChatStreamEvent>()
        val client = OkHttpClient.Builder()
            .addInterceptor(httpResponseInterceptor(code = 400, body = "bad request"))
            .build()
        val provider = SiliconFlowChatProvider(
            apiKeyProvider = { "key" },
            modelProvider = { "model" },
            client = client,
        )

        provider.streamReply(userMessages("hello")) { events.add(it) }

        val error = events.single() as ChatStreamEvent.Error
        assertEquals(false, error.retryable)
        assertTrue(error.message.contains("400"))
    }

    @Test
    fun streamReply_http200NoDelta_returnsError() = runTest {
        val events = mutableListOf<ChatStreamEvent>()
        val body = "data: {}\n\n"
        val client = OkHttpClient.Builder()
            .addInterceptor(httpResponseInterceptor(code = 200, body = body))
            .build()
        val provider = SiliconFlowChatProvider(
            apiKeyProvider = { "key" },
            modelProvider = { "model" },
            client = client,
        )

        provider.streamReply(userMessages("hello")) { events.add(it) }

        val error = events.single() as ChatStreamEvent.Error
        assertIs<ChatStreamEvent.Error>(error)
        assertTrue(error.message.contains("未返回有效文本"))
    }

    private fun passthroughClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(httpResponseInterceptor(code = 500, body = "server error"))
            .build()
    }

    private fun httpResponseInterceptor(code: Int, body: String): Interceptor {
        return Interceptor { chain ->
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("mock")
                .body(body.toResponseBody("text/plain".toMediaTypeOrNull()))
                .build()
        }
    }

    private fun userMessages(content: String): List<Message> {
        return listOf(Message(id = "test-1", content = content, isUser = true))
    }
}
