package com.filtertube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.TimeUnit

class YouTubeDataApiException(
    val statusCode: Int,
    val errorBody: String,
    message: String
) : Exception(message)

/**
 * חיפוש מסונן לרשימה הלבנה דרך ה-YouTube Data API הרשמי.
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
     * מנותב מחדש לשגיאה מפורשת במידה וה-API נכשל (לדוגמה 403 / מכסה מבוזבזת / key invalid).
     */
    suspend fun search(query: String, channels: List<Channel>, live: Boolean = false): List<Video> = withContext(Dispatchers.IO) {
        val allowed = channels.map { it.youtubeChannelId }.toHashSet()
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val event = if (live) "&eventType=live" else ""
        val url = "$BASE/search?part=snippet&type=video$event&maxResults=40&q=$q&key=$KEY"

        Diagnostics.log("SEARCH_START query=$query")
        Diagnostics.log("SEARCH_CHANNELS_COUNT allowed=${allowed.size}")

        val req = Request.Builder().url(url).build()
        var httpCode = -1
        var responseBody = ""

        val resp = runCatching {
            http.newCall(req).execute()
        }.getOrElse { e ->
            Diagnostics.log("SEARCH_DATA_API_FAILED network error=${e.message}")
            throw e
        }

        resp.use { response ->
            httpCode = response.code
            responseBody = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                Diagnostics.log("SEARCH_DATA_API_FAILED status=$httpCode body=$responseBody")
                throw YouTubeDataApiException(
                    statusCode = httpCode,
                    errorBody = responseBody,
                    message = "YouTube Data API HTTP $httpCode: $responseBody"
                )
            }

            val json = JSONObject(responseBody)
            val items = json.optJSONArray("items") ?: run {
                Diagnostics.log("SEARCH_DATA_API_SUCCESS count=0 (missing items array)")
                return@withContext emptyList()
            }

            val out = LinkedHashMap<String, Video>()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val s = item.optJSONObject("snippet") ?: continue
                val vid = item.optJSONObject("id")?.optString("videoId").orEmpty()
                if (vid.isBlank() || out.containsKey(vid)) continue
                val chId = s.optString("channelId")
                if (chId !in allowed) continue
                val thumb = s.optJSONObject("thumbnails")?.optJSONObject("high")?.optString("url")
                    ?: "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
                out[vid] = Video(
                    vid,
                    s.optString("title"),
                    s.optString("channelTitle"),
                    chId,
                    thumb,
                    System.currentTimeMillis()
                )
            }

            val list = out.values.toList()
            Diagnostics.log("SEARCH_DATA_API_SUCCESS count=${list.size}")
            return@withContext list
        }
    }

    // מטמון לשידורים חיים — חיפוש לפי ערוץ יקר במכסה (100 יח'), אז שומרים ל-5 דק'.
    @Volatile private var liveCacheTime = 0L
    @Volatile private var liveCache: List<Video> = emptyList()
    @Volatile private var liveCacheKey = ""

    /**
     * שידורים חיים *פעילים כעת* מתוך [channels] (בדיקה לכל ערוץ, מקבילות מוגבלת).
     */
    suspend fun liveFromChannels(channels: List<Channel>, force: Boolean = false): List<Video> = withContext(Dispatchers.IO) {
        val cacheKey = channels.map { it.youtubeChannelId }.sorted().joinToString(",")
        if (!force && cacheKey == liveCacheKey && System.currentTimeMillis() - liveCacheTime < 5 * 60_000L) {
            return@withContext liveCache
        }
        val out = Collections.synchronizedList(mutableListOf<Video>())
        val sem = Semaphore(5)
        coroutineScope {
            channels.map { ch ->
                async {
                    sem.withPermit {
                        runCatching {
                            val url = "$BASE/search?part=snippet&type=video&eventType=live&maxResults=1" +
                                "&channelId=${ch.youtubeChannelId}&key=$KEY"
                            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                                if (!resp.isSuccessful) return@use
                                val items = JSONObject(resp.body?.string() ?: return@use).optJSONArray("items") ?: return@use
                                for (i in 0 until items.length()) {
                                    val item = items.optJSONObject(i) ?: continue
                                    val vid = item.optJSONObject("id")?.optString("videoId").orEmpty()
                                    if (vid.isBlank()) continue
                                    val s = item.optJSONObject("snippet") ?: continue
                                    val thumb = s.optJSONObject("thumbnails")?.optJSONObject("high")?.optString("url")
                                        ?: "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
                                    out.add(Video(vid, s.optString("title"), s.optString("channelTitle"),
                                        ch.youtubeChannelId, thumb, System.currentTimeMillis()))
                                }
                            }
                        }
                    }
                }
            }.awaitAll()
        }
        liveCache = out.distinctBy { it.id }
        liveCacheKey = cacheKey
        liveCacheTime = System.currentTimeMillis()
        liveCache
    }
}
