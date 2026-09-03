package com.filtertube.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeUrlParserTest {

    @Test
    fun testExtractVideoId() {
        val expected = "dQw4w9WgXcQ"
        assertEquals(expected, YouTubeUrlParser.extractVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(expected, YouTubeUrlParser.extractVideoId("https://youtu.be/dQw4w9WgXcQ?t=4"))
        assertEquals(expected, YouTubeUrlParser.extractVideoId("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertEquals(expected, YouTubeUrlParser.extractVideoId("https://www.youtube.com/live/dQw4w9WgXcQ"))
        assertEquals(expected, YouTubeUrlParser.extractVideoId("https://www.youtube.com/embed/dQw4w9WgXcQ"))
        assertNull(YouTubeUrlParser.extractVideoId("https://www.google.com"))
    }
}
