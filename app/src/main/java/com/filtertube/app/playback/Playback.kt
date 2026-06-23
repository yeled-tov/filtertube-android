package com.filtertube.app.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.filtertube.app.data.ChannelsRepository
import com.filtertube.app.data.SettingsStore
import com.filtertube.app.data.StreamData
import com.filtertube.app.data.StreamRepository
import com.filtertube.app.data.Video
import com.filtertube.app.data.audioOnlyCategories

/**
 * לוגיקת ניגון משותפת — בניית פריטי מדיה, תור "רדיו" אוטומטי, ומטמון StreamData
 * (כדי שמעבר איכות/אודיו יעבוד גם על פריטים שהתור הוסיף אוטומטית).
 */
object Playback {

    const val EXTRA_IS_AUDIO = "filtertube_is_audio"
    private const val CACHE_CAP = 40
    private const val RADIO_SIZE = 8

    private val dataCache = LinkedHashMap<String, StreamData>()

    fun cachedData(videoId: String?): StreamData? = videoId?.let { dataCache[it] }

    private fun cache(videoId: String, data: StreamData) {
        dataCache[videoId] = data
        while (dataCache.size > CACHE_CAP) {
            val oldest = dataCache.keys.firstOrNull() ?: break
            dataCache.remove(oldest)
        }
    }

    /**
     * אינדקס איכות ברירת מחדל. [preferred] = גובה מבוקש (px); 0 = אוטומטי (עד 720).
     * הרשימה ממוינת מהגבוה לנמוך, אז בוחרים את הגבוה ביותר שאינו עולה על המבוקש.
     */
    fun defaultQuality(data: StreamData, preferred: Int = 0): Int {
        if (data.tracks.isEmpty()) return 0
        val idx = if (preferred > 0) {
            data.tracks.indexOfFirst { it.height in 1..preferred }.takeIf { it >= 0 } ?: data.tracks.lastIndex
        } else {
            data.tracks.indexOfFirst { it.height in 1..720 }.takeIf { it >= 0 } ?: 0
        }
        return idx.coerceIn(0, data.tracks.lastIndex)
    }

    fun forcedAudio(category: String?, level: Int): Boolean =
        category in audioOnlyCategories || (level == 1 && category == "music")

    fun buildItem(data: StreamData, videoId: String, audio: Boolean, qualityIndex: Int = defaultQuality(data)): MediaItem {
        val extras = Bundle().apply { putBoolean(EXTRA_IS_AUDIO, audio) }
        val uri: String = if (audio) {
            data.bestAudioUrl ?: data.bestVideoUrl
        } else {
            val t = data.tracks.getOrNull(qualityIndex)
            if (t == null) data.bestVideoUrl
            else {
                if (!t.audioUrl.isNullOrEmpty()) extras.putString(FilterTubeMediaSourceFactory.EXTRA_AUDIO_URL, t.audioUrl)
                t.videoUrl
            }
        }
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(videoId)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(data.title)
                    .setArtist(data.uploaderName)
                    .setArtworkUri(data.thumbnailUrl?.let { Uri.parse(it) })
                    .build(),
            )
            .build()
    }

    /**
     * מתחיל ניגון של [video], ובונה מסביבו תור "רדיו" אוטומטי מסרטונים קשורים מאושרים.
     * חייב לרוץ מתוך coroutine; קריאות ל-controller מתבצעות בחזרה ב-Main.
     */
    suspend fun start(context: Context, controller: MediaController?, video: Video) {
        val c = controller ?: return
        val settings = SettingsStore(context)
        val level = settings.filterLevel
        val channels = ChannelsRepository.getChannels(context)
        val allowed = channels.map { it.youtubeChannelId }.toHashSet()
        val catById = channels.associate { it.youtubeChannelId to it.category }

        val preferred = settings.preferredQuality
        val data = StreamRepository.getStream(video.id)
        cache(video.id, data)
        val audio = forcedAudio(catById[data.channelId], level)
        val firstItem = buildItem(data, video.id, audio, defaultQuality(data, preferred))

        c.setMediaItem(firstItem)
        c.prepare()
        c.play()

        // תן לסרטון הראשון "ראש" של ~1.2 שניות להתחיל להיטען לפני שבונים את התור,
        // כדי שתור הרדיו לא יגזול רוחב פס מהניגון הנוכחי.
        kotlinx.coroutines.delay(1200)

        // תור רדיו אוטומטי. את הסרטונים הקשורים טוענים *כאן* (אחרי שהניגון כבר התחיל)
        // ולא בתוך getStream — כך קריאת הרשת ל-related לא מעכבת את הופעת הסרטון על המסך.
        // הקשורים של יוטיוב לרוב גלובליים ולא ברשימה הלבנה, ולכן ממלאים גם מאותו ערוץ.
        val related = runCatching { com.filtertube.app.data.InnerTube.related(video.id) }.getOrNull().orEmpty()
        val relatedApproved = related.filter { it.channelId in allowed && it.id != video.id }
        val sameChannel = if (data.channelId in allowed) {
            runCatching {
                com.filtertube.app.data.YouTubeRepository
                    .fetchChannelVideos(data.channelId, data.uploaderName)
            }.getOrNull().orEmpty()
        } else emptyList()
        val queue = (relatedApproved + sameChannel)
            .distinctBy { it.id }
            .filter { it.id != video.id }
            .take(RADIO_SIZE)
        val resolved = coroutineScope {
            queue.map { v ->
                async(Dispatchers.IO) {
                    runCatching { StreamRepository.getStream(v.id) }.getOrNull()?.let { v to it }
                }
            }.awaitAll()
        }
        for (pair in resolved) {
            val (v, d) = pair ?: continue
            cache(v.id, d)
            val a = forcedAudio(catById[d.channelId] ?: catById[v.channelId], level)
            c.addMediaItem(buildItem(d, v.id, a, defaultQuality(d, preferred)))
        }
    }
}
