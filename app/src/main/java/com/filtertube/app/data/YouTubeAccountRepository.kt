package com.filtertube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** ערוץ שהמשתמש מנוי אליו ביוטיוב (לתצוגה בספריה). */
@Serializable
data class SubChannel(
    val channelId: String,
    val title: String,
    val thumbnailUrl: String = "",
)

/**
 * קריאות ל-YouTube Data API v3 עם access token של המשתמש.
 * דורש התחברות Google (ראה GoogleAuth).
 */
object YouTubeAccountRepository {

    private val http = Http.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * הודעת שגיאה שאפשר לפעול לפיה.
     *
     * "YouTube API 403" לבדו לא אומר כלום: הוא יכול להיות מכסה יומית
     * שנגמרה, API שלא הופעל בפרויקט, או הרשאה חסרה בטוקן — שלושה דברים עם
     * שלושה פתרונות שונים לגמרי. גוגל מחזירה את הסיבה המדויקת בגוף
     * התשובה (`quotaExceeded`, `accessNotConfigured`, `insufficientPermissions`),
     * וזרקנו אותה. בלעדיה כל אבחון מתחיל מניחוש.
     */
    private fun apiError(code: Int, body: String): String {
        val reason = runCatching {
            val error = JSONObject(body).optJSONObject("error") ?: return@runCatching ""
            val first = error.optJSONArray("errors")?.optJSONObject(0)
            listOfNotNull(
                first?.optString("reason")?.takeIf { it.isNotBlank() },
                error.optString("message").takeIf { it.isNotBlank() },
            ).joinToString(" · ")
        }.getOrDefault("")
        return if (reason.isBlank()) "YouTube API $code" else "YouTube API $code · $reason"
    }

    /** סרטונים שהמשתמש סימן "אהבתי" ביוטיוב. */
    suspend fun likedVideos(token: String): List<Video> = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/youtube/v3/videos" +
            "?part=snippet&myRating=like&maxResults=50"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw RuntimeException(apiError(resp.code, body))
            if (body.isEmpty()) return@use emptyList()
            parseVideos(body)
        }
    }

    /** כל המנויים (ערוצים) של המשתמש ביוטיוב, ממוין לפי א״ב, עם דפדוף עד 500. */
    suspend fun subscriptions(token: String): List<SubChannel> = withContext(Dispatchers.IO) {
        val out = mutableListOf<SubChannel>()
        var pageToken: String? = ""
        var pages = 0
        while (pageToken != null && pages < 10) {
            val url = buildString {
                append("https://www.googleapis.com/youtube/v3/subscriptions")
                append("?part=snippet&mine=true&maxResults=50&order=alphabetical")
                if (pageToken!!.isNotEmpty()) append("&pageToken=").append(pageToken)
            }
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .build()
            val body = http.newCall(request).execute().use { resp ->
                val text = resp.body?.string()
                if (!resp.isSuccessful) throw RuntimeException(apiError(resp.code, text.orEmpty()))
                text
            } ?: break
            val root = JSONObject(body)
            root.optJSONArray("items")?.let { items ->
                for (i in 0 until items.length()) {
                    val sn = items.optJSONObject(i)?.optJSONObject("snippet") ?: continue
                    val channelId = sn.optJSONObject("resourceId")?.optString("channelId").orEmpty()
                    if (channelId.isEmpty()) continue
                    val thumb = sn.optJSONObject("thumbnails")?.let { th ->
                        (th.optJSONObject("high") ?: th.optJSONObject("medium") ?: th.optJSONObject("default"))
                            ?.optString("url")
                    }.orEmpty()
                    out.add(SubChannel(channelId, sn.optString("title"), thumb))
                }
            }
            pageToken = root.optString("nextPageToken").ifEmpty { null }
            pages++
        }
        out.distinctBy { it.channelId }
    }

    /** סימון לייק (או ביטולו) ביוטיוב המקורי. דורש scope force-ssl. */
    suspend fun rate(token: String, videoId: String, like: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (videoId.isEmpty()) return@withContext false
        val rating = if (like) "like" else "none"
        val request = Request.Builder()
            .url("https://www.googleapis.com/youtube/v3/videos/rate?id=$videoId&rating=$rating")
            .header("Authorization", "Bearer $token")
            .post(ByteArray(0).toRequestBody(null))
            .build()
        runCatching {
            http.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private fun parseVideos(body: String): List<Video> {
        val out = mutableListOf<Video>()
        val items = JSONObject(body).optJSONArray("items") ?: return out
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val id = item.optString("id")
            if (id.isEmpty()) continue
            val sn = item.optJSONObject("snippet") ?: continue
            val title = sn.optString("title")
            val channel = sn.optString("channelTitle")
            val channelId = sn.optString("channelId")
            val thumb = sn.optJSONObject("thumbnails")?.let { th ->
                (th.optJSONObject("high") ?: th.optJSONObject("medium") ?: th.optJSONObject("default"))
                    ?.optString("url")
            } ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg"
            out.add(Video(id, title, channel, channelId, thumb, System.currentTimeMillis()))
        }
        return out
    }
}
