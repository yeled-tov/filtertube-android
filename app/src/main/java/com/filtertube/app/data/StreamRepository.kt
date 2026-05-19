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
    val description: String?,
    val thumbnailUrl: String?,
    /** ה-URL הטוב ביותר ל-ExoPlayer (mp4 muxed) */
    val bestVideoUrl: String,
    /** אופציונלי — לניגון ברקע בעתיד */
    val bestAudioUrl: String?,
)

/**
 * מחלץ stream URLs מ-YouTube באמצעות NewPipeExtractor.
 *
 * רץ על הטלפון של המשתמש (IP ביתי) → YouTube לא חוסם.
 */
object StreamRepository {

    suspend fun getStream(videoId: String): StreamData = withContext(Dispatchers.IO) {
        val url = "https://www.youtube.com/watch?v=$videoId"
        val info = StreamInfo.getInfo(ServiceList.YouTube, url)

        // Muxed streams עם אודיו ווידאו ביחד (פחות איכויות אבל יותר פשוט)
        val muxed = info.videoStreams.orEmpty().filter { !it.isVideoOnly }
        val videoOnly = info.videoStreams.orEmpty().filter { it.isVideoOnly }

        // עדיפות לmuxed; אם אין → הגבוה ביותר מ-videoOnly
        @Suppress("DEPRECATION")
        val best = muxed.maxByOrNull { it.height }
            ?: videoOnly.maxByOrNull { it.height }
            ?: throw IllegalStateException("לא נמצא video stream לסרטון")

        val audioBest = info.audioStreams.orEmpty().maxByOrNull { it.bitrate }

        // Description ו-thumbnail — שונים בין גרסאות של NewPipeExtractor.
        // משתמשים בreflection-safe access דרך try/catch
        val description = try {
            info.description?.content
        } catch (_: Throwable) { null }

        val thumbnail = try {
            // ננסה תחילה .thumbnails (גרסאות חדשות), אם נכשל → .thumbnailUrl
            val thumbsField = info.javaClass.methods.firstOrNull {
                it.name == "getThumbnails" && it.parameterCount == 0
            }
            if (thumbsField != null) {
                @Suppress("UNCHECKED_CAST")
                val list = thumbsField.invoke(info) as? List<Any?>
                list?.firstOrNull()?.let { img ->
                    img.javaClass.methods.firstOrNull { it.name == "getUrl" }
                        ?.invoke(img) as? String
                }
            } else {
                // fallback ישן
                val urlField = info.javaClass.methods.firstOrNull { it.name == "getThumbnailUrl" }
                urlField?.invoke(info) as? String
            }
        } catch (_: Throwable) {
            "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        }

        StreamData(
            title = info.name.orEmpty(),
            uploaderName = info.uploaderName.orEmpty(),
            durationSec = info.duration,
            viewCount = info.viewCount,
            description = description,
            thumbnailUrl = thumbnail ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            bestVideoUrl = best.content ?: best.url.orEmpty(),
            bestAudioUrl = audioBest?.content ?: audioBest?.url,
        )
    }
}
