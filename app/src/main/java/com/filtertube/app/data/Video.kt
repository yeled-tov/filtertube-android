package com.filtertube.app.data

import kotlinx.serialization.Serializable

/**
 * סרטון YouTube מערוץ מאושר.
 * מגיע מ-RSS feed של YouTube (ללא API key).
 */
@Serializable
data class Video(
    val id: String,                  // YouTube video ID (11 chars)
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbnailUrl: String,
    val publishedAt: Long,           // milliseconds since epoch
) {
    /** "לפני 3 שעות", "לפני 2 ימים", וכו' */
    fun timeAgoHe(): String {
        val diff = System.currentTimeMillis() - publishedAt
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
}
