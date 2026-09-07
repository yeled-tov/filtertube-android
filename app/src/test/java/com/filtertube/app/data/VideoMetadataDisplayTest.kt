package com.filtertube.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * בדיקות לתצוגת הנתונים האמיתיים של סרטון.
 *
 * הרגרסיה שנבדקת כאן: ההיסטוריה נהגה לדרוס את publishedAt בזמן הצפייה, ולכן כל
 * סרטון בספרייה הוצג כאילו "עלה עכשיו".
 */
class VideoMetadataDisplayTest {

    private val hour = 60L * 60L * 1000L
    private val day = 24L * hour

    private fun video(publishedAt: Long = 0L, watchedAt: Long = 0L) = Video(
        id = "abc12345678",
        title = "כותרת",
        channelName = "ערוץ",
        channelId = "UC123",
        thumbnailUrl = "",
        publishedAt = publishedAt,
        watchedAt = watchedAt,
    )

    @Test
    fun singularFormsAreGrammatical() {
        val now = System.currentTimeMillis()
        assertEquals("לפני שעה", video(publishedAt = now - hour - 1000).timeAgoHe())
        assertEquals("לפני שעתיים", video(publishedAt = now - 2 * hour - 1000).timeAgoHe())
        assertEquals("אתמול", video(publishedAt = now - day - 1000).timeAgoHe())
        assertEquals("לפני יומיים", video(publishedAt = now - 2 * day - 1000).timeAgoHe())
        assertEquals("לפני שבוע", video(publishedAt = now - 7 * day - 1000).timeAgoHe())
    }

    @Test
    fun missingDateIsReportedRatherThanShownAsNow() {
        assertEquals("תאריך לא זמין", video(publishedAt = 0L).timeAgoHe())
    }

    @Test
    fun watchedTimeIsSeparateFromUploadTime() {
        val now = System.currentTimeMillis()
        // הועלה לפני שבוע, נצפה לפני שעתיים — שני הזמנים חייבים להישמר בנפרד.
        val v = video(publishedAt = now - 7 * day - 1000, watchedAt = now - 2 * hour - 1000)
        assertEquals("לפני שבוע", v.timeAgoHe())
        assertEquals("נצפה לפני שעתיים", v.watchedAgoHe())
    }

    @Test
    fun neverWatchedHasNoWatchedLabel() {
        assertEquals("", video(publishedAt = 1L).watchedAgoHe())
    }

    @Test
    fun durationIsFormattedForBothShortAndLongVideos() {
        assertEquals("4:32", video().copy(durationSec = 272).formattedDuration())
        assertEquals("1:12:08", video().copy(durationSec = 4328).formattedDuration())
        // 0 = לא ידוע — לא מציגים "0:00" מזויף
        assertEquals("", video().copy(durationSec = 0).formattedDuration())
    }

    @Test
    fun viewCountIsFormattedInHebrew() {
        assertTrue(video().copy(viewCount = 950).formattedViewCount().contains("950"))
        assertTrue(video().copy(viewCount = 12_500).formattedViewCount().contains("K"))
        assertTrue(video().copy(viewCount = 1_250_000).formattedViewCount().contains("M"))
        assertEquals("", video().copy(viewCount = 0).formattedViewCount())
    }
}
