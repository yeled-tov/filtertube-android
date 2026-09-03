package com.filtertube.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StreamResolverTest {

    @Before
    fun setUp() {
        ResolverHealthMonitor.clear()
    }

    @Test
    fun testResolverHealthMonitorCooldown() {
        val id = "IOS"
        assertTrue(ResolverHealthMonitor.isAvailable(id))

        // Record 2 failures (below threshold 3)
        ResolverHealthMonitor.recordFailure(id, "HTTP 400")
        ResolverHealthMonitor.recordFailure(id, "HTTP 400")
        assertTrue(ResolverHealthMonitor.isAvailable(id))

        // 3rd failure triggers cooldown
        ResolverHealthMonitor.recordFailure(id, "HTTP 400")
        assertFalse(ResolverHealthMonitor.isAvailable(id))

        // Success resets health
        ResolverHealthMonitor.recordSuccess(id)
        assertTrue(ResolverHealthMonitor.isAvailable(id))
    }
}
