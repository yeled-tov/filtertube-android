package com.filtertube.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class YouTubeDataApiTest {

    @Test
    fun testApiExceptionFormatting() {
        val ex = YouTubeDataApiException(403, "{\"error\": \"quotaExceeded\"}", "API Quota Exceeded")
        assertEquals(403, ex.statusCode)
        assertEquals("{\"error\": \"quotaExceeded\"}", ex.errorBody)
        assertEquals("API Quota Exceeded", ex.message)
    }
}
