package com.filtertube.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.File

/**
 * העשרת מטא-דאטה לסרטונים (משך, צפיות, תאריך העלאה, שידור חי) — דרך NewPipe בלבד.
 *
 * ## למה זה לא משתמש יותר ב-YouTube Data API
 * הגרסה הקודמת משכה `videos.list` עם מפתח API. גם כשזה "זול" (יחידת מכסה אחת
 * לכל 50 סרטונים), המכסה היומית של הפרויקט היא 10,000 יחידות **לכל המשתמשים
 * ביחד**. ברגע שהיא נגמרה — מכל סיבה שהיא — כל קריאה החזירה 403, ומסך הבית
 * הציג "שגיאה בטעינה" במקום סרטונים. תלות במשאב משותף ומוגבל היא הבעיה עצמה,
 * לא גודל הבקשה.
 *
 * ## מה במקום
 * NewPipe מחלץ את דף הערוץ ישירות מיוטיוב, בלי מפתח ובלי מכסה. חילוץ אחד של
 * ערוץ מחזיר את הסרטונים האחרונים שלו **כולל משך, צפיות, תאריך וסטטוס שידור
 * חי** — כלומר בקשה אחת מעשירה עשרות סרטונים בבת אחת. התוצאה נשמרת במטמון
 * קבוע על הדיסק, כך שכל ערוץ נמשך פעם אחת ומספר הצפיות מתרענן רק אחרי
 * [VIEWS_TTL_MS].
 *
 * ההעשרה היא תמיד שיפור ולעולם לא תנאי: אם החילוץ נכשל, הרשימה חוזרת כמו
 * שהיא (בלי משך וצפיות) ושום מסך לא נחסם.
 */
object VideoMetadata {

    private const val CACHE_FILE = "video_metadata.json"
    private const val CACHE_CAP = 4000
    private const val VIEWS_TTL_MS = 12 * 60 * 60 * 1000L // מספר הצפיות מתיישן אחרי 12 שעות

    /**
     * כמה ערוצים לחלץ בקריאה אחת. כל ערוץ מעשיר עשרות סרטונים, אז זה מתכנס
     * מהר — אבל 12 היה נמוך מדי: ראש הפיד לבדו מגיע מכ-40 ערוצים שונים, ולכן
     * משך וצפיות הופיעו רק בחלק מהשורות ורק אחרי כמה רענונים.
     */
    private const val MAX_CHANNELS_PER_CALL = 24

    /**
     * מסך "שידורים חיים" בודק יותר ערוצים: שידור חי הוא אירוע נדיר, וכיסוי של
     * 12 ערוצים בלבד היה מפספס את רובם. הסרטונים ממוינים לפי טריות, אז 40
     * הערוצים הראשונים הם אלה שהכי סביר שמשדרים עכשיו.
     */
    private const val LIVE_MAX_CHANNELS = 40

    /** חילוצים מקבילים. גבוה מדי חונק את מאגר ה-IO (ראה הקריסה ב-ChannelAdmin). */
    private const val CONCURRENCY = 6

    @Serializable
    data class Meta(
        val durationSec: Long = 0L,
        val viewCount: Long = 0L,
        val publishedAt: Long = 0L,
        val live: Boolean = false,
        val fetchedAt: Long = 0L,
    )

    private val json = Json { ignoreUnknownKeys = true }

    private val cache = LinkedHashMap<String, Meta>()
    private val lock = Mutex()

    @Volatile private var loaded = false

    // ── מטמון על הדיסק ────────────────────────────────────────────────────
    private fun file(context: Context) = File(context.cacheDir, CACHE_FILE)

    private suspend fun ensureLoaded(context: Context) {
        if (loaded) return
        lock.withLock {
            if (loaded) return
            runCatching {
                val f = file(context)
                if (f.exists()) {
                    json.decodeFromString<Map<String, Meta>>(f.readText())
                        .forEach { (id, meta) -> cache[id] = meta }
                }
            }
            loaded = true
        }
    }

    private suspend fun persist(context: Context) {
        val snapshot = lock.withLock {
            while (cache.size > CACHE_CAP) {
                val oldest = cache.keys.firstOrNull() ?: break
                cache.remove(oldest)
            }
            cache.toMap()
        }
        withContext(Dispatchers.IO) {
            runCatching { file(context).writeText(json.encodeToString(snapshot)) }
        }
    }

    /**
     * צילום מצב אחד של המטמון תחת נעילה אחת.
     *
     * חשוב שזה יהיה snapshot ולא קריאה נעולה לכל מזהה בנפרד: [enrich] רץ על
     * הפיד המלא של מסך הבית — כ-2,500 סרטונים מ-166 ערוצים. נעילה לכל סרטון
     * פירושה ~2,800 רכישות Mutex ברצף, כל אחת נקודת השהיה של קורוטינה, בתוך
     * המסלול שחוסם את הצגת מסך הבית.
     */
    private suspend fun snapshot(): Map<String, Meta> = lock.withLock { cache.toMap() }

    private fun isFresh(meta: Meta): Boolean =
        System.currentTimeMillis() - meta.fetchedAt < VIEWS_TTL_MS

    // ── ה-API הציבורי ─────────────────────────────────────────────────────
    /**
     * מחזיר את [videos] עם משך, צפיות ותאריך העלאה אמיתיים.
     *
     * מה שכבר במטמון מוחזר מיד ובלי רשת. הסינון לרשימה הלבנה לא מושפע:
     * הפונקציה לא מוסיפה ולא מסירה סרטונים, רק ממלאת שדות ריקים.
     */
    suspend fun enrich(context: Context, videos: List<Video>, limit: Int = 300): List<Video> {
        if (videos.isEmpty()) return videos
        ensureLoaded(context)

        val known = snapshot()
        val missing = videos.take(limit)
            .filter { it.id.isNotBlank() && known[it.id]?.takeIf { meta -> isFresh(meta) } == null }

        if (missing.isNotEmpty()) {
            fetchFromNewPipe(missing)
            persist(context)
        }

        val fresh = if (missing.isEmpty()) known else snapshot()
        return videos.map { video ->
            val meta = fresh[video.id] ?: return@map video
            video.copy(
                publishedAt = meta.publishedAt.takeIf { it > 0L } ?: video.publishedAt,
                durationSec = meta.durationSec.takeIf { it > 0L } ?: video.durationSec,
                viewCount = meta.viewCount.takeIf { it > 0L } ?: video.viewCount,
            )
        }
    }

    /** מזהי הסרטונים מתוך [videos] שמשודרים כרגע בשידור חי. */
    suspend fun liveIds(context: Context, videos: List<Video>): Set<String> {
        if (videos.isEmpty()) return emptySet()
        ensureLoaded(context)
        // שידור חי משתנה כל הזמן — תמיד מרעננים את הערוצים הרלוונטיים.
        fetchFromNewPipe(videos, LIVE_MAX_CHANNELS)
        persist(context)
        val known = snapshot()
        return videos.filter { known[it.id]?.live == true }.map { it.id }.toSet()
    }

    /** משך הסרטון בלבד, מהמטמון, בלי גישה לרשת. */
    suspend fun cachedDuration(context: Context, videoId: String): Long {
        ensureLoaded(context)
        return snapshot()[videoId]?.durationSec ?: 0L
    }

    // ── חילוץ דרך NewPipe ─────────────────────────────────────────────────
    /**
     * מחלץ את הערוצים של [videos] ושומר במטמון את המטא-דאטה של כל סרטון שהוחזר.
     *
     * מקובץ לפי ערוץ בכוונה: חילוץ אחד מחזיר את כל הסרטונים האחרונים של הערוץ,
     * כך שבקשה אחת מכסה עשרות סרטונים מהפיד במקום בקשה לכל סרטון.
     */
    private suspend fun fetchFromNewPipe(
        videos: List<Video>,
        maxChannels: Int = MAX_CHANNELS_PER_CALL,
    ) = coroutineScope {
        val channelIds = videos.asSequence()
            .map { it.channelId }
            .filter { it.startsWith("UC") }
            .distinct()
            .take(maxChannels)
            .toList()
        if (channelIds.isEmpty()) return@coroutineScope

        val gate = Semaphore(CONCURRENCY)
        val results = channelIds.map { channelId ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    runCatching { extractChannel(channelId) }
                        .onFailure { Diagnostics.log("META: חילוץ ערוץ $channelId נכשל — ${it.message}") }
                        .getOrDefault(emptyMap())
                }
            }
        }.awaitAll()

        val merged = HashMap<String, Meta>()
        results.forEach { merged.putAll(it) }
        if (merged.isNotEmpty()) {
            lock.withLock { cache.putAll(merged) }
            Diagnostics.log("META: הועשרו ${merged.size} סרטונים מ-${channelIds.size} ערוצים (NewPipe, אפס מכסה)")
        }
    }

    /** חילוץ יחיד של ערוץ → מטא-דאטה לכל סרטון שהוחזר. רץ על thread של IO. */
    private fun extractChannel(channelId: String): Map<String, Meta> {
        val info = ChannelInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/channel/$channelId")
        val tab = info.tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.VIDEOS) }
            ?: info.tabs.firstOrNull()
            ?: return emptyMap()
        val items = ChannelTabInfo.getInfo(ServiceList.YouTube, tab).relatedItems
            .filterIsInstance<StreamInfoItem>()

        val now = System.currentTimeMillis()
        val out = HashMap<String, Meta>()
        items.forEach { item ->
            val id = videoIdFrom(item.url) ?: return@forEach
            out[id] = Meta(
                durationSec = runCatching { item.duration }.getOrNull()?.takeIf { it > 0L } ?: 0L,
                viewCount = runCatching { item.viewCount }.getOrNull()?.takeIf { it > 0L } ?: 0L,
                publishedAt = runCatching {
                    item.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli()
                }.getOrNull() ?: 0L,
                // השוואה לפי שם הקבוע ולא לפי הטיפוס: מכסה גם LIVE_STREAM וגם
                // AUDIO_LIVE_STREAM בלי להיות תלוי בגרסת NewPipe.
                live = runCatching { item.streamType?.name?.contains("LIVE") == true }.getOrDefault(false),
                fetchedAt = now,
            )
        }
        return out
    }

    private fun videoIdFrom(url: String?): String? {
        if (url == null) return null
        Regex("[?&]v=([A-Za-z0-9_-]{11})").find(url)?.let { return it.groupValues[1] }
        Regex("/shorts/([A-Za-z0-9_-]{11})").find(url)?.let { return it.groupValues[1] }
        Regex("youtu\\.be/([A-Za-z0-9_-]{11})").find(url)?.let { return it.groupValues[1] }
        return null
    }
}
