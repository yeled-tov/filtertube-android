package com.filtertube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * "מוזיקה שאהבתי" מיוטיוב מיוזיק.
 *
 * ## למה לא ה-API הרגיל
 * YouTube Data API v3 חושף את הפלייליסט "אהבתי" של יוטיוב (LL) בלבד.
 * "מוזיקה שאהבתי" של מיוזיק היא פלייליסט נפרד (LM) שה-API פשוט לא מגיש —
 * זו לא הגבלת הרשאות אלא פער בממשק.
 *
 * ## מה כן עובד, ובלי התחברות נוספת
 * השרת של music.youtube.com (InnerTube) מקבל את אותו access token שכבר
 * התקבל בהתחברות עם גוגל, עם אותו scope בדיוק (youtube.force-ssl). כלומר
 * אין כאן חשבון שני, אין עוגיות, אין WebView ואין סיסמה — חיבור אחד שכבר
 * קיים, נקודת קצה אחרת.
 *
 * ## הסינון
 * התוצאה מסוננת לערוצים מאושרים. גם רשימה אישית של המשתמש עוברת דרך
 * הרשימה הלבנה — אחרת היה כאן פתח לעקוף את כל האפליקציה דרך "אהבתי
 * במיוזיק".
 */
object YouTubeMusicApi {

    private const val ENDPOINT = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"

    /** מזהה הפלייליסט "מוזיקה שאהבתי" ב-InnerTube. */
    private const val LIKED_MUSIC = "FEmusic_liked_videos"

    private const val CLIENT_NAME = "WEB_REMIX"
    private const val CLIENT_VERSION = "1.20240103.01.00"

    private val http = Http.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /**
     * השירים שהמשתמש סימן ב״אהבתי״ ביוטיוב מיוזיק, מסוננים לערוצים מאושרים.
     *
     * לא זורק: כישלון כאן פירושו רשימה ריקה, לא מסך שבור.
     */
    suspend fun likedSongs(accessToken: String, approvedChannelIds: Set<String>): List<Video> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().apply {
                put("browseId", LIKED_MUSIC)
                put(
                    "context",
                    JSONObject().put(
                        "client",
                        JSONObject().apply {
                            put("clientName", CLIENT_NAME)
                            put("clientVersion", CLIENT_VERSION)
                            put("hl", "he")
                            put("gl", "IL")
                        },
                    ),
                )
            }
            val request = Request.Builder()
                .url(ENDPOINT)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .header("X-Goog-Api-Format-Version", "1")
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", CLIENT_VERSION)
                .header("Origin", "https://music.youtube.com")
                .post(body.toString().toRequestBody(jsonMedia))
                .build()

            val raw = runCatching {
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Diagnostics.log("YT MUSIC: השרת החזיר ${response.code}")
                        null
                    } else {
                        response.body?.string()
                    }
                }
            }.getOrElse {
                Diagnostics.log("YT MUSIC: הבקשה נכשלה — ${it.message}")
                null
            } ?: return@withContext emptyList()

            val all = runCatching { parseSongs(JSONObject(raw)) }.getOrElse {
                Diagnostics.log("YT MUSIC: פענוח נכשל — ${it.message}")
                emptyList()
            }
            val approved = all.filter { it.channelId in approvedChannelIds }
            Diagnostics.log(
                "YT MUSIC: ${all.size} שירים ב״אהבתי״, ${approved.size} מערוצים מאושרים",
            )
            approved
        }

    // ── פענוח ─────────────────────────────────────────────────────────────
    // המבנה של InnerTube עמוק ומשתנה בין גרסאות, ולכן סורקים אותו רקורסיבית
    // ומחפשים את הצומת המוכר במקום להסתמך על נתיב קבוע שיישבר בעדכון הבא.

    private fun parseSongs(root: JSONObject): List<Video> {
        val out = LinkedHashMap<String, Video>()
        walk(root) { node ->
            val item = node.optJSONObject("musicResponsiveListItemRenderer") ?: return@walk
            val videoId = item.optJSONObject("playlistItemData")?.optString("videoId")
                ?.takeIf { it.isNotBlank() }
                ?: findVideoId(item)
                ?: return@walk
            if (out.containsKey(videoId)) return@walk

            val columns = item.optJSONArray("flexColumns") ?: return@walk
            val title = columnText(columns, 0) ?: return@walk
            val artist = columnText(columns, 1).orEmpty()
            val channelId = findChannelId(columns).orEmpty()

            out[videoId] = Video(
                id = videoId,
                title = title,
                channelName = artist,
                channelId = channelId,
                thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                publishedAt = System.currentTimeMillis(),
            )
        }
        return out.values.toList()
    }

    private fun columnText(columns: JSONArray, index: Int): String? {
        val column = columns.optJSONObject(index) ?: return null
        val runs = column.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")?.optJSONArray("runs") ?: return null
        val builder = StringBuilder()
        for (i in 0 until runs.length()) {
            builder.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        }
        return builder.toString().takeIf { it.isNotBlank() }
    }

    /** מזהה הערוץ מגיע מה-run של שם האמן, כקישור דפדוף. */
    private fun findChannelId(columns: JSONArray): String? {
        var found: String? = null
        walk(JSONObject().put("columns", columns)) { node ->
            if (found != null) return@walk
            val id = node.optJSONObject("browseEndpoint")?.optString("browseId")
            if (!id.isNullOrBlank() && id.startsWith("UC")) found = id
        }
        return found
    }

    private fun findVideoId(item: JSONObject): String? {
        var found: String? = null
        walk(item) { node ->
            if (found != null) return@walk
            val id = node.optJSONObject("watchEndpoint")?.optString("videoId")
            if (!id.isNullOrBlank()) found = id
        }
        return found
    }

    private fun walk(node: Any?, visit: (JSONObject) -> Unit) {
        when (node) {
            is JSONObject -> {
                visit(node)
                val keys = node.keys()
                while (keys.hasNext()) walk(node.opt(keys.next()), visit)
            }
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), visit)
        }
    }
}
