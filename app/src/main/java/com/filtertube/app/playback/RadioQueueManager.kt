package com.filtertube.app.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.filtertube.app.data.ChannelsRepository
import com.filtertube.app.data.Diagnostics
import com.filtertube.app.data.FeedCache
import com.filtertube.app.data.SettingsStore
import com.filtertube.app.data.StreamRepository
import com.filtertube.app.data.Video
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * מנהל תור רדיו אוטונומי ומהיר (Radio Queue Manager).
 * בונה תור חכם קשור לשיר הנוכחי, מריץ חילוצי זרם במקביל (Bounded Concurrency),
 * מוסיף סרטונים מיד כשהם מוכנים, ומבצע מילוי מחדש אוטומטי (Auto Refill).
 */
@UnstableApi
object RadioQueueManager {

    private const val QUEUE_TARGET = 10
    private const val QUEUE_MIN = 6
    private const val QUEUE_MAX = 15
    private const val MAX_CONCURRENT_RESOLVES = 4
    private const val RECENT_PLAYED_CAP = 50

    private val recentPlayedIds = LinkedHashMap<String, Long>()
    private val activeQueueIds = ConcurrentHashMap.newKeySet<String>()
    private val resolveSemaphore = Semaphore(MAX_CONCURRENT_RESOLVES)
    @Volatile private var currentQueueJob: Job? = null

    fun recordPlayed(videoId: String) {
        synchronized(recentPlayedIds) {
            recentPlayedIds[videoId] = System.currentTimeMillis()
            while (recentPlayedIds.size > RECENT_PLAYED_CAP) {
                val oldest = recentPlayedIds.keys.firstOrNull() ?: break
                recentPlayedIds.remove(oldest)
            }
        }
    }

    /**
     * מפעיל בניית תור רדיו ברקע באופן מידי (ללא שום המתנה/השהיה חוסמת).
     */
    fun startQueue(
        context: Context,
        controller: MediaController?,
        currentVideo: Video,
        scope: CoroutineScope
    ) {
        val c = controller ?: return
        currentQueueJob?.cancel()
        currentQueueJob = scope.launch(Dispatchers.IO) {
            recordPlayed(currentVideo.id)
            activeQueueIds.clear()
            activeQueueIds.add(currentVideo.id)

            refillInternal(context, c, currentVideo)
        }
    }

    /**
     * בודק אם התור ירד מתחת ל-QUEUE_MIN ומפעיל refill ברקע במידת הצורך.
     */
    fun checkAutoRefill(
        context: Context,
        controller: MediaController?,
        currentVideo: Video,
        scope: CoroutineScope
    ) {
        val c = controller ?: return
        val currentSize = c.mediaItemCount - c.currentMediaItemIndex - 1
        if (currentSize < QUEUE_MIN && (currentQueueJob == null || currentQueueJob?.isCompleted == true)) {
            Diagnostics.log("RADIO queueSize=$currentSize < $QUEUE_MIN ← refill started")
            currentQueueJob = scope.launch(Dispatchers.IO) {
                refillInternal(context, c, currentVideo)
            }
        }
    }

    private suspend fun refillInternal(
        context: Context,
        c: MediaController,
        currentVideo: Video
    ) {
        val settings = SettingsStore(context)
        val level = settings.filterLevel
        val preferredQuality = settings.preferredQuality

        val channels = ChannelsRepository.getCachedChannelsFast(context)
        val catById = channels.associate { it.youtubeChannelId to it.category }
        val currentCat = catById[currentVideo.channelId]

        val feed = runCatching { FeedCache.loadFeed(context) }.getOrNull().orEmpty()

        // דירוג Candidates
        val candidates = feed
            .filter { !it.isShort && it.id != currentVideo.id }
            .map { v ->
                var score = 0
                val vCat = catById[v.channelId]

                if (v.channelId == currentVideo.channelId) score += 100
                if (currentCat != null && vCat == currentCat) score += 60
                if (vCat != null && vCat == "music" && currentCat == "music") score += 50
                score += 20 // fallback

                if (synchronized(recentPlayedIds) { recentPlayedIds.containsKey(v.id) }) score -= 1000
                if (activeQueueIds.contains(v.id)) score -= 10000

                v to score
            }
            .filter { it.second > -5000 }
            .sortedByDescending { it.second }
            .map { it.first }

        val currentCount = withContext(Dispatchers.Main) { c.mediaItemCount - c.currentMediaItemIndex - 1 }
        val neededCount = (QUEUE_TARGET - currentCount).coerceIn(0, QUEUE_MAX - currentCount)

        if (neededCount <= 0 || candidates.isEmpty()) {
            Diagnostics.log("RADIO candidates=${candidates.size} needed=$neededCount (תור מלא/אין candidates)")
            return
        }

        val selectedCandidates = candidates.take(neededCount * 2)
        Diagnostics.log("RADIO candidates=${selectedCandidates.size} selected=$neededCount resolving=$MAX_CONCURRENT_RESOLVES parallel")

        coroutineScope {
            selectedCandidates.map { video ->
                async {
                    resolveSemaphore.withPermit {
                        if (c.mediaItemCount - c.currentMediaItemIndex - 1 >= QUEUE_TARGET) return@withPermit
                        val data = runCatching { StreamRepository.getStream(video.id) }.getOrNull() ?: return@withPermit

                        activeQueueIds.add(video.id)
                        val audio = Playback.forcedAudio(catById[data.channelId] ?: catById[video.channelId], level)
                        val item = Playback.buildItem(data, video.id, audio, Playback.defaultQuality(data, preferredQuality))

                        withContext(Dispatchers.Main) {
                            val addIndex = c.mediaItemCount
                            c.addMediaItem(addIndex, item)
                            Diagnostics.log("RADIO added videoId=${video.id} queueSize=${c.mediaItemCount - c.currentMediaItemIndex - 1}")
                        }
                    }
                }
            }.awaitAll()
        }

        val finalQueueSize = withContext(Dispatchers.Main) { c.mediaItemCount - c.currentMediaItemIndex - 1 }
        Diagnostics.log("RADIO refill complete queueSize=$finalQueueSize")
    }
}
