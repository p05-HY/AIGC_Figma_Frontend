package com.example.blueheartv.viewmodel

internal class StreamTextAccumulator {
    private data class Entry(
        val invocationId: String,
        val builder: StringBuilder = StringBuilder(),
    )

    private val entries = mutableMapOf<String, Entry>()

    val activeCount: Int
        get() = entries.size

    fun append(messageId: String, invocationId: String?, chunk: String): String {
        val normalizedInvocationId = invocationId?.takeIf { it.isNotBlank() } ?: messageId
        val entry = entries[messageId]?.takeIf { it.invocationId == normalizedInvocationId }
            ?: Entry(normalizedInvocationId).also { entries[messageId] = it }
        entry.builder.append(chunk)
        return entry.builder.toString()
    }

    fun take(messageId: String): String =
        entries.remove(messageId)?.builder?.toString().orEmpty()

    fun clear(messageId: String) {
        entries.remove(messageId)
    }

    fun clearAll() {
        entries.clear()
    }
}
