package com.filtertube.app.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object YouTubeRepository {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val iso8601Date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // ───────────────────────────────────────────────────────────────────────
    // FEED — RSS feeds (מהיר, ציבורי, ללא API key)
    // ───────────────────────────────────────────────────────────────────────
    /**
     * כמה בקשות RSS במקביל.
     *
     * קודם לכן כל 166 הערוצים נשלחו בבת אחת. הקריאות סינכרוניות
     * (`execute()`), ולכן מגבלות ה-Dispatcher של OkHttp לא חלות עליהן והן
     * באמת רצות יחד — עשרות חיבורים בו-זמנית לאותו מארח. תחת עומס יוטיוב
     * חונק חלק מהן, הן נכשלות בשקט ומוחזרת רשימה ריקה לכל ערוץ שנפל.
     *
     * כך קרה ש"HOME: 30 סרטונים מה-RSS" הופיע במקום ~2,300: רוב הערוצים
     * נכשלו. וזו גם הסיבה שהחיפוש נהיה איטי — האינדקס המקומי התרוקן,
     * ולכן כל חיפוש נאלץ ליפול ל-NewPipe במקום לענות מיד.
     */
    private const val FEED_CONCURRENCY = 8

    suspend fun fetchAllChannelsFeed(channels: List<Channel>): List<Video> = coroutineScope {
        val targets = channels.filter { it.youtubeChannelId.startsWith("UC") }
        if (targets.isEmpty()) return@coroutineScope emptyList()

        val gate = Semaphore(FEED_CONCURRENCY)
        val allLists = targets.map { channel ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    try { fetchChannelFeed(channel) } catch (e: Exception) {
                        android.util.Log.w("YouTubeRepository", "Feed failed for ${channel.name}: ${e.message}")
                        emptyList()
                    }
                }
            }
        }.awaitAll()

        val videos = allLists.flatten().filterNot { it.isShort }.sortedByDescending { it.publishedAt }
        val answered = allLists.count { it.isNotEmpty() }
        // נרשם ליומן האבחון ולא רק ל-Logcat: כשערוצים נופלים בשקט זה נראה
        // כמו "הפיד קטן משום מה" בלי שום רמז למה.
        if (answered < targets.size) {
            Diagnostics.log("FEED: $answered מתוך ${targets.size} ערוצים החזירו סרטונים")
        }
        videos
    }

    /** סרטוני ערוץ בודד לפי מזהה — להצגת תוכן של מנוי שנבחר. */
    suspend fun fetchChannelVideos(channelId: String, channelName: String): List<Video> =
        fetchChannelFeed(Channel(channelId, channelName, "general"))
            .filterNot { it.isShort }
            .sortedByDescending { it.publishedAt }

    private suspend fun fetchChannelFeed(channel: Channel): List<Video> = withContext(Dispatchers.IO) {
        val url = "https://www.youtube.com/feeds/videos.xml?channel_id=${channel.youtubeChannelId}"
        val request = Request.Builder().url(url).header("User-Agent", "FilterTube/1.0").build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use emptyList()
            val xml = response.body?.string() ?: return@use emptyList()
            parseChannelXml(xml, channel)
        }
    }

    private fun parseChannelXml(xml: String, channel: Channel): List<Video> {
        val videos = mutableListOf<Video>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var event = parser.eventType
        var inEntry = false
        var vId: String? = null
        var vTitle: String? = null
        var vPublished = 0L
        var vThumb: String? = null

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "entry" -> { inEntry = true; vId = null; vTitle = null; vPublished = 0; vThumb = null }
                    "yt:videoId" -> if (inEntry) vId = parser.nextText()
                    "title" -> if (inEntry && vTitle == null) vTitle = parser.nextText()
                    "published" -> if (inEntry) try {
                        val cleaned = parser.nextText().substringBefore("+").substringBefore("Z").trim()
                        vPublished = iso8601Date.parse(cleaned)?.time ?: 0L
                    } catch (_: Exception) {}
                    "media:thumbnail" -> if (inEntry) vThumb = parser.getAttributeValue(null, "url")
                }
                XmlPullParser.END_TAG -> if (parser.name == "entry" && inEntry) {
                    val id = vId; val title = vTitle
                    if (!id.isNullOrEmpty() && !title.isNullOrEmpty()) {
                        videos.add(
                            Video(
                                id = id,
                                title = title,
                                channelName = channel.name,
                                channelId = channel.youtubeChannelId,
                                thumbnailUrl = vThumb ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                                publishedAt = vPublished
                            )
                        )
                    }
                    inEntry = false
                }
            }
            event = parser.next()
        }
        return videos
    }

    // ───────────────────────────────────────────────────────────────────────
    // SEARCH — NewPipeExtractor, מסונן לערוצים מאושרים בלבד
    // ───────────────────────────────────────────────────────────────────────
    /**
     * חיפוש מסונן לערוצים מאושרים. **הדרגתי** — [onPartial] נקרא אחרי כל עמוד
     * עם כל התוצאות שנאספו עד כה.
     */
    suspend fun search(
        query: String,
        channels: List<Channel>,
        onPartial: (List<Video>) -> Unit = {},
    ): List<Video> = withContext(Dispatchers.IO) {
        val allowedIds = channels.map { it.youtubeChannelId }.toHashSet()
        val qh = ServiceList.YouTube.searchQHFactory.fromQuery(query, listOf("videos"), "")

        val collected = LinkedHashMap<String, Video>()
        fun ingest(items: List<Any?>) {
            items.filterIsInstance<StreamInfoItem>()
                .mapNotNull { item -> toVideo(item) }
                // סינון לפי מזהה ערוץ בלבד. התאמה לפי *שם* ערוץ הייתה חור בסינון:
                // כל ערוץ ביוטיוב יכול לקרוא לעצמו בשם של ערוץ מאושר ולעבור.
                .filter { it.channelId in allowedIds }
                .forEach { if (!collected.containsKey(it.id)) collected[it.id] = it }
        }

        val info = SearchInfo.getInfo(ServiceList.YouTube, qh)
        ingest(info.relatedItems)
        onPartial(collected.values.toList())

        var nextPage = info.nextPage
        var pagesFetched = 0
        while (nextPage != null && pagesFetched < 5) {
            try {
                val more = SearchInfo.getMoreItems(ServiceList.YouTube, qh, nextPage)
                ingest(more.items)
                onPartial(collected.values.toList())
                nextPage = more.nextPage
                pagesFetched++
            } catch (e: Exception) {
                android.util.Log.w("YouTubeRepository", "search page failed: ${e.message}")
                break
            }
        }

        collected.values.toList()
    }

    // ───────────────────────────────────────────────────────────────────────
    // SHORTS — מהטאב "Shorts" של ערוצים מאושרים
    // ───────────────────────────────────────────────────────────────────────
    suspend fun fetchShorts(channels: List<Channel>): List<Video> = coroutineScope {
        val sample = channels.filter { it.youtubeChannelId.startsWith("UC") }.shuffled().take(12)

        val lists = sample.map { channel ->
            async(Dispatchers.IO) {
                try { fetchChannelShorts(channel) } catch (e: Exception) {
                    android.util.Log.w("YouTubeRepository", "Shorts failed for ${channel.name}: ${e.message}")
                    emptyList()
                }
            }
        }.awaitAll()

        lists.flatten().shuffled()
    }

    private fun fetchChannelShorts(channel: Channel): List<Video> {
        val url = "https://www.youtube.com/channel/${channel.youtubeChannelId}"
        val info = ChannelInfo.getInfo(ServiceList.YouTube, url)
        val shortsTab = info.tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.SHORTS) }
            ?: return emptyList()
        val tabInfo = ChannelTabInfo.getInfo(ServiceList.YouTube, shortsTab)
        return tabInfo.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { toVideo(it, channel.name, channel.youtubeChannelId) }
            .take(8)
    }

    // ───────────────────────────────────────────────────────────────────────
    // המרת StreamInfoItem → Video עם metadata מלא (duration, viewCount, uploadDate)
    // ───────────────────────────────────────────────────────────────────────
    private fun toVideo(item: StreamInfoItem, fallbackChannel: String? = null, fallbackChannelId: String? = null): Video? {
        val videoId = extractVideoId(item.url) ?: return null
        val channelId = fallbackChannelId ?: extractChannelId(item.uploaderUrl) ?: ""
        val thumb = item.thumbnails?.maxByOrNull { it.height }?.url
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        val uploadDateMillis = runCatching {
            item.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli()
        }.getOrNull() ?: 0L

        val durationSec = runCatching { item.duration }.getOrNull()?.takeIf { it > 0 } ?: 0L
        val viewCount = runCatching { item.viewCount }.getOrNull()?.takeIf { it > 0 } ?: 0L

        return Video(
            id = videoId,
            title = item.name ?: "",
            channelName = fallbackChannel ?: item.uploaderName ?: "",
            channelId = channelId,
            thumbnailUrl = thumb,
            publishedAt = uploadDateMillis,
            isShort = item.url?.contains("/shorts/", ignoreCase = true) == true,
            durationSec = durationSec,
            viewCount = viewCount
        )
    }

    private fun extractVideoId(url: String?): String? {
        if (url == null) return null
        Regex("[?&]v=([A-Za-z0-9_-]{11})").find(url)?.let { return it.groupValues[1] }
        Regex("/shorts/([A-Za-z0-9_-]{11})").find(url)?.let { return it.groupValues[1] }
        Regex("youtu\\.be/([A-Za-z0-9_-]{11})").find(url)?.let { return it.groupValues[1] }
        return null
    }

    private fun extractChannelId(url: String?): String? {
        if (url == null) return null
        return Regex("/channel/(UC[\\w-]+)").find(url)?.groupValues?.get(1)
    }
}
