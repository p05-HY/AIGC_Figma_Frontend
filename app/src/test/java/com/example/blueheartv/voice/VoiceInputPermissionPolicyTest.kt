package com.example.blueheartv.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceInputPermissionPolicyTest {

    @Test
    fun micClick_fromTextWithoutAudioPermission_requestsPermission() {
        val action = VoiceInputPermissionPolicy.onMicClick(
            currentMode = InputMode.TEXT,
            hasAudioPermission = false,
        )

        assertEquals(VoiceMicPermissionAction.REQUEST_AUDIO_PERMISSION, action)
    }

    @Test
    fun micClick_fromTextWithAudioPermission_entersVoiceMode() {
        val action = VoiceInputPermissionPolicy.onMicClick(
            currentMode = InputMode.TEXT,
            hasAudioPermission = true,
        )

        assertEquals(VoiceMicPermissionAction.ENTER_VOICE_MODE, action)
    }

    @Test
    fun micClick_fromVoice_exitsVoiceModeEvenWhenPermissionMissing() {
        val action = VoiceInputPermissionPolicy.onMicClick(
            currentMode = InputMode.VOICE,
            hasAudioPermission = false,
        )

        assertEquals(VoiceMicPermissionAction.EXIT_VOICE_MODE, action)
    }

    @Test
    fun holdStart_withoutAudioPermission_requestsPermissionOnly() {
        val action = VoiceInputPermissionPolicy.onHoldStart(hasAudioPermission = false)

        assertEquals(VoiceHoldPermissionAction.REQUEST_AUDIO_PERMISSION, action)
    }

    @Test
    fun holdStart_withAudioPermission_startsRecording() {
        val action = VoiceInputPermissionPolicy.onHoldStart(hasAudioPermission = true)

        assertEquals(VoiceHoldPermissionAction.START_RECORDING, action)
    }
}
