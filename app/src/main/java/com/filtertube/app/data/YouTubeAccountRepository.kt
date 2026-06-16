package com.filtertube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
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

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

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
            if (!resp.isSuccessful) throw RuntimeException("YouTube API ${resp.code}")
            val body = resp.body?.string() ?: return@use emptyList()
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
                if (!resp.isSuccessful) throw RuntimeException("YouTube API ${resp.code}")
                resp.body?.string()
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
