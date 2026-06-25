package com.filtertube.app.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
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
    private const val RADIO_SIZE = 6

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
            // אוטומטי: מעדיפים זרם משולב (muxed, audioUrl==null) — קובץ יחיד שמתחיל מהר
            // ויציב, בלי מיזוג DASH של וידאו+אודיו נפרדים שעלול להיתקע באמצע. אם אין
            // muxed — הגבוה ביותר עד 720p. המשתמש יכול להעלות איכות ידנית בתפריט הנגן.
            data.tracks.indexOfFirst { it.audioUrl == null }.takeIf { it >= 0 }
                ?: data.tracks.indexOfFirst { it.height in 1..720 }.takeIf { it >= 0 }
                ?: 0
        }
        return idx.coerceIn(0, data.tracks.lastIndex)
    }

    fun forcedAudio(category: String?, level: Int): Boolean =
        category in audioOnlyCategories || (level == 1 && category == "music")

    fun buildItem(data: StreamData, videoId: String, audio: Boolean, qualityIndex: Int = defaultQuality(data)): MediaItem {
        val extras = Bundle().apply { putBoolean(EXTRA_IS_AUDIO, audio) }
        // ה-UA שבו נחלצו כתובות הזרם — חובה לנגן באותו UA אחרת יוטיוב חותך אחרי כמה שניות
        data.streamUserAgent?.let { extras.putString(FilterTubeMediaSourceFactory.EXTRA_USER_AGENT, it) }
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
        // היסטוריית צפייה מקומית — מזינה את מסך ההיסטוריה ואת התאמת מסך הבית
        runCatching {
            com.filtertube.app.data.LibraryStore(context).addToHistory(
                Video(
                    id = video.id,
                    title = data.title.ifBlank { video.title },
                    channelName = data.uploaderName.ifBlank { video.channelName },
                    channelId = data.channelId.ifBlank { video.channelId },
                    thumbnailUrl = data.thumbnailUrl ?: video.thumbnailUrl,
                    publishedAt = System.currentTimeMillis(),
                ),
            )
        }
        val audio = forcedAudio(catById[data.channelId], level)
        val firstItem = buildItem(data, video.id, audio, defaultQuality(data, preferred))

        c.setMediaItem(firstItem)
        c.prepare()
        c.play()

        // תן לסרטון הנוכחי "ראש" גדול (6ש') לבנות buffer לפני שמתחילים עבודת רקע,
        // אחרת חילוץ התור גוזל רוחב פס והניגון נתקע אחרי כמה שניות (התסמין שדווח).
        kotlinx.coroutines.delay(6000)

        // ── בניית תור "רדיו" חכם ומגוון (בתוך הרשימה הלבנה בלבד) ──
        // מועמדים, לפי עדיפות:
        //   1) סרטונים *קשורים* אמיתיים של יוטיוב (מסוננים למאושרים)
        //   2) סרטונים מאותה *קטגוריה* מערוצים אחרים — לא רק אותו ערוץ! (מהפיד שכבר בזיכרון)
        //   3) עוד מאותו ערוץ (גיבוי)
        // משתמשים בפיד המטמון כדי לא לעשות רשת נוספת לבניית רשימת המועמדים.
        val feed = runCatching { com.filtertube.app.data.FeedCache.loadFeed(context) }.getOrNull().orEmpty()
        val currentCat = catById[data.channelId]
        val related = runCatching { com.filtertube.app.data.InnerTube.related(video.id) }.getOrNull().orEmpty()
        val relatedApproved = related.filter { it.channelId in allowed && it.id != video.id }
        val sameCategory = feed
            .filter { currentCat != null && catById[it.channelId] == currentCat && it.channelId != data.channelId }
            .shuffled()
        val sameChannel = feed.filter { it.channelId == data.channelId }
        val queue = (relatedApproved + sameCategory + sameChannel)
            .distinctBy { it.id }
            .filter { it.id != video.id }
            .take(RADIO_SIZE)

        // פותרים *אחד-אחד* דרך getStream (הנתיב העובד — NewPipe). הראש הגדול (6ש') +
        // הבאפר הענק מונעים עצירות. כל פריט מתווסף לתור ברגע שהתפענח.
        for (v in queue) {
            val d = runCatching { StreamRepository.getStream(v.id) }.getOrNull() ?: continue
            cache(v.id, d)
            val a = forcedAudio(catById[d.channelId] ?: catById[v.channelId], level)
            c.addMediaItem(buildItem(d, v.id, a, defaultQuality(d, preferred)))
            kotlinx.coroutines.delay(1200)   // נשימה בין חילוצים, לשמור רוחב פס לניגון
        }
    }
}
