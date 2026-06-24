package com.filtertube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * איכות וידאו זמינה. אם [audioUrl] לא null — מדובר בזרם וידאו-בלבד שצריך
 * למזג עם זרם אודיו נפרד (DASH). אחרת זה זרם משולב (muxed) שכבר כולל קול.
 */
data class StreamTrack(
    val height: Int,
    val label: String,
    val videoUrl: String,
    val audioUrl: String?,
)

data class StreamData(
    val title: String,
    val uploaderName: String,
    val channelId: String,
    val durationSec: Long,
    val viewCount: Long,
    val description: String?,
    val thumbnailUrl: String?,
    /** איכויות וידאו זמינות, ממוינות מהגבוהה לנמוכה */
    val tracks: List<StreamTrack>,
    /** זרם האודיו הטוב ביותר — למצב אודיו בלבד */
    val bestAudioUrl: String?,
    /** זרם וידאו משולב הטוב ביותר — להורדה */
    val bestVideoUrl: String,
    /** סרטונים קשורים — להפעלה אוטומטית (לפני סינון לרשימה הלבנה) */
    val related: List<Video>,
    /**
     * ה-User-Agent שבו *חייבים* לנגן את כתובות הזרם. יוטיוב מאמת את ה-UA מול
     * הלקוח שביקש את הזרם (IOS/VR/Web) — נגינה ב-UA שונה גורמת ל-CDN לחתוך את
     * הזרם אחרי כמה שניות. null = אפשר UA ברירת מחדל.
     */
    val streamUserAgent: String? = null,
)

object StreamRepository {

    suspend fun getStream(videoId: String): StreamData = coroutineScope {
        // שני הנתיבים רצים במקביל — מי שמחזיר מהר מנצח. NewPipe מתחיל מיד; ל-InnerTube
        // תקרת זמן, ואם חזר ראשון מבטלים את NewPipe. כך אין יותר המתנה לנתיב האיטי.
        val newpipe = async(Dispatchers.IO) { runCatching { extractViaNewPipe(videoId) }.getOrNull() }
        val inner = withTimeoutOrNull(4500) { runCatching { InnerTube.player(videoId) }.getOrNull() }
        if (inner != null) { newpipe.cancel(); return@coroutineScope inner }
        newpipe.await() ?: throw IllegalStateException("לא נמצא video stream")
    }

    // חילוץ דרך NewPipe — קוראים ישירות מה-extractor ועוטפים כל שדה ב-runCatching.
    // מדלגים על getDescription() — הוא קורא ל-URLDecoder.decode(String,Charset) שלא קיים
    // ב-API<33 ומפיל את האפליקציה (NoSuchMethodError הוא Error, לא Exception).
    private suspend fun extractViaNewPipe(videoId: String): StreamData = withContext(Dispatchers.IO) {
        val linkHandler = org.schabi.newpipe.extractor.services.youtube.linkHandler
            .YoutubeStreamLinkHandlerFactory.getInstance().fromId(videoId)
        val extractor = ServiceList.YouTube.getStreamExtractor(linkHandler)
        extractor.fetchPage()

        val allVideo = runCatching { extractor.videoStreams }.getOrNull().orEmpty()
        val videoOnlyList = runCatching { extractor.videoOnlyStreams }.getOrNull().orEmpty()
        val audioStreams = runCatching { extractor.audioStreams }.getOrNull().orEmpty()

        val muxed = allVideo.filter { !it.isVideoOnly && it.height > 0 }
        val videoOnly = (allVideo.filter { it.isVideoOnly } + videoOnlyList).filter { it.height > 0 }
        val audioBest = audioStreams.maxByOrNull { it.bitrate }

        // בונים רשימת איכויות: muxed (כוללים קול) קודם, אחר כך video-only (ממוזגים עם אודיו)
        val muxedTracks = muxed.map { StreamTrack(it.height, "${it.height}p", it.content, null) }
        val dashTracks = if (audioBest != null) {
            videoOnly.map { StreamTrack(it.height, "${it.height}p", it.content, audioBest.content) }
        } else emptyList()

        val tracks = (muxedTracks + dashTracks)
            .distinctBy { it.height }          // muxed מקבל עדיפות (מופיע ראשון)
            .sortedByDescending { it.height }

        if (tracks.isEmpty()) throw IllegalStateException("לא נמצא video stream")

        // להורדה — זרם משולב הגבוה ביותר (כך הקובץ כולל קול)
        val bestMuxed = muxed.maxByOrNull { it.height }?.content
            ?: tracks.first().videoUrl

        val channelId = extractChannelId(runCatching { extractor.uploaderUrl }.getOrNull()) ?: ""

        // הסרטונים הקשורים נטענים בנפרד ב-Playback אחרי תחילת הניגון — לא כאן.
        // כך מוסרים round-trip שלם מהמסלול הקריטי של טעינת הסרטון.
        val related = emptyList<Video>()

        StreamData(
            title = runCatching { extractor.name }.getOrNull().orEmpty(),
            uploaderName = runCatching { extractor.uploaderName }.getOrNull().orEmpty(),
            channelId = channelId,
            durationSec = runCatching { extractor.length }.getOrNull() ?: 0L,
            viewCount = runCatching { extractor.viewCount }.getOrNull() ?: 0L,
            description = null,   // מדלגים — זה מקור הקריסה ב-API<33
            thumbnailUrl = runCatching { extractor.thumbnails?.maxByOrNull { it.height }?.url }.getOrNull(),
            tracks = tracks,
            bestAudioUrl = audioBest?.content,
            bestVideoUrl = bestMuxed,
            related = related,
        )
    }

    private fun relatedToVideo(item: StreamInfoItem): Video? {
        val vid = extractVideoId(item.url) ?: return null
        val chId = extractChannelId(item.uploaderUrl) ?: ""
        val thumb = item.thumbnails?.maxByOrNull { it.height }?.url
            ?: "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
        return Video(vid, item.name ?: "", item.uploaderName ?: "", chId, thumb, System.currentTimeMillis())
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
