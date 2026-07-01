package com.example.blueheartv.viewmodel

internal class StreamTextAccumulator {
    private data class Entry(
        val invocationId: String,
        val builder: StringBuilder = StringBuilder(),
        val thinkStripper: ThinkStripper = ThinkStripper(),
    )

    private val entries = mutableMapOf<String, Entry>()

    val activeCount: Int
        get() = entries.size

    fun append(messageId: String, runId: String? = null, invocationId: String?, chunk: String): String {
        val key = entryKey(messageId, runId)
        val normalizedInvocationId = invocationId?.takeIf { it.isNotBlank() } ?: messageId
        val entry = entries[key]?.takeIf { it.invocationId == normalizedInvocationId }
            ?: Entry(normalizedInvocationId).also { entries[key] = it }
        entry.builder.append(entry.thinkStripper.feed(chunk))
        return entry.builder.toString()
    }

    fun take(messageId: String, runId: String? = null): String {
        val entry = entries.remove(entryKey(messageId, runId)) ?: return ""
        entry.builder.append(entry.thinkStripper.finish())
        return entry.builder.toString()
    }

    fun clear(messageId: String, runId: String? = null) {
        entries.remove(entryKey(messageId, runId))
    }

    fun clearAll() {
        entries.clear()
    }

    private fun entryKey(messageId: String, runId: String?): String =
        runId?.takeIf { it.isNotBlank() }?.let { "$messageId#$it" } ?: messageId

    private class ThinkStripper {
        private var buffer = ""
        private var inside = false

        fun feed(chunk: String): String {
            buffer += chunk
            val output = StringBuilder()
            while (buffer.isNotEmpty()) {
                val markers = if (inside) listOf(CLOSE) else listOf(OPEN, CLOSE)
                val match = findFirstMarker(buffer, markers)
                if (match == null) {
                    val suffixLength = partialSuffixLength(buffer, markers)
                    val stable = if (suffixLength > 0) buffer.dropLast(suffixLength) else buffer
                    if (!inside) output.append(stable)
                    buffer = if (suffixLength > 0) buffer.takeLast(suffixLength) else ""
                    break
                }
                val (marker, index) = match
                if (!inside) {
                    output.append(buffer.take(index))
                    inside = marker == OPEN
                } else {
                    inside = false
                }
                buffer = buffer.drop(index + marker.length)
            }
            return output.toString()
        }

        fun finish(): String {
            if (inside) {
                buffer = ""
                inside = false
                return ""
            }
            val pending = buffer
            buffer = ""
            return pending
        }

        private fun findFirstMarker(value: String, markers: List<String>): Pair<String, Int>? {
            val lower = value.lowercase()
            return markers
                .mapNotNull { marker ->
                    val index = lower.indexOf(marker)
                    if (index >= 0) marker to index else null
                }
                .minByOrNull { it.second }
        }

        private fun partialSuffixLength(value: String, markers: List<String>): Int {
            val lower = value.lowercase()
            return markers.maxOf { marker ->
                val maxLength = minOf(value.length, marker.length - 1)
                (maxLength downTo 1).firstOrNull { size ->
                    marker.startsWith(lower.takeLast(size))
                } ?: 0
            }
        }

        private companion object {
            const val OPEN = "<think>"
            const val CLOSE = "</think>"
        }
    }
}
