package com.filtertube.app.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

/**
 * האיכות שתנוגן כברירת מחדל — **מקור אמת יחיד** לבחירת האיכות.
 *
 * הרשימה ממוינת מהגבוה לנמוך, ולכן הראשון שעומד בתנאי הוא הטוב ביותר.
 * במצב אוטומטי לוקחים עד 720p; אם המשתמש בחר איכות מועדפת, לוקחים את
 * הגבוהה ביותר שאינה עולה עליה.
 *
 * חשוב שזה יישאר במקום אחד: קודם לכן הייתה כאן לוגיקה כפולה — אחת לניגון
 * ואחת ליומן האבחון — והן נפרדו זו מזו, כך שהיומן דיווח "360p [muxed]"
 * בזמן שהניגון בחר משהו אחר לגמרי.
 */
fun StreamData.defaultTrackIndex(preferred: Int = 0): Int {
    if (tracks.isEmpty()) return 0
    val idx = if (preferred > 0) {
        tracks.indexOfFirst { it.height in 1..preferred }.takeIf { it >= 0 } ?: tracks.lastIndex
    } else {
        tracks.indexOfFirst { it.height in 1..720 }.takeIf { it >= 0 } ?: 0
    }
    return idx.coerceIn(0, tracks.lastIndex)
}

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
    /**
     * שם המנוע שהחזיר את הזרם הזה.
     *
     * נדרש להתאוששות: כשהנגן מקבל 403 על כתובת, אין טעם לבקש אותה שוב מאותו
     * מנוע — הוא יחזיר בדיוק את אותה כתובת. עם השם אפשר לפסול אותו לניסיון
     * הבא ולקבל כתובת ממקור אחר.
     */
    val resolvedBy: String = "",
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
        "TVHTML5_EMBED" to InnerTubeResolver(InnerTubeClientType.TVHTML5_EMBED),
        "MWEB" to InnerTubeResolver(InnerTubeClientType.MWEB),
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

    /**
     * ה-scope שבו רץ *כל* פתרון זרם — לא ה-scope של מי שביקש ראשון.
     *
     * שני דברים היו שבורים כאן:
     *
     * 1. **מרוץ ב-inFlight.** הבדיקה `inFlight[videoId]` וההכנסה שאחריה לא היו
     *    אטומיות. שתי קריאות שהגיעו יחד לאותו סרטון ראו שתיהן null, שתיהן
     *    התחילו פתרון מלא, והשנייה דרסה את הראשונה במפה — בדיוק הכפילות
     *    שהמנגנון הזה אמור למנוע.
     *
     * 2. **ביטול מדבק.** ה-async היה ילד של הקורוטינה הקוראת. כלומר הפותר
     *    הראשון קבע את גורל כל השאר: אם המסך שלו נסגר וה-scope בוטל, גם כל
     *    מי שהמתין לאותו Deferred דרך inFlight קיבל ביטול — בלי שום קשר
     *    למצב שלו. זה בדיוק המלכוד שמתועד אצל prefetchScope, רק במסלול הרגיל.
     */
    private val resolveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun getStream(videoId: String): StreamData {
        // 1. בדיקת מטמון
        getCached(videoId)?.let { cached ->
            Diagnostics.log("StreamRepository $videoId: cache hit (0ms) · ${trackSummary(cached)}")
            return cached
        }

        // 2. מניעת קריאות כפולות במקביל — הכנסה אטומית, עם התחלה עצלה כדי
        //    שהמפסיד במרוץ לא יריץ שום דבר ולא ימחק את המפתח של המנצח.
        val fresh = resolveScope.async(start = CoroutineStart.LAZY) {
            try {
                resolveInternal(videoId)
            } finally {
                inFlight.remove(videoId)
            }
        }
        val existing = inFlight.putIfAbsent(videoId, fresh)
        val deferred = if (existing != null) {
            fresh.cancel()
            Diagnostics.log("StreamRepository $videoId: בקשה מקבילית קיימת, ממתין לתשובה")
            existing
        } else {
            fresh.also { it.start() }
        }
        return deferred.await()
    }

    /** מגביל את החימום ברקע כדי שלא יתחרה בסרטון שהמשתמש באמת מנגן עכשיו. */
    private val prefetchGate = Semaphore(2)

    /**
     * scope עצמאי לחימום מראש — **בכוונה לא ה-scope של המסך**.
     *
     * [getStream] יוצר את ה-Deferred שלו כילד של ה-scope הקורא. אילו החימום היה
     * רץ מ-scope של Composable, יציאה מהמסך הייתה מבטלת אותו — וגרוע מכך, אם
     * הנגן כבר המתין לאותו Deferred דרך inFlight, גם הוא היה נופל יחד איתו.
     */
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * מחמם מראש את הזרמים של [videoIds] כדי שלחיצה על סרטון תהיה מיידית.
     *
     * הבעיה שזה פותר: פתרון הזרם עצמו לוקח ~1.5 שניות (NewPipe), וכל הזמן הזה
     * נגבה מהמשתמש *אחרי* הלחיצה. אין דרך להאיץ את יוטיוב עצמו — אבל אפשר
     * לשלם את המחיר מראש, ברקע, בזמן שהמשתמש עוד גולל. אז הלחיצה פוגעת
     * במטמון ומחזירה "cache hit (0ms)".
     *
     * לא suspend ולא חוסם: יורה ומשחרר. כישלונות נבלעים בשקט — זה שיפור, לא תנאי.
     */
    fun prefetch(videoIds: List<String>, max: Int = 8) {
        val targets = videoIds.asSequence()
            .filter { it.isNotBlank() && getCached(it) == null }
            .distinct()
            .take(max)
            .toList()
        if (targets.isEmpty()) return

        Diagnostics.log("PREFETCH: מחמם ${targets.size} סרטונים ברקע")
        targets.forEach { id ->
            prefetchScope.launch {
                prefetchGate.withPermit {
                    PlaybackPriority.awaitIdle()   // הנגן קודם
                    if (getCached(id) == null) runCatching { getStream(id) }
                }
            }
        }
    }

    /**
     * חילוץ טרי, בלי מטמון ובלי איחוד בקשות, תוך פסילת מנוע מסוים.
     *
     * זה המסלול של ההתאוששות בנגן. getStream הרגיל היה מחזיר את אותה כתובת
     * — או מהמטמון, או מבקשה מקבילה שכבר רצה — וזה בדיוק מה שלא רוצים אחרי
     * ש-403 הוכיח שהכתובת ההיא מתה.
     */
    suspend fun resolveFresh(videoId: String, excludeResolver: String?): StreamData {
        invalidateCache(videoId)
        val data = resolveInternal(videoId, excludeResolver)
        putCache(videoId, data)
        return data
    }

    private suspend fun resolveInternal(
        videoId: String,
        excludeResolver: String? = null,
    ): StreamData = coroutineScope {
        val t0 = System.currentTimeMillis()
        val priorityKeys = RemoteConfig.resolverPriority()

        // סינון זריז: בוחרים רק מנועים זמינים שאינם ב-cooldown
        val activeResolvers = buildList {
            for (key in priorityKeys) {
                if (ResolverHealthMonitor.isAvailable(key) && RemoteConfig.isResolverEnabled(key, true)) {
                    resolverMap[key]?.let { add(it) }
                }
            }
            // אם כל המנועים המועדפים ב-cooldown, משתמשים ב-NewPipe כגיבוי ישיר
            if (isEmpty()) {
                resolverMap["NewPipe"]?.let { add(it) }
            }
        }.filter { it.name != excludeResolver }
            // פסילה שמרוקנת את הרשימה גרועה מאי-פסילה: עדיף לנסות שוב את
            // אותו מנוע מאשר לא לנסות כלום.
            .ifEmpty { activeFallback() }

        // כל המנועים רצים **במקביל**, והראשון שמצליח מנצח.
        //
        // קודם לכן זו הייתה לולאה טורית: כל מנוע כושל היה חייב להיכשל עד הסוף
        // לפני שהבא בתור התחיל. כששני מנועי InnerTube לא עובדים, זה הוסיף
        // כשנייה שלמה של המתנה לכל סרטון לפני ש-NewPipe בכלל יצא לדרך:
        //   IOS נכשל 642ms → VR נכשל 335ms → NewPipe הצליח 1943ms = 2922ms
        // במקביל, הסרטון עולה כזמן המנוע המהיר שהצליח, וכישלון של מנוע אחר
        // כבר לא עולה למשתמש כלום.
        val winner = CompletableDeferred<StreamData>()
        val attempts = activeResolvers.map { resolver ->
            launch(Dispatchers.IO) {
                val rT0 = System.currentTimeMillis()
                // CancellationException נתפס בנפרד ולא נספר ככישלון.
                //
                // ברגע שמנוע אחד מנצח, כל השאר מבוטלים — וזה בדיוק התכנון.
                // אבל runCatching בלע גם את הביטול והחזיר null, כך שהיומן
                // הציג "NewPipe נכשל (1781ms)" בשורה אחת מתחת ל"NewPipe
                // SUCCESS (1780ms)". שתי שורות סותרות על אותו חילוץ, וכל
                // ניסיון לאבחן מהיומן התחיל מלנסות להבין מה מהן נכון.
                val result = try {
                    resolver.resolve(videoId)?.copy(resolvedBy = resolver.name)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    Diagnostics.log("StreamRepository $videoId: ${resolver.name} בוטל — מנוע אחר כבר ניצח")
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (result == null) {
                    Diagnostics.log("StreamRepository $videoId: ${resolver.name} נכשל (${System.currentTimeMillis() - rT0}ms)")
                } else if (winner.complete(result)) {
                    Diagnostics.log(
                        "StreamRepository $videoId: ${resolver.name} ניצח ב-${System.currentTimeMillis() - rT0}ms " +
                            "(סה\"כ ${System.currentTimeMillis() - t0}ms) · ${trackSummary(result)}"
                    )
                }
            }
        }

        // אם כל המנועים נכשלו, ההמתנה חייבת להשתחרר במקום להיתקע לנצח.
        val allFailed = launch {
            attempts.joinAll()
            // joinAll חוזר גם כשמנוע הצליח — כל הניסיונות פשוט הסתיימו. בלי
            // הבדיקה הזו נרשם "כל המנועים נכשלו" מיד אחרי "NewPipe ניצח".
            if (winner.isCompleted) return@launch
            Diagnostics.log("StreamRepository $videoId: כל המנועים נכשלו ${System.currentTimeMillis() - t0}ms ✖")
            winner.completeExceptionally(
                IllegalStateException("לא הצלחנו להפעיל את הסרטון. נסה שוב בעוד רגע."),
            )
        }

        val won = try {
            winner.await()
        } finally {
            // ברגע שיש מנצח אין טעם להמשיך לחכות לשאר.
            attempts.forEach { it.cancel() }
            allFailed.cancel()
        }

        putCache(videoId, won)
        won
    }

    private fun activeFallback(): List<StreamResolver> =
        listOfNotNull(resolverMap["NewPipe"])

    /**
     * תקציר האיכויות — ברירת המחדל נלקחת מ-[Playback.defaultQuality], שהיא
     * הבחירה האמיתית בזמן ניגון.
     *
     * קודם לכן היה כאן עותק נפרד של הלוגיקה (`firstOrNull { audioUrl == null }`),
     * שדיווח תמיד על הזרם ה-muxed בלי קשר למה שנבחר בפועל. כלומר היומן הציג
     * "360p [muxed]" גם אחרי שברירת המחדל שונתה — אבחון שמטעה במקום לעזור.
     */
    private fun trackSummary(d: StreamData): String {
        val muxed = d.tracks.count { it.audioUrl == null }
        val def = d.tracks.getOrNull(d.defaultTrackIndex())
        val kind = if (def?.audioUrl == null) "muxed" else "DASH"
        return "${d.tracks.size} איכויות ($muxed muxed), ברירת מחדל ${def?.label ?: "?"} [$kind]"
    }
}
