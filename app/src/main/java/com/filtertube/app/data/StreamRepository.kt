package com.filtertube.app.data

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

/**
 * איכות וידאו זמינה. אם [audioUrl] לא null — מדובר בזרם וידאו-בלבד שצריך
 * למזג עם זרם אודיו נפרד (DASH). אחרת זה זרם משולב (muxed) שכבר כולל קול.
 */
data class StreamTrack(
    val height: Int,
    val label: String,
    val videoUrl: String,
    val audioUrl: String?,
)

data class StreamData(
    val title: String,
    val uploaderName: String,
    val channelId: String,
    val durationSec: Long,
    val viewCount: Long,
    val description: String?,
    val thumbnailUrl: String?,
    /** איכויות וידאו זמינות, ממוינות מהגבוהה לנמוכה */
    val tracks: List<StreamTrack>,
    /** זרם האודיו הטוב ביותר — למצב אודיו בלבד */
    val bestAudioUrl: String?,
    /** זרם וידאו משולב הטוב ביותר — להורדה */
    val bestVideoUrl: String,
    /** סרטונים קשורים — להפעלה אוטומטית (לפני סינון לרשימה הלבנה) */
    val related: List<Video>,
    /**
     * ה-User-Agent שבו *חייבים* לנגן את כתובות הזרם.
     */
    val streamUserAgent: String? = null,
)

object StreamRepository {

    private const val CACHE_TTL_MS = 5 * 60 * 1000L // 5 דקות TTL (כתובות YouTube חתומות פגות מהר)
    private const val MAX_CACHE_SIZE = 50

    private data class CachedStream(
        val data: StreamData,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val cache = LinkedHashMap<String, CachedStream>()
    private val inFlight = ConcurrentHashMap<String, Deferred<StreamData>>()

    private val resolverMap: Map<String, StreamResolver> = mapOf(
        "IOS" to InnerTubeResolver(InnerTubeClientType.IOS),
        "ANDROID_VR" to InnerTubeResolver(InnerTubeClientType.ANDROID_VR),
        "NewPipe" to NewPipeResolver()
    )

    @Synchronized
    fun getCached(videoId: String): StreamData? {
        val entry = cache[videoId] ?: return null
        if (System.currentTimeMillis() - entry.timestamp > CACHE_TTL_MS) {
            cache.remove(videoId)
            return null
        }
        return entry.data
    }

    @Synchronized
    fun invalidateCache(videoId: String) {
        if (cache.containsKey(videoId)) {
            cache.remove(videoId)
            Diagnostics.log("StreamRepository $videoId: cache invalidated ✖")
        }
    }

    @Synchronized
    private fun putCache(videoId: String, data: StreamData) {
        cache[videoId] = CachedStream(data)
        while (cache.size > MAX_CACHE_SIZE) {
            val oldest = cache.keys.firstOrNull() ?: break
            cache.remove(oldest)
        }
    }

    @Synchronized
    fun clearCache() {
        cache.clear()
    }

    suspend fun getStream(videoId: String): StreamData = coroutineScope {
        // 1. בדיקת מטמון
        getCached(videoId)?.let { cached ->
            Diagnostics.log("StreamRepository $videoId: cache hit (0ms) · ${trackSummary(cached)}")
            return@coroutineScope cached
        }

        // 2. מניעת קריאות כפולות במקביל (Deduplication)
        val activeDeferred = inFlight[videoId]
        if (activeDeferred != null) {
            Diagnostics.log("StreamRepository $videoId: בקשה מקבילית קיימת, ממתין לתשובה")
            return@coroutineScope activeDeferred.await()
        }

        val deferred = async(Dispatchers.IO) {
            try {
                resolveInternal(videoId)
            } finally {
                inFlight.remove(videoId)
            }
        }

        inFlight[videoId] = deferred
        deferred.await()
    }

    private suspend fun resolveInternal(videoId: String): StreamData {
        val t0 = System.currentTimeMillis()
        val priorityKeys = RemoteConfig.resolverPriority()

        val activeResolvers = buildList {
            for (key in priorityKeys) {
                resolverMap[key]?.let { add(it) }
            }
            // הוספת מנועים חסרים שלא צוינו ב-priority כגיבוי
            for ((key, res) in resolverMap) {
                if (key !in priorityKeys) add(res)
            }
        }

        for (resolver in activeResolvers) {
            val rT0 = System.currentTimeMillis()
            val result = runCatching { resolver.resolve(videoId) }.getOrNull()
            if (result != null) {
                putCache(videoId, result)
                Diagnostics.log(
                    "StreamRepository $videoId: ${resolver.name} ניצח ב-${System.currentTimeMillis() - rT0}ms (סה\"כ ${System.currentTimeMillis() - t0}ms) · ${trackSummary(result)}"
                )
                return result
            }
            Diagnostics.log("StreamRepository $videoId: ${resolver.name} נכשל → מעבר למנוע הבא")
        }

        Diagnostics.log("StreamRepository $videoId: כל המנועים נכשלו ${System.currentTimeMillis() - t0}ms ✖")
        throw IllegalStateException("לא הצלחנו להפעיל את הסרטון. נסה שוב בעוד רגע.")
    }

    /** תקציר האיכויות שנבחרו — האם ברירת המחדל משולבת (muxed) או DASH (מיזוג). */
    private fun trackSummary(d: StreamData): String {
        val muxed = d.tracks.count { it.audioUrl == null }
        val def = d.tracks.firstOrNull { it.audioUrl == null } ?: d.tracks.firstOrNull()
        val kind = if (def?.audioUrl == null) "muxed" else "DASH"
        return "${d.tracks.size} איכויות ($muxed muxed), ברירת מחדל ${def?.label ?: "?"} [$kind]"
    }
}
