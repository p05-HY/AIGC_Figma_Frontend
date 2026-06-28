package com.example.blueheartv.voice

enum class InputMode { TEXT, VOICE }

enum class VoiceRecordingState { IDLE, RECORDING, CANCELLING, RECOGNIZING, SUCCESS, FAILED }

enum class VoiceMicPermissionAction {
    ENTER_VOICE_MODE,
    EXIT_VOICE_MODE,
    REQUEST_AUDIO_PERMISSION,
}

enum class VoiceHoldPermissionAction {
    START_RECORDING,
    REQUEST_AUDIO_PERMISSION,
}

object VoiceInputPermissionPolicy {
    fun onMicClick(
        currentMode: InputMode,
        hasAudioPermission: Boolean,
    ): VoiceMicPermissionAction {
        if (currentMode == InputMode.VOICE) {
            return VoiceMicPermissionAction.EXIT_VOICE_MODE
        }
        return if (hasAudioPermission) {
            VoiceMicPermissionAction.ENTER_VOICE_MODE
        } else {
            VoiceMicPermissionAction.REQUEST_AUDIO_PERMISSION
        }
    }

    fun onHoldStart(hasAudioPermission: Boolean): VoiceHoldPermissionAction {
        return if (hasAudioPermission) {
            VoiceHoldPermissionAction.START_RECORDING
        } else {
            VoiceHoldPermissionAction.REQUEST_AUDIO_PERMISSION
        }
    }
}
