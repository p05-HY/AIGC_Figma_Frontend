package com.example.blueheartv.chat

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentServerAsrClientTest {

    @Test
    fun transcribeVoice_uploadsMultipartAudioToMobileAsrRoute() {
        var capturedPath = ""
        var capturedApiKey: String? = null
        var capturedHeaderContentType: String? = null
        var capturedBodyContentType = ""
        var capturedBody = ""
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                capturedPath = request.url.encodedPath
                capturedApiKey = request.header("X-Api-Key")
                capturedHeaderContentType = request.header("Content-Type")
                capturedBodyContentType = request.body?.contentType()?.toString().orEmpty()
                val buffer = Buffer()
                request.body?.writeTo(buffer)
                capturedBody = buffer.readUtf8()
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """{"text":"打开微信","provider":"aliyun-nls","requestId":"req-1","durationMs":42}"""
                            .toResponseBody("application/json".toMediaType())
                    )
                    .build()
            }
            .build()
        val agentClient = AgentServerClient(
            configProvider = { AgentServerConfig("http://agent.example", "test-key") },
            client = client,
        )

        val result = agentClient.transcribeVoice(
            audio = byteArrayOf(1, 2, 3, 4),
            audioFormat = "pcm",
            sampleRate = 16_000,
            language = "zh-CN",
        )

        assertEquals("/mobile/asr/transcribe", capturedPath)
        assertEquals("test-key", capturedApiKey)
        assertNull(capturedHeaderContentType)
        assertTrue(capturedBodyContentType.startsWith("multipart/form-data"))
        assertTrue(capturedBody.contains("name=\"format\""))
        assertTrue(capturedBody.contains("pcm"))
        assertTrue(capturedBody.contains("name=\"sampleRate\""))
        assertTrue(capturedBody.contains("16000"))
        assertTrue(capturedBody.contains("name=\"language\""))
        assertTrue(capturedBody.contains("zh-CN"))
        assertTrue(capturedBody.contains("name=\"audio\"; filename=\"speech.pcm\""))
        assertEquals("打开微信", result.text)
        assertEquals("aliyun-nls", result.provider)
        assertEquals("req-1", result.requestId)
        assertEquals(42L, result.durationMs)
    }
}
