package com.filtertube.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamResolverTest {

    @Test
    fun testCompareVersions() {
        assertTrue(InnerTubeResolver.compareVersions("20.50.3", "19.29.1") > 0)
        assertTrue(InnerTubeResolver.compareVersions("19.29.1", "20.50.3") < 0)
        assertEquals(0, InnerTubeResolver.compareVersions("20.50.3", "20.50.3"))
        assertTrue(InnerTubeResolver.compareVersions("21.0.0", "20.50.3") > 0)
        assertTrue(InnerTubeResolver.compareVersions("1.60.20", "1.60.19") > 0)
    }
}
