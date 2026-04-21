package com.example.blueheartv.chat

data class SiliconFlowConfig(
    val apiKey: String?,
    val model: String,
)

object SiliconFlowConfigStore {
    private const val DEFAULT_MODEL = "deepseek-ai/DeepSeek-R1-Distill-Qwen-32B"

    @Volatile
    private var apiKey: String? = null

    @Volatile
    private var model: String = DEFAULT_MODEL

    fun configure(
        apiKey: String?,
        model: String?,
    ) {
        this.apiKey = apiKey?.trim()?.takeIf { it.isNotEmpty() }
        this.model = model?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_MODEL
    }

    fun clearApiKey() {
        apiKey = null
    }

    fun snapshot(): SiliconFlowConfig = SiliconFlowConfig(
        apiKey = apiKey,
        model = model,
    )
}
