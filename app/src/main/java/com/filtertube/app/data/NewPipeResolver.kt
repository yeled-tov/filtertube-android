package com.filtertube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList

/**
 * מנוע פתרון זרמי NewPipe — המנגנון האמין כנתיב גיבוי (Fallback).
 */
class NewPipeResolver : StreamResolver {

    override val name: String = "NewPipe"
    private val clientKey: String = "NewPipe"

    override suspend fun resolve(videoId: String): StreamData? = withContext(Dispatchers.IO) {
        if (!RemoteConfig.isResolverEnabled(clientKey, default = true)) {
            Diagnostics.log("$name $videoId: מנוע מבוטל ב-RemoteConfig")
            return@withContext null
        }

        if (!ResolverHealthMonitor.isAvailable(clientKey)) {
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

            val muxedTracks = muxed.map { StreamTrack(it.height, "${it.height}p", it.content, null) }
            val dashTracks = if (audioBest != null) {
                videoOnly.map { StreamTrack(it.height, "${it.height}p", it.content, audioBest.content) }
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
            Diagnostics.log("$name $videoId: $reason FAILED (${elapsedMs}ms)")
            ResolverHealthMonitor.recordFailure(clientKey, reason)
            null
        }
    }

    private fun extractChannelId(url: String?): String? {
        if (url == null) return null
        return Regex("/channel/(UC[\\w-]+)").find(url)?.groupValues?.get(1)
    }
}
