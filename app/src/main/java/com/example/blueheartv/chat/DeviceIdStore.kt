package com.example.blueheartv.chat

import android.content.Context
import androidx.core.content.edit
import com.example.blueheartv.BuildConfig
import java.util.UUID

/**
 * 全局唯一设备标识（device_id）。
 *
 * 首次访问时生成随机 UUID 并持久化到 SharedPreferences，卸载重装前保持稳定。
 * 统一命名为 device_id，作为 WebSocket 路径段携带，供后端区分设备：
 * - /adb WebSocket：ws://host/adb/{deviceId}
 * - /system WebSocket：ws://host/system/{deviceId}
 * - 后续可扩展到 /network/{deviceId}/status 等自定义 HTTP 接口
 *
 * 是否把 deviceId 拼进路径，由 [BuildConfig.DEVICE_ID_IN_PATH] 开关控制（见 [pathSegment]）：
 * - true（默认）：走带参路径 /adb/{deviceId}，对齐协议、支持多设备区分。
 * - false：走无参路径 /adb（兼容尚未改造的现网后端，便于后端对齐前提前联调）。
 */
object DeviceIdStore {
    private const val PREFS_NAME = "device_identity"
    private const val KEY_DEVICE_ID = "device_id"

    @Volatile
    private var cached: String? = null

    /**
     * 返回拼接到 WebSocket 路径的 deviceId 片段。
     *
     * - 开关关闭（[BuildConfig.DEVICE_ID_IN_PATH] = false）时返回空串，URL 退化为无参路径。
     * - 开关开启但 deviceId 尚不可用时同样返回空串，安全降级。
     */
    fun pathSegment(): String {
        if (!BuildConfig.DEVICE_ID_IN_PATH) return ""
        return deviceId()?.trim()?.trim('/').orEmpty()
    }

    /**
     * 返回当前设备的 device_id。若上下文不可用且尚未生成，返回 null（调用方应跳过注入）。
     */
    fun deviceId(): String? {
        cached?.let { return it }
        val context = AppContextHolder.getOrNull() ?: return null
        return deviceId(context)
    }

    /**
     * 返回当前设备的 device_id，必要时生成并持久化。
     */
    @Synchronized
    fun deviceId(context: Context): String {
        cached?.let { return it }
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_ID, null)?.trim().orEmpty()
        val id = existing.ifBlank {
            UUID.randomUUID().toString().also { generated ->
                prefs.edit { putString(KEY_DEVICE_ID, generated) }
            }
        }
        cached = id
        return id
    }
}
