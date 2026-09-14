package com.filtertube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * השרת הפנימי של יוטיוב, עם אסימון OAuth של החשבון שכבר במכשיר.
 *
 * ## למה זה קיים
 * שני מסלולי החיבור הקיימים מתפשרים, כל אחד בכיוון אחר:
 *
 *  • YouTube Data API — נוח (בוחרים חשבון, בלי סיסמה) אבל מוגבל: גוגל
 *    סגרה שם את היסטוריית הצפייה, את ההמלצות ואת "מוזיקה שאהבתי".
 *  • עוגיות מהדפדפן — מביא הכל, אבל מחייב את המשתמש להקליד סיסמה.
 *
 * כאן מנסים את השילוב: אותה נקודת קצה פנימית שמחזירה את הכל, אבל עם
 * הזדהות Bearer מהאסימון שכבר התקבל בבחירת החשבון — בלי סיסמה, בלי
 * דפדפן, בלי עוגיות.
 *
 * ## האם זה עובד
 * ההרשאה youtube.force-ssl תקפה לשרת הזה, אבל יוטיוב לא מתחייבת שכל
 * browseId יוגש לכל סוג הזדהות. לכן כל קריאה כאן **רושמת ביומן מה בדיוק
 * חזר** — קוד ותחילת גוף התשובה. אם גוגל חוסמת, נדע שהיא חוסמת ולא נחשוב
 * שהבקשה שלנו שגויה; ואם הבקשה שגויה, נראה את ההסבר שלה.
 *
 * כישלון כאן לעולם אינו שובר כלום: הקורא נופל חזרה למסלולים הקיימים.
 */
object InnerTubeOAuth {

    private val http = Http.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    private const val UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    /** יעדי הדפדוף שמעניינים אותנו, עם המארח והלקוח שמגישים כל אחד. */
    private data class Target(
        val label: String,
        val host: String,
        val clientName: String,
        val clientVersion: String,
        val browseId: String,
    )

    private val LIKED_VIDEOS = Target(
        "אהבתי", "https://www.youtube.com", "WEB", "2.20241125.01.00", "VLLL",
    )
    private val LIKED_MUSIC = Target(
        "מוזיקה", "https://music.youtube.com", "WEB_REMIX", "1.20240103.01.00",
        "FEmusic_liked_videos",
    )
    private val HISTORY = Target(
        "היסטוריה", "https://www.youtube.com", "WEB", "2.20241125.01.00", "FEhistory",
    )

    suspend fun likedVideos(token: String): List<Video> = browse(token, LIKED_VIDEOS)
    suspend fun likedMusic(token: String): List<Video> = browse(token, LIKED_MUSIC)
    suspend fun history(token: String): List<Video> = browse(token, HISTORY)

    private suspend fun browse(token: String, target: Target): List<Video> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("browseId", target.browseId)
                put(
                    "context",
                    JSONObject().put(
                        "client",
                        JSONObject().apply {
                            put("clientName", target.clientName)
                            put("clientVersion", target.clientVersion)
                            put("hl", "he")
                            put("gl", "IL")
                        },
                    ),
                )
            }
            val request = Request.Builder()
                .url("${target.host}/youtubei/v1/browse?prettyPrint=false")
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("User-Agent", UA)
                .header("X-Goog-AuthUser", "0")
                .header("Origin", target.host)
                .header("Referer", "${target.host}/")
                .header("X-Origin", target.host)
                .post(body.toString().toRequestBody(jsonMedia))
                .build()

            runCatching {
                http.newCall(request).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        Diagnostics.log(
                            "OAUTH ${target.label}: HTTP ${resp.code} · ${raw.take(180)}",
                        )
                        return@use emptyList()
                    }
                    val items = InnerTube.parseVideos(JSONObject(raw))
                    Diagnostics.log("OAUTH ${target.label}: ${items.size} פריטים ✓")
                    items
                }
            }.getOrElse {
                Diagnostics.log("OAUTH ${target.label}: נכשל — ${it.message}")
                emptyList()
            }
        }
}
