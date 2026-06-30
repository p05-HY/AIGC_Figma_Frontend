package com.example.blueheartv.chat

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentServerSafeSseTest {

    @Test
    fun streamRun_usesSafeFacadeAndConsumesOnlySafeEvents() {
        var capturedPath = ""
        var capturedRunId: String? = null
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                capturedPath = chain.request().url.encodedPath
                capturedRunId = chain.request().header("X-Mobile-Run-Id")
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(SAFE_SSE.toResponseBody("text/event-stream".toMediaType()))
                    .build()
            }
            .build()
        val events = mutableListOf<ChatStreamEvent>()
        val agentClient = AgentServerClient(
            configProvider = { AgentServerConfig("http://agent.example", "test-key") },
            client = client,
        )

        agentClient.streamRun(
            "thread-1",
            ChatPrompt("你好"),
            mobileRunId = "run-client-1",
            onEvent = events::add,
        )

        assertEquals("/mobile/threads/thread-1/runs/stream", capturedPath)
        assertEquals("run-client-1", capturedRunId)
        assertTrue(events.first() is ChatStreamEvent.StreamStarted)
        assertEquals(1L, (events.first() as ChatStreamEvent.StreamStarted).streamSeq)
        assertTrue(events.any { it is ChatStreamEvent.TextDelta && it.chunk == "答案" })
        assertTrue(events.any { it is ChatStreamEvent.Trace })
        assertTrue(events.any { it is ChatStreamEvent.StreamEof })
        assertFalse(events.any { it is ChatStreamEvent.Completed })
    }

    @Test
    fun streamRun_handlesUtf8ChunkBoundaries_multilineData_commentsAndOneBadEvent() {
        val payload = """
            : keepalive
            id: ignored-id
            retry: 1000

            event: trace.v1
            data: not-json

            event: stream.started
            data: {"type":"stream.started","runId":"run-1","threadId":"thread-1","message":"已接收请求，正在连接 Agent。","streamSeq":1}

            event: assistant.delta
            data: {"type":"assistant.delta",
            data: "chunk":"微信已打开","streamSeq":2}

            event: trace.v1
            data: {"type":"trace.v1","version":1,"runId":"run-1","eventId":"evt-2","seq":2,"event":"run.terminal","status":"succeeded","streamSeq":3}

            event: stream.eof
            data: {"type":"stream.eof","streamSeq":4}

        """.trimIndent()
        val events = streamEvents(chunkedBody(payload, chunkSize = 5))

        assertTrue(events.any { it is ChatStreamEvent.TextDelta && it.chunk == "微信已打开" })
        assertTrue(events.any { it is ChatStreamEvent.Trace })
        assertTrue(events.any { it is ChatStreamEvent.StreamEof })
        assertFalse(events.any { it is ChatStreamEvent.Error })
    }

    @Test
    fun streamRun_dispatchesInterleavedAssistantDeltaAndTraceInOrder() {
        val payload = """
            event: stream.started
            data: {"type":"stream.started","runId":"run-1","threadId":"thread-1","message":"已接收请求，正在连接 Agent。","streamSeq":1}

            event: assistant.delta
            data: {"type":"assistant.delta","chunk":"北京","streamSeq":2}

            event: trace.v1
            data: {"type":"trace.v1","version":1,"runId":"run-1","eventId":"evt-1","seq":1,"event":"step.upsert","step":{"stepId":"weather-1","kind":"weather_query","title":"weather_query","summary":"正在查询北京天气","status":"running","visibleToUser":true},"streamSeq":3}

            event: assistant.delta
            data: {"type":"assistant.delta","chunk":"天气晴。","streamSeq":4}

            event: trace.v1
            data: {"type":"trace.v1","version":1,"runId":"run-1","eventId":"evt-2","seq":2,"event":"run.terminal","status":"succeeded","streamSeq":5}

            event: stream.eof
            data: {"type":"stream.eof","streamSeq":6}

        """.trimIndent()

        val events = streamEvents(payload.toResponseBody("text/event-stream".toMediaType()))

        assertEquals(
            listOf(
                "started",
                "delta:北京",
                "trace",
                "delta:天气晴。",
                "trace",
                "eof",
            ),
            events.map { event ->
                when (event) {
                    is ChatStreamEvent.StreamStarted -> "started"
                    is ChatStreamEvent.TextDelta -> "delta:${event.chunk}"
                    is ChatStreamEvent.Trace -> "trace"
                    is ChatStreamEvent.StreamEof -> "eof"
                    else -> "other"
                }
            },
        )
    }

    @Test
    fun cancelAndStatus_useTheMobileFacadeAndParseBackendConfirmation() {
        val requests = mutableListOf<String>()
        var cancelBody = ""
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requests += "${chain.request().method} ${chain.request().url.encodedPath}"
                val body = when (chain.request().url.encodedPath.substringAfterLast('/')) {
                    "cancel" -> {
                        val requestBody = Buffer()
                        chain.request().body?.writeTo(requestBody)
                        cancelBody = requestBody.readUtf8()
                        """{"runId":"run-1","threadId":"thread-1","status":"not_bound_but_fenced","backendRunId":null,"backendStatus":"unknown_not_bound","cancelSource":"frontend_timeout","terminalReason":"frontend_timeout"}"""
                    }
                    "status" -> """{"runId":"run-1","status":"cancelled","backendStatus":"cancelled","terminal":true}"""
                    else -> error("unexpected request")
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        val agentClient = AgentServerClient(
            configProvider = { AgentServerConfig("http://agent.example", "test-key") },
            client = client,
        )

        val cancellation = agentClient.cancelRun("thread-1", "run-1", cancelSource = "frontend_timeout")
        val status = agentClient.getRunStatus("thread-1", "run-1")

        assertTrue(cancellation.accepted)
        assertEquals("not_bound_but_fenced", cancellation.status)
        assertEquals("unknown_not_bound", cancellation.backendStatus)
        assertEquals("frontend_timeout", cancellation.cancelSource)
        assertEquals("frontend_timeout", cancellation.terminalReason)
        assertEquals("""{"cancelSource":"frontend_timeout"}""", cancelBody)
        assertTrue(status.terminal)
        assertEquals("cancelled", status.backendStatus)
        assertEquals(
            listOf(
                "POST /mobile/threads/thread-1/runs/run-1/cancel",
                "GET /mobile/threads/thread-1/runs/run-1/status",
            ),
            requests,
        )
    }

    @Test
    fun streamRun_keepsTerminalBeforeFollowingStreamError() {
        val payload = """
            event: stream.started
            data: {"type":"stream.started","runId":"run-1","threadId":"thread-1","message":"已接收请求，正在连接 Agent。","streamSeq":1}

            event: trace.v1
            data: {"type":"trace.v1","version":1,"runId":"run-1","eventId":"evt-1","seq":1,"event":"run.terminal","status":"failed","reason":"upstream_ended_without_terminal","streamSeq":2}

            event: stream.error
            data: {"type":"stream.error","message":"任务未返回结束状态，已停止后续操作。","retryable":true,"terminalStatus":"failed","terminalReason":"upstream_ended_without_terminal","streamSeq":3}

            event: stream.eof
            data: {"type":"stream.eof","streamSeq":4}

        """.trimIndent()

        val events = streamEvents(payload.toResponseBody("text/event-stream".toMediaType()))

        assertTrue(events[1] is ChatStreamEvent.Trace)
        assertTrue(events[2] is ChatStreamEvent.Error)
        assertFalse(events.any { it is ChatStreamEvent.StreamEof })
    }

    private fun streamEvents(body: ResponseBody): List<ChatStreamEvent> {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body)
                    .build()
            }
            .build()
        val events = mutableListOf<ChatStreamEvent>()
        AgentServerClient(
            configProvider = { AgentServerConfig("http://agent.example", "test-key") },
            client = client,
        ).streamRun("thread-1", ChatPrompt("打开微信"), onEvent = events::add)
        return events
    }

    private fun chunkedBody(payload: String, chunkSize: Int): ResponseBody {
        val chunks = payload.toByteArray(Charsets.UTF_8)
            .asList()
            .chunked(chunkSize)
            .map { it.toByteArray() }
            .iterator()
        val source = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (!chunks.hasNext()) return -1L
                val chunk = chunks.next()
                sink.write(chunk)
                return chunk.size.toLong()
            }

            override fun timeout(): Timeout = Timeout.NONE

            override fun close() = Unit
        }.buffer()
        return object : ResponseBody() {
            override fun contentType() = "text/event-stream".toMediaType()

            override fun contentLength() = -1L

            override fun source(): BufferedSource = source
        }
    }

    private companion object {
        val SAFE_SSE = """
            event: stream.started
            data: {"type":"stream.started","runId":"run-1","threadId":"thread-1","message":"已接收请求，正在连接 Agent。","streamSeq":1}

            event: assistant.delta
            data: {"type":"assistant.delta","chunk":"答案","streamSeq":2}

            event: stream.heartbeat
            data: {"type":"stream.heartbeat","runId":"run-1","streamSeq":3}

            event: trace.v1
            data: {"type":"trace.v1","version":1,"runId":"run-1","eventId":"evt-1","seq":1,"event":"run.terminal","status":"succeeded","streamSeq":4}

            event: stream.eof
            data: {"type":"stream.eof","streamSeq":5}

        """.trimIndent()
    }
}
