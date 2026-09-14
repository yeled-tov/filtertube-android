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
    @SerialName("gender")
    val gender: String = "",
)

val categoryLabels: Map<String, String> = mapOf(
    "torah" to "תורה",
    "torah_study" to "שיעורי תורה",
    "music" to "מוזיקה",
    "dati_light" to "דתי לייט",
    "kids" to "ילדים",
    "diy" to "עשה זאת בעצמך",
    "cooking" to "אפייה ובישול",
    "beauty" to "יופי",
    "fashion" to "אופנה",
    "home" to "בית",
    "education" to "חינוך",
    "events" to "אירועים והופעות",
    "news" to "חדשות",
    "general" to "כללי",
)

/** קטגוריות "דתי לייט" — מוצגות רק ברמה 3, ותמיד מתנגנות כאודיו בלבד. */
val audioOnlyCategories: Set<String> = setOf("dati_light")

/** רמת הסינון שבה מציגים את ערוצי "דתי לייט". */
const val DATI_LIGHT_LEVEL = 3

/**
 * האם התוכן הזה מוגבל לאודיו — **ללא קשר למי שמבקש ומה הוא ביקש**.
 *
 * ## למה זה יושב כאן ולא בנגן
 * הכלל הזה חל על שני מסלולים שונים לגמרי: הניגון וההורדה. כשהוא היה ממומש
 * רק בנגן, "דתי לייט" אכן התנגן כאודיו — אבל כפתור ההורדה הציע את כל
 * איכויות הווידאו, והקובץ נשמר לגלריית המכשיר. כלומר ההגבלה הייתה תקפה רק
 * כל עוד לא לוחצים "הורד", וזה מבטל אותה לגמרי.
 *
 * פונקציה אחת, שני המסלולים קוראים לה, ואי אפשר להוסיף מסלול שלישי ששוכח.
 */
fun isAudioOnlyContent(category: String?, level: Int, audioOnlyMode: Boolean): Boolean =
    audioOnlyMode || category in audioOnlyCategories || (level == 1 && category == "music")

/**
 * סינון הערוצים לפי רמת הסינון ומגדר יעד:
 *  - רמה 3 (דתי לייט): כל הערוצים כולל "דתי לייט"
 *  - אחרת: ללא ערוצי "דתי לייט"
 *  - אם יש מגדר שנבחר, מציגים רק ערוצים שמתאימים אליו, ואילו ללא מגדר ניתנים לכולם.
 */
fun List<Channel>.forLevel(level: Int, userGender: String = ""): List<Channel> {
    val visible = if (level == DATI_LIGHT_LEVEL) this else filter { it.category !in audioOnlyCategories }
    if (userGender.isBlank()) return visible

    val target = userGender.trim().lowercase()
    return visible.filter { ch ->
        val gender = ch.gender.trim().lowercase()
        gender.isBlank() || gender == "all" || gender == target
    }
}
