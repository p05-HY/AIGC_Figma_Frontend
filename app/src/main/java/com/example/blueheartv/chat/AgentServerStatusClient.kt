package com.example.blueheartv.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class AgentServerStatusSnapshot(
    val adb: AdbStatus,
    val system: SystemToolStatus,
    val network: NetworkRuntimeStatus,
)

data class AdbStatus(
    val connected: Boolean,
    val width: Int?,
    val height: Int?,
    val currentPackage: String?,
    val activity: String?,
    val error: String? = null,
)

data class SystemToolStatus(
    val connected: Boolean,
    val path: String?,
    val remoteAddress: String?,
    val error: String? = null,
)

data class NetworkRuntimeStatus(
    val networkConnected: Boolean?,
    val mode: String?,
    val localServerRunning: Boolean?,
    val localBaseUrl: String?,
    val localModelName: String?,
    val localModelPath: String?,
    val lastError: String?,
    val error: String? = null,
)

class AgentServerStatusClient(
    private val configProvider: () -> AgentServerConfig = { AgentServerConfigStore.snapshot() },
    private val client: OkHttpClient = sharedClient,
) {
    suspend fun fetchAll(): AgentServerStatusSnapshot = withContext(Dispatchers.IO) {
        AgentServerStatusSnapshot(
            adb = fetchAdbStatus(),
            system = fetchSystemStatus(),
            network = fetchNetworkStatus(),
        )
    }

    suspend fun updateNetworkConnected(connected: Boolean): NetworkRuntimeStatus = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("connected", connected)
            .toString()
            .toRequestBody(JSON)
        val json = executeJson(
            Request.Builder()
                .url(url("network", deviceId(), "status"))
                .post(body)
                .addJsonHeaders()
                .build()
        )
        NetworkRuntimeStatus(
            networkConnected = json.optionalBoolean("networkConnected"),
            mode = json.optionalString("mode"),
            localServerRunning = json.optionalBoolean("localServerRunning"),
            localBaseUrl = json.optionalString("localBaseUrl"),
            localModelName = json.optionalString("localModelName"),
            localModelPath = json.optionalString("localModelPath"),
            lastError = json.optionalString("lastError"),
        )
    }

    private fun fetchAdbStatus(): AdbStatus {
        return runCatching {
            val json = getJson(url("adb", deviceId(), "status"))
            AdbStatus(
                connected = json.optBoolean("connected", false),
                width = json.optionalInt("width"),
                height = json.optionalInt("height"),
                currentPackage = json.optionalString("currentPackage"),
                activity = json.optionalString("activity"),
            )
        }.getOrElse { error ->
            AdbStatus(
                connected = false,
                width = null,
                height = null,
                currentPackage = null,
                activity = null,
                error = error.message ?: "ADB 状态查询失败",
            )
        }
    }

    private fun fetchSystemStatus(): SystemToolStatus {
        return runCatching {
            val json = getJson(url("system", deviceId(), "status"))
            SystemToolStatus(
                connected = json.optBoolean("connected", false),
                path = json.optionalString("path"),
                remoteAddress = json.optionalString("remoteAddress"),
            )
        }.getOrElse { error ->
            SystemToolStatus(
                connected = false,
                path = null,
                remoteAddress = null,
                error = error.message ?: "System 状态查询失败",
            )
        }
    }

    private fun fetchNetworkStatus(): NetworkRuntimeStatus {
        return runCatching {
            val json = getJson(url("network", deviceId(), "status"))
            NetworkRuntimeStatus(
                networkConnected = json.optionalBoolean("networkConnected"),
                mode = json.optionalString("mode"),
                localServerRunning = json.optionalBoolean("localServerRunning"),
                localBaseUrl = json.optionalString("localBaseUrl"),
                localModelName = json.optionalString("localModelName"),
                localModelPath = json.optionalString("localModelPath"),
                lastError = json.optionalString("lastError"),
            )
        }.getOrElse { error ->
            NetworkRuntimeStatus(
                networkConnected = null,
                mode = null,
                localServerRunning = null,
                localBaseUrl = null,
                localModelName = null,
                localModelPath = null,
                lastError = null,
                error = error.message ?: "Network 状态查询失败",
            )
        }
    }

    private fun getJson(url: HttpUrl): JSONObject {
        val request = Request.Builder()
            .url(url)
            .get()
            .addJsonHeaders()
            .build()
        return executeJson(request)
    }

    private fun executeJson(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                throw IOException(body.ifBlank { "HTTP ${response.code}" })
            }
            return JSONObject(body.ifBlank { "{}" })
        }
    }

    private fun Request.Builder.addJsonHeaders(): Request.Builder {
        addHeader("Content-Type", "application/json")
        configProvider().apiKey.takeIf { it.isNotBlank() }?.let { addHeader("X-Api-Key", it) }
        return this
    }

    private fun url(vararg segments: String): HttpUrl {
        val base = normalizedBaseUrl()
        return base.newBuilder()
            .encodedPath(joinPath(base.encodedPath, *segments))
            .build()
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

    private fun joinPath(basePath: String, vararg segments: String): String {
        val prefix = basePath.trimEnd('/').takeIf { it.isNotEmpty() && it != "/" }.orEmpty()
        return (listOf(prefix) + segments.map { it.trim('/') })
            .filter { it.isNotBlank() }
            .joinToString(prefix = "/", separator = "/")
    }

    private fun deviceId(): String = DeviceIdStore.deviceId()
        ?.trim()
        ?.trim('/')
        .orEmpty()

    private fun JSONObject.optionalString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optionalInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return optInt(key)
    }

    private fun JSONObject.optionalBoolean(key: String): Boolean? {
        if (!has(key) || isNull(key)) return null
        return optBoolean(key)
    }

    private companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val sharedClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
