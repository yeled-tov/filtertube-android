package com.filtertube.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList

/**
 * מנוע פתרון זרמי NewPipe — המנגנון האמין כנתיב גיבוי (Fallback).
 */
class NewPipeResolver : StreamResolver {

    override val name: String = "NewPipe"
    private val clientKey: String = "NewPipe"

    override suspend fun resolve(videoId: String, force: Boolean): StreamData? = withContext(Dispatchers.IO) {
        if (!RemoteConfig.isResolverEnabled(clientKey, default = true)) {
            Diagnostics.log("$name $videoId: מנוע מבוטל ב-RemoteConfig")
            return@withContext null
        }

        if (!force && !ResolverHealthMonitor.isAvailable(clientKey)) {
            Diagnostics.log("$name $videoId: מנוע ב-cooldown (נכשל לאחרונה)")
            return@withContext null
        }

        val t0 = System.currentTimeMillis()
        runCatching {
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

            val muxedTracks = muxed.map {
                StreamTrack(it.height, "${it.height}p", it.content, null, it.format?.mimeType.orEmpty())
            }
            val dashTracks = if (audioBest != null) {
                val audioMime = audioBest.format?.mimeType.orEmpty()
                videoOnly.map {
                    StreamTrack(
                        it.height, "${it.height}p", it.content, audioBest.content,
                        it.format?.mimeType.orEmpty(), audioMime,
                    )
                }
            } else emptyList()

            val vodTracks = (muxedTracks + dashTracks)
                .distinctBy { it.height }
                .sortedByDescending { it.height }

            val hls = runCatching { extractor.hlsUrl }.getOrNull()
            val live = vodTracks.isEmpty() && !hls.isNullOrEmpty()
            val tracks = if (live) listOf(StreamTrack(0, "שידור חי", hls!!, null)) else vodTracks

            if (tracks.isEmpty()) throw IllegalStateException("לא נמצא video stream ב-NewPipe")

            val bestMuxed = if (live) hls!! else (muxed.maxByOrNull { it.height }?.content ?: tracks.first().videoUrl)
            val channelId = extractChannelId(runCatching { extractor.uploaderUrl }.getOrNull()) ?: ""

            val elapsedMs = System.currentTimeMillis() - t0

            val streamData = StreamData(
                title = runCatching { extractor.name }.getOrNull().orEmpty(),
                uploaderName = runCatching { extractor.uploaderName }.getOrNull().orEmpty(),
                channelId = channelId,
                durationSec = runCatching { extractor.length }.getOrNull() ?: 0L,
                viewCount = runCatching { extractor.viewCount }.getOrNull() ?: 0L,
                description = null,
                thumbnailUrl = runCatching { extractor.thumbnails?.maxByOrNull { it.height }?.url }.getOrNull(),
                tracks = tracks,
                bestAudioUrl = audioBest?.content,
                bestVideoUrl = bestMuxed,
                related = emptyList(),
                streamUserAgent = null
            )

            if (!StreamValidator.validateBasic(streamData)) {
                throw IllegalStateException("Basic stream validation failed in NewPipe")
            }

            ResolverHealthMonitor.recordSuccess(clientKey)
            Diagnostics.log("$name $videoId: tracks=${tracks.size} SUCCESS (${elapsedMs}ms)")
            streamData
        }.getOrElse { e ->
            val elapsedMs = System.currentTimeMillis() - t0
            val reason = e.message ?: "Unknown error"

            // ── ביטול אינו כישלון ──────────────────────────────────────────
            // המנועים רצים במרוץ, וברגע שאחד מנצח כל השאר מבוטלים. כשהביטול
            // תופס את NewPipe באמצע fetchPage, ההפרעה לשקע מגיעה לכאן כחריגת
            // IO רגילה ("Socket closed", "interrupted") — ונרשמה ככישלון של
            // המנוע.
            //
            // מרגע שמנוע ה-iOS התחיל לנצח ב-230ms מול 1,700ms, זה קרה כמעט
            // בכל סרטון: שלושה ניצחונות של iOS הכניסו את NewPipe ל-cooldown.
            // משם התגלגלה ספירלה — כל המנועים בצינון, כישלון תוך 0ms, ושום
            // סרטון לא מתנגן.
            if (!isActive || e is CancellationException) {
                Diagnostics.log("$name $videoId: בוטל — מנוע אחר כבר ניצח (${elapsedMs}ms)")
                return@getOrElse null
            }

            Diagnostics.log("$name $videoId: $reason FAILED (${elapsedMs}ms)")

            // סרטונים מוגבלים גיל/ארגון אינם פגם במנוע החילוץ עצמו
            val isRestricted = reason.contains("restricted", ignoreCase = true) ||
                reason.contains("age", ignoreCase = true) ||
                reason.contains("flagged", ignoreCase = true)

            if (!isRestricted) {
                ResolverHealthMonitor.recordFailure(clientKey, reason)
            }
            null
        }
    }

    private fun extractChannelId(url: String?): String? {
        if (url == null) return null
        return Regex("/channel/(UC[\\w-]+)").find(url)?.groupValues?.get(1)
    }
}
