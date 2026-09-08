package com.filtertube.app.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.filtertube.app.data.AccountDataGuard
import com.filtertube.app.data.ChannelsRepository
import com.filtertube.app.data.LibraryStore
import com.filtertube.app.data.SettingsStore
import com.filtertube.app.data.PlaybackPriority
import com.filtertube.app.data.StreamData
import com.filtertube.app.data.defaultTrackIndex
import com.filtertube.app.data.StreamRepository
import com.filtertube.app.data.Video
import com.filtertube.app.data.audioOnlyCategories
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * לוגיקת ניגון משותפת — בניית פריטי מדיה, הפעלת תור רדיו אוטונומי, ומטמון StreamData.
 */
@UnstableApi
object Playback {

    const val EXTRA_IS_AUDIO = "filtertube_is_audio"
    private const val CACHE_CAP = 60

    private val dataCache = LinkedHashMap<String, StreamData>()
    private val pendingNext = ArrayDeque<Video>()
    private val playbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var activeController: MediaController? = null

    fun cachedData(videoId: String?): StreamData? = videoId?.let { dataCache[it] }

    private fun cache(videoId: String, data: StreamData) {
        dataCache[videoId] = data
        while (dataCache.size > CACHE_CAP) {
            val oldest = dataCache.keys.firstOrNull() ?: break
            dataCache.remove(oldest)
        }
    }

    /** Add a video after the current item, or remember it for the next session. */
    suspend fun enqueueNext(context: Context, video: Video): Boolean {
        val data = runCatching { StreamRepository.getStream(video.id) }.getOrNull()
        if (data == null) {
            synchronized(pendingNext) { pendingNext.removeAll { it.id == video.id }; pendingNext.addLast(video) }
            return false
        }
        cache(video.id, data)
        val controller = activeController
        if (controller == null) {
            synchronized(pendingNext) { pendingNext.removeAll { it.id == video.id }; pendingNext.addLast(video) }
            return true
        }
        return runCatching {
            val settings = SettingsStore(context)
            val item = buildItem(data, video.id, forcedAudio(null, settings.filterLevel), defaultQuality(data, settings.preferredQuality))
            withContext(Dispatchers.Main) {
                val index = (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
                controller.addMediaItem(index, item)
            }
            true
        }.getOrElse {
            synchronized(pendingNext) { pendingNext.removeAll { it.id == video.id }; pendingNext.addLast(video) }
            false
        }
    }

    private suspend fun addPendingNext(context: Context, controller: MediaController) {
        val requested = synchronized(pendingNext) {
            val copy = pendingNext.toList(); pendingNext.clear(); copy
        }.take(10)
        if (requested.isEmpty()) return
        val settings = SettingsStore(context)
        for (video in requested) {
            val data = runCatching { StreamRepository.getStream(video.id) }.getOrNull() ?: continue
            cache(video.id, data)
            val item = buildItem(data, video.id, forcedAudio(null, settings.filterLevel), defaultQuality(data, settings.preferredQuality))
            withContext(Dispatchers.Main) {
                controller.addMediaItem((controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount), item)
            }
        }
    }

    /** אינדקס איכות ברירת מחדל. ראה [com.filtertube.app.data.defaultTrackIndex]. */
    fun defaultQuality(data: StreamData, preferred: Int = 0): Int =
        data.defaultTrackIndex(preferred)

    fun forcedAudio(category: String?, level: Int): Boolean =
        category in audioOnlyCategories || (level == 1 && category == "music")

    fun buildItem(data: StreamData, videoId: String, audio: Boolean, qualityIndex: Int = defaultQuality(data)): MediaItem {
        val extras = Bundle().apply { putBoolean(EXTRA_IS_AUDIO, audio) }
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
     * מתחיל ניגון של [video] מיד, ומפעיל ברקע בניית תור רדיו אוטונומי.
     */
    suspend fun start(context: Context, controller: MediaController?, video: Video) {
        val c = controller ?: return
        // מכריזים על חזית: עבודות הרקע (רדיו, חימום, העשרה) ימתינו כדי לא
        // לחנוק את ההורדה של הזרם שהמשתמש מחכה לו ממש עכשיו.
        PlaybackPriority.begin()
        try {
            startInternal(context, c, video)
        } finally {
            // משחררים רק אחרי שהנגן הספיק למלא באפר, לא ברגע ש-play() חזר.
            playbackScope.launch {
                delay(PLAYBACK_GRACE_MS)
                PlaybackPriority.end()
            }
        }
    }

    /** זמן החסד שבו הרשת שמורה לנגן אחרי הלחיצה. */
    private const val PLAYBACK_GRACE_MS = 3_000L

    private suspend fun startInternal(context: Context, c: MediaController, video: Video) {
        activeController = c
        val firebaseUser = FirebaseAuth.getInstance().currentUser
            ?.takeIf { it.isEmailVerified }
            ?: return
        val expectedUid = firebaseUser.uid
        val generation = AccountDataGuard.generation()
        val library = LibraryStore(context)
        fun sessionCurrent(): Boolean {
            val current = FirebaseAuth.getInstance().currentUser
            return current?.uid == expectedUid &&
                current.isEmailVerified &&
                AccountDataGuard.generation() == generation
        }
        val settings = SettingsStore(context)
        val level = settings.filterLevel

        val channels = ChannelsRepository.getCachedChannelsFast(context)
        val catById = channels.associate { it.youtubeChannelId to it.category }

        val preferred = settings.preferredQuality
        val data = StreamRepository.getStream(video.id)
        if (!sessionCurrent()) return
        cache(video.id, data)

        com.filtertube.app.data.LibraryBadges.markWatched(video.id)
        val audio = forcedAudio(catById[data.channelId], level)
        val firstItem = buildItem(data, video.id, audio, defaultQuality(data, preferred))

        if (!sessionCurrent()) return
        c.setMediaItem(firstItem)
        c.prepare()
        c.play()

        // כתיבת ההיסטוריה מפענחת ומקודדת JSON שלם ומתזמנת גיבוי לענן. אין שום
        // סיבה שהמשתמש יחכה לזה לפני שהצליל יוצא, אז זה עבר לכאן.
        runCatching {
            library.addToHistory(
                Video(
                    id = video.id,
                    title = data.title.ifBlank { video.title },
                    channelName = data.uploaderName.ifBlank { video.channelName },
                    channelId = data.channelId.ifBlank { video.channelId },
                    thumbnailUrl = data.thumbnailUrl ?: video.thumbnailUrl,
                    // תאריך ההעלאה האמיתי נשמר; זמן הצפייה נרשם ב-watchedAt בתוך addToHistory.
                    publishedAt = video.publishedAt,
                    durationSec = data.durationSec.takeIf { it > 0L } ?: video.durationSec,
                    viewCount = data.viewCount.takeIf { it > 0L } ?: video.viewCount,
                ),
            )
        }
        addPendingNext(context, c)

        // הפעלה מבוזרת ומהירה ברקע של תור הרדיו (ללא שום delay חוסם!)
        RadioQueueManager.startQueue(context, c, video, playbackScope)
    }
}
