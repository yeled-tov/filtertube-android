package com.filtertube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * חיפוש מהיר דרך ה-YouTube Data API הרשמי — קריאה אחת, מהיר בהרבה מחילוץ NewPipe
 * (שעשה חיפוש + עד 5 עמודי המשך). הניגון עדיין דרך חילוץ; רק החיפוש כאן.
 */
object YouTubeDataApi {
    private const val KEY = "AIzaSyDLAo5cUv4lt1Tsad50aMGFE0jl-mfRtOk"
    private const val BASE = "https://www.googleapis.com/youtube/v3"

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * חיפוש מסונן לרשימה הלבנה — מחזיר רק תוצאות מערוצים מאושרים.
     * [live] = רק שידורים חיים פעילים (eventType=live).
     */
    suspend fun search(query: String, channels: List<Channel>, live: Boolean = false): List<Video> = withContext(Dispatchers.IO) {
        val allowed = channels.map { it.youtubeChannelId }.toHashSet()
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val event = if (live) "&eventType=live" else ""
        val url = "$BASE/search?part=snippet&type=video$event&maxResults=40&q=$q&key=$KEY"
        val out = LinkedHashMap<String, Video>()
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val items = JSONObject(resp.body?.string() ?: return@use)
                    .optJSONArray("items") ?: return@use
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val s = item.optJSONObject("snippet") ?: continue
                    val vid = item.optJSONObject("id")?.optString("videoId").orEmpty()
                    if (vid.isBlank() || out.containsKey(vid)) continue
                    val chId = s.optString("channelId")
                    if (chId !in allowed) continue
                    val thumb = s.optJSONObject("thumbnails")?.optJSONObject("high")?.optString("url")
                        ?: "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
                    out[vid] = Video(vid, s.optString("title"), s.optString("channelTitle"),
                        chId, thumb, System.currentTimeMillis())
                }
            }
        }
        out.values.toList()
    }
}
