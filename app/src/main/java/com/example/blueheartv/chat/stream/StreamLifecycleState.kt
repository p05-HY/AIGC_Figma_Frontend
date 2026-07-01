package com.example.blueheartv.chat.stream

enum class StreamLifecycleState {
    IDLE,
    STARTING,
    STREAMING,
    RECONNECTING,
    CANCELING,
    CANCELED,
    CANCELED_WITH_UNCONFIRMED_BACKEND,
    INTERRUPTED,
    FAILED,
    DONE,
    WAITING_FOR_USER,
}
