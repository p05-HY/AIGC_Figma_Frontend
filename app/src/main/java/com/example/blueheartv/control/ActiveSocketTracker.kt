package com.example.blueheartv.control

/**
 * Keeps callbacks from a socket that has been replaced from mutating the
 * currently active connection.
 */
internal class ActiveSocketTracker<T : Any> {
    var current: T? = null
        private set

    fun replace(socket: T): T? {
        val previous = current
        current = socket
        return previous
    }

    fun clearIfCurrent(socket: T): Boolean {
        if (current !== socket) return false
        current = null
        return true
    }
}
