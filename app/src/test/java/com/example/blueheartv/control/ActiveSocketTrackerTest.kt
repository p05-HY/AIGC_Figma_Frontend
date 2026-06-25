package com.example.blueheartv.control

import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveSocketTrackerTest {

    @Test
    fun staleSocketCloseCannotClearNewActiveSocket() {
        val tracker = ActiveSocketTracker<Any>()
        val stale = Any()
        val current = Any()

        tracker.replace(stale)
        tracker.replace(current)

        assertFalse(tracker.clearIfCurrent(stale))
        assertSame(current, tracker.current)
    }

    @Test
    fun currentSocketCloseClearsActiveSocket() {
        val tracker = ActiveSocketTracker<Any>()
        val current = Any()

        tracker.replace(current)

        assertTrue(tracker.clearIfCurrent(current))
        assertSame(null, tracker.current)
    }
}
