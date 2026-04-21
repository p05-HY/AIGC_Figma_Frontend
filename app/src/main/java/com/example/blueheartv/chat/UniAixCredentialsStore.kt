package com.example.blueheartv.chat

data class UniAixCredentials(
    val apiKey: String? = null,
    val token: String? = null,
)

object UniAixCredentialsStore {
    @Volatile
    private var apiKey: String? = null

    @Volatile
    private var token: String? = null

    fun setApiKey(value: String?) {
        apiKey = value?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun setToken(value: String?) {
        token = value?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun clear() {
        apiKey = null
        token = null
    }

    fun snapshot(): UniAixCredentials = UniAixCredentials(
        apiKey = apiKey,
        token = token,
    )
}
