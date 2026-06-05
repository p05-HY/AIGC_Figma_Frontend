package com.example.blueheartv.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRecognitionFallbackPolicyTest {

    @Test
    fun shouldUseSystemRecognizer_forNoMatchWithoutText() {
        val diagnostics = VoiceRecognitionDiagnostics(
            errorCode = VoiceRecognitionFallbackPolicy.ERROR_NO_MATCH,
            lastPartialText = "",
            finalText = "",
        )

        assertTrue(VoiceRecognitionFallbackPolicy.shouldUseSystemRecognizer(diagnostics))
    }

    @Test
    fun shouldUseSystemRecognizer_forSpeechTimeoutWithoutText() {
        val diagnostics = VoiceRecognitionDiagnostics(
            errorCode = VoiceRecognitionFallbackPolicy.ERROR_SPEECH_TIMEOUT,
            lastPartialText = "",
            finalText = "",
        )

        assertTrue(VoiceRecognitionFallbackPolicy.shouldUseSystemRecognizer(diagnostics))
    }

    @Test
    fun shouldNotUseSystemRecognizer_whenPartialTextExists() {
        val diagnostics = VoiceRecognitionDiagnostics(
            errorCode = VoiceRecognitionFallbackPolicy.ERROR_NO_MATCH,
            lastPartialText = "你好",
            finalText = "",
        )

        assertFalse(VoiceRecognitionFallbackPolicy.shouldUseSystemRecognizer(diagnostics))
    }

    @Test
    fun shouldNotUseSystemRecognizer_forNetworkError() {
        val diagnostics = VoiceRecognitionDiagnostics(
            errorCode = 2,
            lastPartialText = "",
            finalText = "",
        )

        assertFalse(VoiceRecognitionFallbackPolicy.shouldUseSystemRecognizer(diagnostics))
    }

    @Test
    fun shouldUseSystemRecognizer_whenSnapshotErrorWasClearedButCallbackHasNoMatch() {
        val diagnostics = VoiceRecognitionDiagnostics(
            errorCode = null,
            lastPartialText = "",
            finalText = "",
        )

        assertTrue(
            VoiceRecognitionFallbackPolicy.shouldUseSystemRecognizer(
                callbackErrorCode = VoiceRecognitionFallbackPolicy.ERROR_NO_MATCH,
                diagnostics = diagnostics,
            ),
        )
    }

    @Test
    fun withCallbackError_preservesCallbackErrorWhenSnapshotHasNoError() {
        val diagnostics = VoiceRecognitionFallbackPolicy.withCallbackError(
            callbackErrorCode = VoiceRecognitionFallbackPolicy.ERROR_SPEECH_TIMEOUT,
            callbackMessage = "未检测到语音",
            diagnostics = VoiceRecognitionDiagnostics(errorCode = null),
        )

        assertTrue(diagnostics.summary().contains("error=6"))
        assertTrue(diagnostics.summary().contains("message=未检测到语音"))
    }

    @Test
    fun summary_containsUsefulDebugFields() {
        val summary = VoiceRecognitionDiagnostics(
            sessionId = 3,
            readyForSpeech = true,
            beganSpeech = false,
            rmsSampleCount = 4,
            maxRmsDb = 6.5f,
            partialResultCount = 0,
            errorCode = VoiceRecognitionFallbackPolicy.ERROR_NO_MATCH,
            errorMessage = "未检测到语音",
        ).summary()

        assertTrue(summary.contains("session=3"))
        assertTrue(summary.contains("ready=true"))
        assertTrue(summary.contains("rmsSamples=4"))
        assertTrue(summary.contains("error=7"))
    }
}