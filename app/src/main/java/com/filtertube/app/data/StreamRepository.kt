package com.filtertube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * תוצאת חילוץ stream — URLs שאפשר לתת ל-ExoPlayer.
 */
data class StreamData(
    val title: String,
    val uploaderName: String,
    val durationSec: Long,
    val viewCount: Long,
    val likeCount: Long,
    val description: String?,
    val thumbnailUrl: String?,
    // ה-URL הטוב ביותר — דחיפת ExoPlayer
    val bestVideoUrl: String,
    // אופציונלי — לניגון ברקע (אודיו בלבד)
    val bestAudioUrl: String?,
    // איכויות זמינות (לבחירה ידנית בעתיד)
    val qualities: List<VideoQuality>,
)

data class VideoQuality(
    val label: String,    // "720p", "1080p"...
    val url: String,
    val isHls: Boolean,
)

/**
 * מחלץ stream URLs מ-YouTube באמצעות NewPipeExtractor.
 *
 * רץ על הטלפון של המשתמש (IP ביתי) → YouTube לא חוסם.
 */
object StreamRepository {

    /**
     * מחלץ stream URLs לסרטון. עלול לקחת 2-5 שניות.
     */
    suspend fun getStream(videoId: String): StreamData = withContext(Dispatchers.IO) {
        val url = "https://www.youtube.com/watch?v=$videoId"
        val info = StreamInfo.getInfo(ServiceList.YouTube, url)

        // Muxed streams עם אודיו וביוואו ביחד
        val muxed = info.videoStreams?.filter { !it.isVideoOnly } ?: emptyList()
        // Video-only streams (יותר איכויות זמינות אבל צריך לערבב עם אודיו)
        val videoOnly = info.videoStreams?.filter { it.isVideoOnly } ?: emptyList()

        // עדיפות לmuxed (אין צורך בערבוב). אם אין — ניקח את ההגבוה ביותר
        val best = muxed.maxByOrNull { it.height ?: 0 }
            ?: videoOnly.maxByOrNull { it.height ?: 0 }
            ?: throw IllegalStateException("לא נמצא video stream")

        val audioBest = info.audioStreams?.maxByOrNull { it.bitrate } ?: info.audioStreams?.firstOrNull()

        val qualities = muxed.map { s ->
            VideoQuality(
                label = "${s.height ?: 0}p",
                url = s.content,
                isHls = false,
            )
        }

        StreamData(
            title = info.name ?: "",
            uploaderName = info.uploaderName ?: "",
            durationSec = info.duration,
            viewCount = info.viewCount,
            likeCount = info.likeCount,
            description = info.description?.content,
            thumbnailUrl = info.thumbnails?.maxByOrNull { it.height }?.url,
            bestVideoUrl = best.content,
            bestAudioUrl = audioBest?.content,
            qualities = qualities,
        )
    }
}
