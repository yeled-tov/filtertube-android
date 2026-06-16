package com.filtertube.app.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    suspend fun fetchAllChannelsFeed(channels: List<Channel>): List<Video> = coroutineScope {
        val allLists = channels
            .filter { it.youtubeChannelId.startsWith("UC") }
            .map { channel ->
                async(Dispatchers.IO) {
                    try { fetchChannelFeed(channel) } catch (e: Exception) {
                        android.util.Log.w("YouTubeRepository", "Feed failed for ${channel.name}: ${e.message}")
                        emptyList()
                    }
                }
            }
            .awaitAll()
        allLists.flatten().sortedByDescending { it.publishedAt }
    }

    /** סרטוני ערוץ בודד לפי מזהה — להצגת תוכן של מנוי שנבחר. */
    suspend fun fetchChannelVideos(channelId: String, channelName: String): List<Video> =
        fetchChannelFeed(Channel(channelId, channelName, "general"))
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
                        videos.add(Video(id, title, channel.name, channel.youtubeChannelId,
                            vThumb ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg", vPublished))
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
     * עם כל התוצאות שנאספו עד כה, כך שה-UI מציג תוצאות מיד עם העמוד הראשון
     * במקום להמתין לכל 6 העמודים. מחזיר את הרשימה הסופית בסוף.
     */
    suspend fun search(
        query: String,
        channels: List<Channel>,
        onPartial: (List<Video>) -> Unit = {},
    ): List<Video> = withContext(Dispatchers.IO) {
        val allowedIds = channels.map { it.youtubeChannelId }.toHashSet()
        val allowedNames = channels.map { it.name.trim().lowercase() }.toHashSet()
        val qh = ServiceList.YouTube.searchQHFactory.fromQuery(query, listOf("videos"), "")

        // אוסף שומר סדר ומונע כפילויות
        val collected = LinkedHashMap<String, Video>()
        fun ingest(items: List<Any?>) {
            items.filterIsInstance<StreamInfoItem>()
                .mapNotNull { item -> toVideo(item) }
                .filter { it.channelId in allowedIds || it.channelName.trim().lowercase() in allowedNames }
                .forEach { if (!collected.containsKey(it.id)) collected[it.id] = it }
        }

        val info = SearchInfo.getInfo(ServiceList.YouTube, qh)
        ingest(info.relatedItems)
        onPartial(collected.values.toList())   // ← תוצאות ראשונות מופיעות כאן מיד

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
    // SHORTS — מהטאב "Shorts" של ערוצים מאושרים (כך זה תמיד רק מאושרים)
    // ───────────────────────────────────────────────────────────────────────
    suspend fun fetchShorts(channels: List<Channel>): List<Video> = coroutineScope {
        // דגימה של עד 12 ערוצים בכל פעם (לשמור מהירות), ערבוב להגוון
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
    // המרת StreamInfoItem → Video
    // ───────────────────────────────────────────────────────────────────────
    private fun toVideo(item: StreamInfoItem, fallbackChannel: String? = null, fallbackChannelId: String? = null): Video? {
        val videoId = extractVideoId(item.url) ?: return null
        val channelId = fallbackChannelId ?: extractChannelId(item.uploaderUrl) ?: ""
        val thumb = item.thumbnails?.maxByOrNull { it.height }?.url
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        return Video(
            id = videoId,
            title = item.name ?: "",
            channelName = fallbackChannel ?: item.uploaderName ?: "",
            channelId = channelId,
            thumbnailUrl = thumb,
            publishedAt = System.currentTimeMillis(), // search/shorts אין תאריך מדויק
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
