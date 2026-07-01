package com.example.blueheartv.chat.stream

class StreamSession internal constructor(
    val threadId: String,
    val assistantMessageId: String,
    var runId: String,
    val accumulatorRunId: String,
    val startedAtMillis: Long,
) {
    private var boundRunId: String? = null
    private var lastReceivedStreamSeq: Long = 0L
    private var activityVersion: Long = 0L

    var backendRunId: String? = null
        internal set
    var terminalStatus: String? = null
        internal set
    var lastHeartbeatAtMillis: Long = 0L
        internal set
    var lastEventAtMillis: Long = startedAtMillis
        internal set

    val receivedStreamSeq: Long
        get() = lastReceivedStreamSeq

    val version: Long
        get() = activityVersion

    internal fun bindRunId(eventRunId: String?): Boolean {
        val normalized = eventRunId?.takeIf { it.isNotBlank() } ?: return true
        val bound = boundRunId
        if (bound == null) {
            boundRunId = normalized
            runId = normalized
            return true
        }
        if (bound != normalized) return false
        runId = normalized
        return true
    }

    internal fun acceptStreamSeq(
        streamSeq: Long?,
        allowNonIncreasingTerminal: Boolean,
        nowMillis: Long,
    ): Boolean {
        lastEventAtMillis = nowMillis
        if (streamSeq == null) {
            activityVersion += 1
            return true
        }
        if (streamSeq <= lastReceivedStreamSeq) {
            if (allowNonIncreasingTerminal) {
                activityVersion += 1
                return true
            }
            return false
        }
        lastReceivedStreamSeq = streamSeq
        activityVersion += 1
        return true
    }

    internal fun markHeartbeat(nowMillis: Long) {
        lastHeartbeatAtMillis = nowMillis
        lastEventAtMillis = nowMillis
    }
}
