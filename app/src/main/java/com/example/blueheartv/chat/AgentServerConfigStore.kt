package com.example.blueheartv.chat

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AgentServerConfig(
    val baseUrl: String,
    val apiKey: String,
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank()
}

object AgentServerConfigStore {
    private const val PREFS_NAME = "agent_server_config"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_API_KEY = "api_key"

    const val ASSISTANT_ID = "agent"

    private val _config = MutableStateFlow(AgentServerConfig(baseUrl = "", apiKey = ""))
    val config: StateFlow<AgentServerConfig> = _config.asStateFlow()

    fun init(
        context: Context,
        defaultBaseUrl: String,
        defaultApiKey: String,
    ) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _config.value = AgentServerConfig(
            baseUrl = prefs.getString(KEY_BASE_URL, null)?.trim().orEmpty()
                .ifBlank { defaultBaseUrl.trim() },
            apiKey = prefs.getString(KEY_API_KEY, null)?.trim().orEmpty()
                .ifBlank { defaultApiKey.trim() },
        )
    }

    fun update(context: Context, baseUrl: String, apiKey: String) {
        val normalized = AgentServerConfig(
            baseUrl = baseUrl.trim().trimEnd('/'),
            apiKey = apiKey.trim(),
        )
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString(KEY_BASE_URL, normalized.baseUrl)
            putString(KEY_API_KEY, normalized.apiKey)
        }
        _config.value = normalized
    }

    fun snapshot(): AgentServerConfig = _config.value

    @androidx.annotation.VisibleForTesting
    fun setForTesting(baseUrl: String, apiKey: String = "") {
        _config.value = AgentServerConfig(baseUrl = baseUrl, apiKey = apiKey)
    }
}
