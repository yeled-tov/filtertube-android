package com.filtertube.app.data

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * מושך את הסרטונים האחרונים מערוצי YouTube באמצעות RSS feeds.
 *
 * RSS feeds של YouTube הם ציבוריים, חינמיים, ולא דורשים API key:
 *   GET https://www.youtube.com/feeds/videos.xml?channel_id=UCxxx
 *
 * חשוב: זה רץ על הטלפון של המשתמש (IP ביתי) — YouTube לא חוסם.
 */
object YouTubeRepository {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val iso8601Date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * מושך את הסרטונים האחרונים מכל הערוצים, ממוין מהחדש לישן.
     */
    suspend fun fetchAllChannelsFeed(channels: List<Channel>): List<Video> = coroutineScope {
        // מקבילי — כל ערוץ מובא בכוח עצמו
        val allLists = channels
            .filter { it.youtubeChannelId.startsWith("UC") }
            .map { channel ->
                async(Dispatchers.IO) {
                    try {
                        fetchChannelFeed(channel)
                    } catch (e: Exception) {
                        android.util.Log.w(
                            "YouTubeRepository",
                            "Failed for ${channel.name}: ${e.message}"
                        )
                        emptyList()
                    }
                }
            }
            .awaitAll()

        allLists.flatten().sortedByDescending { it.publishedAt }
    }

    /**
     * מושך עד 15 סרטונים אחרונים מערוץ אחד.
     */
    private suspend fun fetchChannelFeed(channel: Channel): List<Video> = withContext(Dispatchers.IO) {
        val url = "https://www.youtube.com/feeds/videos.xml?channel_id=${channel.youtubeChannelId}"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "FilterTube/1.0")
            .build()

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
        var currentVideoId: String? = null
        var currentTitle: String? = null
        var currentPublished: Long = 0
        var currentThumbnail: String? = null

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "entry" -> {
                            inEntry = true
                            currentVideoId = null
                            currentTitle = null
                            currentPublished = 0
                            currentThumbnail = null
                        }
                        "yt:videoId" -> if (inEntry) currentVideoId = parser.nextText()
                        "title" -> if (inEntry && currentTitle == null) currentTitle = parser.nextText()
                        "published" -> if (inEntry) {
                            try {
                                // ISO 8601 format: 2024-01-15T10:30:00+00:00
                                val cleaned = parser.nextText().substringBefore("+").substringBefore("Z").trim()
                                currentPublished = iso8601Date.parse(cleaned)?.time ?: 0L
                            } catch (_: Exception) {}
                        }
                        "media:thumbnail" -> if (inEntry) {
                            currentThumbnail = parser.getAttributeValue(null, "url")
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "entry" && inEntry) {
                        val id = currentVideoId
                        val title = currentTitle
                        if (!id.isNullOrEmpty() && !title.isNullOrEmpty()) {
                            videos.add(
                                Video(
                                    id = id,
                                    title = title,
                                    channelName = channel.name,
                                    channelId = channel.youtubeChannelId,
                                    thumbnailUrl = currentThumbnail
                                        ?: "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                                    publishedAt = currentPublished,
                                )
                            )
                        }
                        inEntry = false
                    }
                }
            }
            event = parser.next()
        }
        return videos
    }
}
