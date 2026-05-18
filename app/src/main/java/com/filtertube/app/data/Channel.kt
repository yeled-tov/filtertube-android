package com.filtertube.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * Channel ברשימה הלבנה — מגיע מטבלת `channels` ב-Supabase.
 */
@Serializable
data class Channel(
    val id: String,
    @SerialName("youtube_channel_id")
    val youtubeChannelId: String,
    val name: String,
    val category: String,
)

/**
 * תרגום קטגוריות לעברית — לתצוגה במסך.
 */
val categoryLabels: Map<String, String> = mapOf(
    "torah" to "תורה",
    "music" to "מוזיקה",
    "kids" to "ילדים",
    "diy" to "עשה זאת בעצמך",
    "news" to "חדשות",
    "general" to "כללי",
)
