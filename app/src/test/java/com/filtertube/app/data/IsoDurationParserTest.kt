package com.filtertube.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class IsoDurationParserTest {

    @Test
    fun testParseIsoDuration() {
        assertEquals(45L, IsoDurationParser.parseToSeconds("PT45S"))
        assertEquals(272L, IsoDurationParser.parseToSeconds("PT4M32S"))
        assertEquals(4328L, IsoDurationParser.parseToSeconds("PT1H12M8S"))
        assertEquals(3600L, IsoDurationParser.parseToSeconds("PT1H"))
        assertEquals(720L, IsoDurationParser.parseToSeconds("PT12M"))
        assertEquals(0L, IsoDurationParser.parseToSeconds(""))
    }

    @Test
    fun testVideoFormatting() {
        val v1 = Video("id1", "Title", "Channel", "UC1", "thumb", 0L, durationSec = 272L, viewCount = 12500L)
        assertEquals("4:32", v1.formattedDuration())
        assertEquals("12.5K צפיות", v1.formattedViewCount())
        assertEquals("תאריך לא זמין", v1.timeAgoHe())

        val v2 = Video("id2", "Title2", "Channel", "UC1", "thumb", System.currentTimeMillis() - 3600000L, durationSec = 4328L, viewCount = 1250000L)
        assertEquals("1:12:08", v2.formattedDuration())
        assertEquals("1.25M צפיות", v2.formattedViewCount())
        assertEquals("לפני 1 שעות", v2.timeAgoHe())
    }
}
