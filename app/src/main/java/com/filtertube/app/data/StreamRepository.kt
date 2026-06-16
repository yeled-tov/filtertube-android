package com.filtertube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
)

object StreamRepository {

    suspend fun getStream(videoId: String): StreamData = withContext(Dispatchers.IO) {
        val url = "https://www.youtube.com/watch?v=$videoId"
        val info = StreamInfo.getInfo(ServiceList.YouTube, url)

        val muxed = info.videoStreams.orEmpty().filter { !it.isVideoOnly && it.height > 0 }
        val videoOnly = info.videoStreams.orEmpty().filter { it.isVideoOnly && it.height > 0 }

        val audioBest = info.audioStreams.orEmpty().maxByOrNull { it.bitrate }

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

        val channelId = extractChannelId(info.uploaderUrl) ?: ""

        // סרטונים קשורים
        val related = info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .mapNotNull { item -> relatedToVideo(item) }

        StreamData(
            title = info.name.orEmpty(),
            uploaderName = info.uploaderName.orEmpty(),
            channelId = channelId,
            durationSec = info.duration,
            viewCount = info.viewCount,
            description = info.description?.content,
            thumbnailUrl = info.thumbnails?.maxByOrNull { it.height }?.url,
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
