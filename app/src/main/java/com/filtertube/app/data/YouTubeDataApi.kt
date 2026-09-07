package com.filtertube.app.data

/**
 * נזרקת כאשר קריאה ל-YouTube Data API נכשלת (למשל 403 בגלל מכסה שנגמרה).
 */
class YouTubeDataApiException(
    val statusCode: Int,
    val errorBody: String,
    message: String,
) : Exception(message)

/**
 * גישה חסכונית ל-YouTube Data API.
 *
 * ## למה הקובץ הזה השתנה מהיסוד
 * הגרסה הקודמת השתמשה ב-`search.list`, שעולה **100 יחידות מכסה לקריאה**, בזמן
 * שהמכסה היומית של הפרויקט היא 10,000 יחידות — כלומר כ-100 חיפושים ליום *לכל
 * המשתמשים ביחד*. גרוע מכך, מסך "שידורים חיים" הריץ `search.list` נפרד לכל ערוץ
 * מאושר: 166 ערוצים × 100 = **16,600 יחידות בפתיחה אחת** של המסך — יותר מהמכסה
 * היומית כולה. מרגע זה כל קריאה נוספת החזירה 403, החיפוש נתקע, והמשתמש ראה שגיאה.
 *
 * לכן `search.list` הוסר לגמרי:
 * - **חיפוש** רץ עכשיו מקומית על הפיד השמור ואז דרך NewPipe — אפס יחידות מכסה.
 * - **שידורים חיים** מזוהים דרך [VideoMetadata] עם `videos.list`, שעולה
 *   **יחידה אחת לכל 50 סרטונים**.
 */
object YouTubeDataApi {

    /**
     * מתוך [videos] — מחזיר את אלה שמשודרים כרגע בשידור חי.
     * עלות: יחידת מכסה אחת לכל 50 סרטונים, במקום 100 יחידות לכל ערוץ.
     */
    suspend fun liveFromVideos(
        context: android.content.Context,
        videos: List<Video>,
    ): List<Video> {
        if (videos.isEmpty()) return emptyList()
        val candidates = videos.distinctBy { it.id }.take(300)
        val liveIds = VideoMetadata.liveIds(context, candidates)
        Diagnostics.log("LIVE: ${liveIds.size} שידורים חיים מתוך ${candidates.size} סרטונים נבדקים")
        return candidates.filter { it.id in liveIds }
    }
}
