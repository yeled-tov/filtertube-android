package com.filtertube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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

    private fun parseVideos(body: String): List<Video> {
        val out = mutableListOf<Video>()
        val items = JSONObject(body).optJSONArray("items") ?: return out
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val id = item.optString("id").ifEmpty { continue }
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
