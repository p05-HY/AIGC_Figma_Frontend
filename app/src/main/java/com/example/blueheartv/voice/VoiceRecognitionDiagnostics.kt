package com.example.blueheartv.voice

data class VoiceRecognitionDiagnostics(
    val sessionId: Int = 0,
    val started: Boolean = false,
    val readyForSpeech: Boolean = false,
    val beganSpeech: Boolean = false,
    val endedSpeech: Boolean = false,
    val rmsSampleCount: Int = 0,
    val maxRmsDb: Float = Float.NEGATIVE_INFINITY,
    val partialResultCount: Int = 0,
    val lastPartialText: String = "",
    val finalText: String = "",
    val errorCode: Int? = null,
    val errorMessage: String = "",
    val startedAtMillis: Long = 0L,
    val readyAtMillis: Long = 0L,
    val finishedAtMillis: Long = 0L,
) {
    val hasPartialText: Boolean get() = lastPartialText.isNotBlank()

    fun summary(): String {
        val maxRms = if (maxRmsDb.isFinite()) "%.1f".format(maxRmsDb) else "n/a"
        val readyDelay = if (startedAtMillis > 0L && readyAtMillis > 0L) readyAtMillis - startedAtMillis else -1L
        val duration = if (startedAtMillis > 0L && finishedAtMillis > 0L) finishedAtMillis - startedAtMillis else -1L
        return "session=$sessionId ready=$readyForSpeech began=$beganSpeech ended=$endedSpeech " +
                "rmsSamples=$rmsSampleCount maxRms=$maxRms partials=$partialResultCount " +
                "partialBlank=${lastPartialText.isBlank()} finalBlank=${finalText.isBlank()} " +
                "error=$errorCode readyDelayMs=$readyDelay durationMs=$duration message=$errorMessage"
    }
}

object VoiceRecognitionFallbackPolicy {
    const val ERROR_SPEECH_TIMEOUT = 6
    const val ERROR_NO_MATCH = 7

    fun shouldUseSystemRecognizer(diagnostics: VoiceRecognitionDiagnostics): Boolean {
        return shouldUseSystemRecognizer(diagnostics.errorCode, diagnostics)
    }

    fun shouldUseSystemRecognizer(
        callbackErrorCode: Int?,
        diagnostics: VoiceRecognitionDiagnostics,
    ): Boolean {
        val code = diagnostics.errorCode ?: callbackErrorCode ?: return false
        return code in setOf(ERROR_SPEECH_TIMEOUT, ERROR_NO_MATCH) &&
                diagnostics.lastPartialText.isBlank() &&
                diagnostics.finalText.isBlank()
    }

    fun withCallbackError(
        callbackErrorCode: Int,
        callbackMessage: String,
        diagnostics: VoiceRecognitionDiagnostics,
    ): VoiceRecognitionDiagnostics {
        if (diagnostics.errorCode != null) return diagnostics
        return diagnostics.copy(errorCode = callbackErrorCode, errorMessage = callbackMessage)
    }
}