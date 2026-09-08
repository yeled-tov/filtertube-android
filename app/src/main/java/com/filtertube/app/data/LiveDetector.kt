package com.filtertube.app.data

import android.content.Context

/**
 * זיהוי שידורים חיים מתוך רשימת סרטונים.
 *
 * ## היסטוריה — למה זה נראה ככה
 * הגרסה הראשונה הריצה `search.list` של YouTube Data API בנפרד **לכל ערוץ
 * מאושר**: 166 ערוצים × 100 יחידות מכסה = 16,600 יחידות בפתיחה אחת של המסך,
 * מול מכסה יומית של 10,000 לכל המשתמשים ביחד. פתיחה בודדת שרפה את כל המכסה
 * של היום, ומאותו רגע כל קריאה באפליקציה החזירה 403.
 *
 * הגרסה השנייה עברה ל-`videos.list` (יחידה אחת לכל 50 סרטונים) — זול בהרבה,
 * אבל עדיין תלוי באותה מכסה משותפת שיכולה להיגמר.
 *
 * היום אין כאן שום קריאה ל-YouTube Data API: הזיהוי נעשה דרך NewPipe
 * ([VideoMetadata]), בלי מפתח ובלי מכסה.
 */
object LiveDetector {

    /** מתוך [videos] — מחזיר את אלה שמשודרים כרגע בשידור חי. */
    suspend fun liveFromVideos(
        context: Context,
        videos: List<Video>,
    ): List<Video> {
        if (videos.isEmpty()) return emptyList()
        val candidates = videos.distinctBy { it.id }.take(300)
        val liveIds = VideoMetadata.liveIds(context, candidates)
        Diagnostics.log("LIVE: ${liveIds.size} שידורים חיים מתוך ${candidates.size} סרטונים נבדקים")
        return candidates.filter { it.id in liveIds }
    }
}
