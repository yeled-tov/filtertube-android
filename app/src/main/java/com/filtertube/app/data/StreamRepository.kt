package com.filtertube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class StreamData(
    val title: String,
    val uploaderName: String,
    val channelId: String,
    val durationSec: Long,
    val viewCount: Long,
    val description: String?,
    val thumbnailUrl: String?,
    val bestVideoUrl: String,
    val bestAudioUrl: String?,
    /** סרטונים קשורים — להפעלה אוטומטית (לפני סינון לרשימה הלבנה) */
    val related: List<Video>,
)

object StreamRepository {

    suspend fun getStream(videoId: String): StreamData = withContext(Dispatchers.IO) {
        val url = "https://www.youtube.com/watch?v=$videoId"
        val info = StreamInfo.getInfo(ServiceList.YouTube, url)

        val muxed = info.videoStreams.orEmpty().filter { !it.isVideoOnly }
        val videoOnly = info.videoStreams.orEmpty().filter { it.isVideoOnly }

        val best = muxed.maxByOrNull { it.height }
            ?: videoOnly.maxByOrNull { it.height }
            ?: throw IllegalStateException("לא נמצא video stream")

        val audioBest = info.audioStreams.orEmpty().maxByOrNull { it.bitrate }

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
            bestVideoUrl = best.content,
            bestAudioUrl = audioBest?.content,
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
