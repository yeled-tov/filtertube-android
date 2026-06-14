package com.filtertube.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * ערוץ ברשימה הלבנה.
 * מגיע מ-channels.json (GitHub raw / asset מקומי) — לא מ-Supabase יותר.
 */
@Serializable
data class Channel(
    @SerialName("youtube_channel_id")
    val youtubeChannelId: String,
    val name: String,
    val category: String = "general",
)

val categoryLabels: Map<String, String> = mapOf(
    "torah" to "תורה",
    "music" to "מוזיקה",
    "dati_light" to "דתי לייט",
    "kids" to "ילדים",
    "diy" to "עשה זאת בעצמך",
    "news" to "חדשות",
    "general" to "כללי",
)

/** קטגוריות שמתנגנות תמיד כאודיו בלבד (ללא וידאו), ללא תלות ברמת הסינון. */
val audioOnlyCategories: Set<String> = setOf("dati_light")
