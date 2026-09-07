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
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Locale
import java.util.TimeZone
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

    private fun parseIsoDate(str: String?): Long {
        if (str.isNullOrBlank()) return 0L
        val clean = str.substringBefore(".").substringBefore("+").substringBefore("Z").trim()
        return runCatching {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            fmt.parse(clean)?.time
        }.getOrNull() ?: 0L
    }

    /**
     * חיפוש מסונן לרשימה הלבנה — מחזיר רק תוצאות מערוצים מאושרים.
     * משיג metadata מלא (תאריך פרסום אמיתי, משך ומספר צפיות) בבקשה מקובצת יחידה (batched).
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

            // שלב א': אוספים סרטונים מערוצים מאושרים ומזהי videoId
            val candidateVideos = LinkedHashMap<String, Video>()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val s = item.optJSONObject("snippet") ?: continue
                val vid = item.optJSONObject("id")?.optString("videoId").orEmpty()
                if (vid.isBlank() || candidateVideos.containsKey(vid)) continue
                val chId = s.optString("channelId")
                if (chId !in allowed) continue

                val pubDateStr = s.optString("publishedAt")
                val pubMillis = parseIsoDate(pubDateStr)
                val thumb = s.optJSONObject("thumbnails")?.optJSONObject("high")?.optString("url")
                    ?: "https://i.ytimg.com/vi/$vid/hqdefault.jpg"

                candidateVideos[vid] = Video(
                    id = vid,
                    title = s.optString("title"),
                    channelName = s.optString("channelTitle"),
                    channelId = chId,
                    thumbnailUrl = thumb,
                    publishedAt = pubMillis,
                    durationSec = 0L,
                    viewCount = 0L
                )
            }

            if (candidateVideos.isEmpty()) {
                Diagnostics.log("SEARCH_DATA_API_SUCCESS count=0 (no candidates in allowed channels)")
                return@withContext emptyList()
            }

            // שלב ב': בקשת details מרוכזת אחת לכל ה-IDs ביחד (batched videos request)
            val videoIdsParam = candidateVideos.keys.joinToString(",")
            val detailsUrl = "$BASE/videos?part=snippet,contentDetails,statistics&id=$videoIdsParam&key=$KEY"

            val enrichedVideos = LinkedHashMap<String, Video>(candidateVideos)

            runCatching {
                http.newCall(Request.Builder().url(detailsUrl).build()).execute().use { detailsResp ->
                    if (detailsResp.isSuccessful) {
                        val detailsJson = JSONObject(detailsResp.body?.string().orEmpty())
                        val detailItems = detailsJson.optJSONArray("items") ?: return@use
                        for (j in 0 until detailItems.length()) {
                            val dItem = detailItems.optJSONObject(j) ?: continue
                            val dVid = dItem.optString("id")
                            val baseVid = candidateVideos[dVid] ?: continue

                            val dSnippet = dItem.optJSONObject("snippet")
                            val dPubStr = dSnippet?.optString("publishedAt")
                            val dPubMillis = parseIsoDate(dPubStr).takeIf { it > 0L } ?: baseVid.publishedAt

                            val contentDetails = dItem.optJSONObject("contentDetails")
                            val durationIso = contentDetails?.optString("duration")
                            val durationSec = IsoDurationParser.parseToSeconds(durationIso)

                            val stats = dItem.optJSONObject("statistics")
                            val viewCount = stats?.optString("viewCount")?.toLongOrNull() ?: 0L

                            enrichedVideos[dVid] = baseVid.copy(
                                publishedAt = dPubMillis,
                                durationSec = durationSec,
                                viewCount = viewCount
                            )
                        }
                    }
                }
            }

            val list = enrichedVideos.values.toList()
            Diagnostics.log("SEARCH_DATA_API_SUCCESS count=${list.size} (enriched with duration & views)")
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
                                    val pubDateStr = s.optString("publishedAt")
                                    val pubMillis = parseIsoDate(pubDateStr)
                                    out.add(Video(vid, s.optString("title"), s.optString("channelTitle"),
                                        ch.youtubeChannelId, thumb, pubMillis))
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
