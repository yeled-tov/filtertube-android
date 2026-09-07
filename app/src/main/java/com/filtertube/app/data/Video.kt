package com.filtertube.app.data

import kotlinx.serialization.Serializable

/**
 * סרטון YouTube מערוץ מאושר.
 */
@Serializable
data class Video(
    val id: String,                  // YouTube video ID (11 chars)
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbnailUrl: String,
    val publishedAt: Long,           // milliseconds since epoch
    val isShort: Boolean = false,    // Shorts stay in the dedicated Shorts tab
    val durationSec: Long = 0L,      // משך הסרטון בשניות
    val viewCount: Long = 0L,        // מספר צפיות
) {
    /** "לפני 3 שעות", "לפני 2 ימים", או "תאריך לא זמין" */
    fun timeAgoHe(): String {
        if (publishedAt <= 0L) return "תאריך לא זמין"
        val diff = System.currentTimeMillis() - publishedAt
        if (diff < -60_000L) return "תאריך לא זמין" // תאריך עתידי
        val mins = diff / 60_000
        if (mins < 1) return "עכשיו"
        if (mins < 60) return "לפני $mins דק׳"
        val hrs = mins / 60
        if (hrs < 24) return "לפני $hrs שעות"
        val days = hrs / 24
        if (days < 7) return "לפני $days ימים"
        val weeks = days / 7
        if (weeks < 5) return "לפני $weeks שבועות"
        val months = days / 30
        if (months < 12) return "לפני $months חודשים"
        return "לפני ${days / 365} שנים"
    }

    /** עיצוב משך הסרטון (לדוגמה: "4:32", "1:12:08", "0:45") */
    fun formattedDuration(): String {
        if (durationSec <= 0L) return ""
        val h = durationSec / 3600L
        val m = (durationSec % 3600L) / 60L
        val s = durationSec % 60L
        return if (h > 0) {
            "%d:%02d:%02d".format(h, m, s)
        } else {
            "%d:%02d".format(m, s)
        }
    }

    /** עיצוב מספר הצפיות (לדוגמה: "950 צפיות", "1.2K צפיות", "12.5K צפיות", "1.25M צפיות") */
    fun formattedViewCount(): String {
        if (viewCount <= 0L) return ""
        if (viewCount < 1000L) return "$viewCount צפיות"
        if (viewCount < 1_000_000L) {
            val v = viewCount / 1000.0
            val formatted = "%.1f".format(v).replace(".0", "")
            return "${formatted}K צפיות"
        }
        if (viewCount < 1_000_000_000L) {
            val v = viewCount / 1_000_000.0
            val formatted = "%.2f".format(v).replace(".00", "").replace(Regex("""0+$"""), "").replace(Regex("""\.$"""), "")
            return "${formatted}M צפיות"
        }
        val v = viewCount / 1_000_000_000.0
        val formatted = "%.2f".format(v).replace(".00", "").replace(Regex("""0+$"""), "").replace(Regex("""\.$"""), "")
        return "${formatted}B צפיות"
    }
}
