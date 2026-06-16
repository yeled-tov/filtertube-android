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

/** קטגוריות "דתי לייט" — מוצגות רק ברמה 3, ותמיד מתנגנות כאודיו בלבד. */
val audioOnlyCategories: Set<String> = setOf("dati_light")

/** רמת הסינון שבה מציגים את ערוצי "דתי לייט". */
const val DATI_LIGHT_LEVEL = 3

/**
 * סינון הערוצים לפי רמת הסינון:
 *  - רמה 3 (דתי לייט): כל הערוצים כולל "דתי לייט"
 *  - אחרת: ללא ערוצי "דתי לייט"
 */
fun List<Channel>.forLevel(level: Int): List<Channel> =
    if (level == DATI_LIGHT_LEVEL) this
    else filter { it.category !in audioOnlyCategories }
