package com.filtertube.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * העשרת מטא-דאטה אמיתי לסרטונים (משך, צפיות, תאריך העלאה אמיתי, שידור חי).
 *
 * למה זה קיים:
 * ה-RSS של הערוצים (המקור של הפיד) מחזיר רק כותרת + תאריך, בלי משך ובלי צפיות.
 * לכן עד היום הפיד הציג סרטונים בלי משך ובלי נתונים אמיתיים.
 *
 * הפתרון: `videos.list` של YouTube Data API — עולה **יחידת מכסה אחת לכל 50 סרטונים**
 * (לעומת `search.list` שעולה 100 יחידות לקריאה בודדת). מטמון קבוע על הדיסק מוודא
 * שכל סרטון נמשך פעם אחת בלבד: משך ותאריך העלאה לא משתנים לעולם, ומספר הצפיות
 * מתרענן רק אחרי [VIEWS_TTL_MS].
 */
object VideoMetadata {

    private const val KEY = "AIzaSyDLAo5cUv4lt1Tsad50aMGFE0jl-mfRtOk"
    private const val BASE = "https://www.googleapis.com/youtube/v3"
    private const val CACHE_FILE = "video_metadata.json"
    private const val CACHE_CAP = 4000
    private const val VIEWS_TTL_MS = 12 * 60 * 60 * 1000L // מספר הצפיות מתיישן אחרי 12 שעות
    private const val BATCH = 50                          // המקסימום ש-videos.list מקבל בקריאה אחת
    private const val MAX_BATCHES_PER_CALL = 6            // תקרת בטיחות: עד 300 סרטונים (6 יחידות מכסה)

    @Serializable
    data class Meta(
        val durationSec: Long = 0L,
        val viewCount: Long = 0L,
        val publishedAt: Long = 0L,
        val live: Boolean = false,
        val fetchedAt: Long = 0L,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val cache = LinkedHashMap<String, Meta>()
    private val lock = Mutex()

    @Volatile private var loaded = false

    /** כשהמכסה נגמרה אין טעם להמשיך לנסות — נחכה עד תחילת היום הבא (שעון PT). */
    @Volatile private var quotaBlockedUntil = 0L

    val quotaBlocked: Boolean get() = System.currentTimeMillis() < quotaBlockedUntil

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
     * המסלול שחוסם את הצגת מסך הבית. זו הסיבה שמסך הבית נתקע בעוד החיפוש
     * (60 תוצאות) עבד תקין.
     */
    private suspend fun snapshot(): Map<String, Meta> = lock.withLock { cache.toMap() }

    private fun isFresh(meta: Meta): Boolean =
        System.currentTimeMillis() - meta.fetchedAt < VIEWS_TTL_MS

    // ── ה-API הציבורי ─────────────────────────────────────────────────────
    /**
     * מחזיר את [videos] עם משך, צפיות ותאריך העלאה אמיתיים.
     *
     * מה שכבר במטמון מוחזר מיד ובלי רשת. רק מזהים חסרים נמשכים, בקבוצות של 50.
     * אם הרשת או המכסה נכשלות — מוחזרת הרשימה המקורית כמו שהיא, בלי לזרוק חריגה.
     * הסינון לרשימה הלבנה לא מושפע: הפונקציה לא מוסיפה ולא מסירה סרטונים.
     */
    suspend fun enrich(context: Context, videos: List<Video>, limit: Int = 300): List<Video> {
        if (videos.isEmpty()) return videos
        ensureLoaded(context)

        val known = snapshot()
        val missing = videos.take(limit)
            .map { it.id }
            .filter { it.isNotBlank() }
            .distinct()
            .filter { id -> known[id]?.takeIf { isFresh(it) } == null }

        if (missing.isNotEmpty() && !quotaBlocked) {
            fetchInto(missing.take(BATCH * MAX_BATCHES_PER_CALL))
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

    /** מזהי הסרטונים מתוך [videos] שמשודרים כרגע בשידור חי. עלות: יחידה אחת לכל 50. */
    suspend fun liveIds(context: Context, videos: List<Video>): Set<String> {
        if (videos.isEmpty()) return emptySet()
        ensureLoaded(context)
        val ids = videos.map { it.id }.filter { it.isNotBlank() }.distinct().take(BATCH * MAX_BATCHES_PER_CALL)
        // שידור חי משתנה כל הזמן — תמיד מרעננים, גם אם יש ערך במטמון.
        if (!quotaBlocked) {
            fetchInto(ids)
            persist(context)
        }
        val known = snapshot()
        return ids.filter { known[it]?.live == true }.toSet()
    }

    /** משך הסרטון בלבד, מהמטמון, בלי גישה לרשת. */
    suspend fun cachedDuration(context: Context, videoId: String): Long {
        ensureLoaded(context)
        return snapshot()[videoId]?.durationSec ?: 0L
    }

    // ── משיכה מהשרת ───────────────────────────────────────────────────────
    private suspend fun fetchInto(ids: List<String>) = withContext(Dispatchers.IO) {
        ids.chunked(BATCH).forEach { chunk ->
            if (quotaBlocked) return@withContext
            val url = "$BASE/videos?part=snippet,contentDetails,statistics" +
                "&id=${chunk.joinToString(",")}&maxResults=$BATCH&key=$KEY"
            runCatching {
                http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        if (isQuotaError(response.code, body)) {
                            // עד חצות בשעון האוקיינוס השקט, שם מתאפסת המכסה של גוגל.
                            quotaBlockedUntil = System.currentTimeMillis() + 60 * 60 * 1000L
                            Diagnostics.log("META: מכסת YouTube API נגמרה — עוברים למצב חסכוני")
                        } else {
                            Diagnostics.log("META: HTTP ${response.code}")
                        }
                        return@use
                    }
                    val items = JSONObject(body).optJSONArray("items") ?: return@use
                    val now = System.currentTimeMillis()
                    val parsed = buildMap {
                        for (i in 0 until items.length()) {
                            val item = items.optJSONObject(i) ?: continue
                            val id = item.optString("id")
                            if (id.isBlank()) continue
                            val snippet = item.optJSONObject("snippet")
                            put(
                                id,
                                Meta(
                                    durationSec = IsoDurationParser.parseToSeconds(
                                        item.optJSONObject("contentDetails")?.optString("duration"),
                                    ),
                                    viewCount = item.optJSONObject("statistics")
                                        ?.optString("viewCount")?.toLongOrNull() ?: 0L,
                                    publishedAt = parseIsoDate(snippet?.optString("publishedAt")),
                                    live = snippet?.optString("liveBroadcastContent") == "live",
                                    fetchedAt = now,
                                ),
                            )
                        }
                    }
                    lock.withLock { cache.putAll(parsed) }
                    Diagnostics.log("META: הועשרו ${parsed.size}/${chunk.size} סרטונים (יחידת מכסה אחת)")
                }
            }.onFailure { Diagnostics.log("META: נכשל — ${it.message}") }
        }
    }

    private fun isQuotaError(code: Int, body: String): Boolean =
        code == 403 && (body.contains("quotaExceeded") || body.contains("dailyLimitExceeded"))

    private fun parseIsoDate(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        val clean = value.substringBefore(".").substringBefore("+").substringBefore("Z").trim()
        return runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.parse(clean)?.time
        }.getOrNull() ?: 0L
    }
}
