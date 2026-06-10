package com.filtertube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

data class StreamData(
    val title: String,
    val uploaderName: String,
    val durationSec: Long,
    val viewCount: Long,
    val description: String?,
    val bestVideoUrl: String,
    val bestAudioUrl: String?,
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

        StreamData(
            title = info.name.orEmpty(),
            uploaderName = info.uploaderName.orEmpty(),
            durationSec = info.duration,
            viewCount = info.viewCount,
            description = info.description?.content,
            bestVideoUrl = best.content,
            bestAudioUrl = audioBest?.content,
        )
    }
}
