package com.example.blueheartv.chat

import com.example.blueheartv.BuildConfig
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class AgentServerRawSseTest {

    @Test
    fun sendNiHao_printsRawSseMessages() {
        val baseUrl = configured("agent.server.baseUrl", BuildConfig.AGENT_SERVER_BASE_URL)
        assumeTrue(
            "Set -Dagent.server.baseUrl=http://host:port/agent-server-api to run this test.",
            baseUrl.isNotBlank(),
        )
        val apiKey = configured("agent.server.apiKey", BuildConfig.AGENT_SERVER_API_KEY)

        val threadId = createThread(baseUrl, apiKey)
        val connection = postJson(
            url = agentUrl(baseUrl, "mobile", "threads", threadId, "runs", "stream"),
            apiKey = apiKey,
            body = """
                {
                  "assistant_id": "$ASSISTANT_ID",
                  "input": {
                    "messages": [
                      {
                        "type": "human",
                        "content": "你好"
                      }
                    ]
                  },
                  "stream_mode": ["messages-tuple", "updates", "tasks", "custom"]
                }
            """.trimIndent(),
            accept = "text/event-stream",
        )

        connection.use {
            check(it.responseCode in 200..299) {
                "Agent Server stream failed: HTTP ${it.responseCode} ${it.responseText()}"
            }
            it.inputStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
                lines.forEach(::println)
            }
        }
    }

    private fun createThread(baseUrl: String, apiKey: String): String {
        val connection = postJson(
            url = agentUrl(baseUrl, "threads"),
            apiKey = apiKey,
            body = """{"metadata":{"graph_id":"$ASSISTANT_ID"}}""",
        )
        connection.use {
            check(it.responseCode in 200..299) {
                "Agent Server create thread failed: HTTP ${it.responseCode} ${it.responseText()}"
            }
            val body = it.responseText()
            return THREAD_ID_REGEX.find(body)?.groupValues?.get(1)
                ?: THREAD_ID_CAMEL_REGEX.find(body)?.groupValues?.get(1)
                ?: error("Agent Server did not return thread_id: $body")
        }
    }

    private fun postJson(
        url: URL,
        apiKey: String,
        body: String,
        accept: String = "application/json",
    ): HttpURLConnection {
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 0
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", accept)
            apiKey.takeIf { it.isNotBlank() }?.let { setRequestProperty("X-Api-Key", it) }
            outputStream.use { stream ->
                stream.write(body.toByteArray(StandardCharsets.UTF_8))
            }
        }
    }

    private fun agentUrl(baseUrl: String, vararg segments: String): URL {
        val normalized = when {
            baseUrl.startsWith("http://") || baseUrl.startsWith("https://") -> baseUrl
            baseUrl.startsWith("ws://") -> "http://${baseUrl.removePrefix("ws://")}"
            baseUrl.startsWith("wss://") -> "https://${baseUrl.removePrefix("wss://")}"
            else -> "http://$baseUrl"
        }.trimEnd('/')
        val path = segments.joinToString("/") { it.trim('/') }
        return URL("$normalized/$path")
    }

    private fun HttpURLConnection.responseText(): String {
        val stream = if (responseCode >= 400) errorStream else inputStream
        return stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
    }

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T {
        return try {
            block(this)
        } finally {
            disconnect()
        }
    }

    private fun configured(propertyName: String, buildConfigValue: String): String {
        return System.getProperty(propertyName)?.trim().orEmpty()
            .ifBlank { buildConfigValue.trim() }
    }

    private companion object {
        const val ASSISTANT_ID = "agent"
        val THREAD_ID_REGEX = Regex(""""thread_id"\s*:\s*"([^"]+)"""")
        val THREAD_ID_CAMEL_REGEX = Regex(""""threadId"\s*:\s*"([^"]+)"""")
    }
}
