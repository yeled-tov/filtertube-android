package com.filtertube.app.data

import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.TimeUnit

/**
 * ניהול רשימת הערוצים דרך GitHub API — מוסיף/מסיר ערוצים ע"י עריכת channels.json ב-repo.
 * שינויים מתפרסמים אוטומטית לכל המשתמשים (האפליקציה קוראת מ-GitHub raw).
 */
object ChannelAdmin {

    private const val OWNER = "yeled-tov"
    private const val REPO = "filtertube-android"
    private const val PATH = "channels.json"
    private const val API = "https://api.github.com/repos/$OWNER/$REPO/contents/$PATH"

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val http = Http.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** תוצאת זיהוי ערוץ — כולל קישור ותמונה, להצגה למשתמש לפני שליחת הבקשה. */
    data class Resolved(
        val channelId: String,
        val name: String,
        val url: String,
        val avatarUrl: String?,
    )

    /**
     * חילוץ ערוץ דרך NewPipe הוא כבד (שתי בקשות רשת ופענוח JSON גדול), ולא ניתן
     * להפסקה באמצע. בלי המנעול הזה, כל הקלדה במסך "בקשת הוספת ערוץ" פתחה חילוץ
     * נוסף במקביל: עשר אותיות = עשרה חילוצים בו-זמנית, שחנקו את מאגר ה-IO והפילו
     * את האפליקציה בזיכרון על מכשירים חלשים. מנעול = חילוץ אחד בכל רגע נתון.
     */
    private val resolveLock = Mutex()

    /**
     * מזהה ערוץ YouTube משם, קישור, handle או מזהה UC.
     *
     * מחזיר null אם לא נמצא ערוץ. **לא זורק לעולם** — גם לא על [Error]
     * (כמו OutOfMemoryError מפענוח תגובה גדולה), שהגרסה הקודמת לא תפסה.
     * ביטול הקורוטינה כן מועבר הלאה, כדי שהקלדה חדשה באמת תבטל חיפוש ישן.
     */
    suspend fun resolveChannel(input: String): Resolved? = withContext(Dispatchers.IO) {
        val raw = input.trim()
        if (raw.length < 2) return@withContext null
        resolveLock.withLock {
            try {
                // שם רגיל (למשל "עומר אדם") עובר דרך חיפוש ערוצים של NewPipe;
                // קישור, handle או מזהה UC מטופלים ישירות ובלי חיפוש.
                val url = if (raw.startsWith("http") || raw.startsWith("UC") || raw.startsWith("@")) {
                    normalizeChannelUrl(raw)
                } else {
                    val query = ServiceList.YouTube.searchQHFactory.fromQuery(raw, listOf("channels"), "")
                    val info = SearchInfo.getInfo(ServiceList.YouTube, query)
                    val candidate = info.relatedItems.firstOrNull { item ->
                        val u = item.url ?: ""
                        u.contains("youtube.com/channel/") || u.contains("youtube.com/@")
                    } ?: info.relatedItems.firstOrNull()
                    candidate?.url ?: return@withLock null
                }
                val info = ChannelInfo.getInfo(ServiceList.YouTube, url)
                val channelId = Regex("/channel/(UC[\\w-]+)").find(info.url)?.groupValues?.get(1)
                    ?: Regex("(UC[\\w-]{20,})").find(info.id ?: "")?.groupValues?.get(1)
                    ?: return@withLock null
                Resolved(
                    channelId = channelId,
                    name = info.name ?: channelId,
                    url = "https://www.youtube.com/channel/$channelId",
                    avatarUrl = runCatching {
                        info.avatars?.maxByOrNull { it.height }?.url
                    }.getOrNull(),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                android.util.Log.w("ChannelAdmin", "resolveChannel('$raw') failed", error)
                null
            }
        }
    }

    /** סרטון בתצוגה המקדימה — מספיק כדי להבין מה יש בערוץ, בלי לפתוח אותו. */
    data class PreviewVideo(
        val id: String,
        val title: String,
        val thumbnail: String?,
        val durationSec: Long,
    )

    /** מה שהמנהל רואה לפני שהוא מאשר: הערוץ עצמו, וגם מה באמת מתפרסם בו. */
    data class Preview(
        val resolved: Resolved,
        val subscribers: Long,
        val description: String,
        val videos: List<PreviewVideo>,
    )

    /**
     * תצוגה מקדימה של ערוץ — הפרטים שלו והסרטונים האחרונים בו.
     *
     * בקשת הוספה מגיעה עם שם וקישור בלבד, ומהם אי אפשר לדעת מה מתפרסם בערוץ.
     * אישור על סמך שם הוא בדיוק מה שהרשימה הלבנה אמורה למנוע, ולכן המסך הזה
     * מביא את הסרטונים עצמם.
     *
     * החילוץ נעשה דרך [resolveChannel] ואז שליפה נוספת של הערוץ: זו אמנם בקשת
     * רשת שנייה, אבל היא רצה פעם אחת בלחיצה ידנית של המנהל, לא בלולאה.
     * **לא זורק** — ערוץ שנפתח אך לא ניתן למשוך ממנו סרטונים יחזור עם רשימה
     * ריקה, כדי שהמנהל עדיין יוכל לדחות או לפתוח ביוטיוב.
     */
    suspend fun previewChannel(input: String, max: Int = 12): Preview? = withContext(Dispatchers.IO) {
        val resolved = resolveChannel(input) ?: return@withContext null
        val info = runCatching { ChannelInfo.getInfo(ServiceList.YouTube, resolved.url) }.getOrNull()
            ?: return@withContext Preview(resolved, -1L, "", emptyList())

        val subscribers = runCatching { info.subscriberCount }.getOrDefault(-1L)
        val description = runCatching { info.description ?: "" }.getOrDefault("")
        val videos = runCatching {
            val tab = info.tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.VIDEOS) }
                ?: info.tabs.firstOrNull()
                ?: return@runCatching emptyList<PreviewVideo>()
            ChannelTabInfo.getInfo(ServiceList.YouTube, tab).relatedItems
                .filterIsInstance<StreamInfoItem>()
                .take(max)
                .mapNotNull { item ->
                    val id = Regex("(?:v=|/shorts/|youtu\\.be/)([\\w-]{11})").find(item.url ?: "")
                        ?.groupValues?.get(1) ?: return@mapNotNull null
                    PreviewVideo(
                        id = id,
                        title = item.name ?: "",
                        thumbnail = runCatching { item.thumbnails?.maxByOrNull { it.height }?.url }.getOrNull()
                            ?: "https://i.ytimg.com/vi/$id/mqdefault.jpg",
                        durationSec = runCatching { item.duration }.getOrDefault(0L),
                    )
                }
        }.getOrDefault(emptyList())

        Preview(resolved, subscribers, description, videos)
    }

    private fun normalizeChannelUrl(input: String): String = when {
        input.startsWith("http") -> input
        input.startsWith("UC") && input.length >= 20 -> "https://www.youtube.com/channel/$input"
        input.startsWith("@") -> "https://www.youtube.com/$input"
        else -> "https://www.youtube.com/@$input"
    }

    /**
     * מושך את הקובץ הנוכחי מ-GitHub: מחזיר (channels, sha).
     */
    suspend fun fetchCurrent(token: String): Pair<MutableList<Channel>, String> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(API)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("GitHub ${resp.code}: ${resp.message}")
            val body = resp.body?.string() ?: throw RuntimeException("empty")
            val obj = JSONObject(body)
            val sha = obj.getString("sha")
            val contentB64 = obj.getString("content").replace("\n", "")
            val decoded = String(Base64.decode(contentB64, Base64.DEFAULT))
            val channels = json.decodeFromString<List<Channel>>(decoded).toMutableList()
            channels to sha
        }
    }

    /**
     * דוחף רשימה מעודכנת ל-GitHub.
     */
    suspend fun commit(token: String, channels: List<Channel>, sha: String, message: String): Boolean =
        withContext(Dispatchers.IO) {
            val newContent = json.encodeToString(channels)
            val b64 = Base64.encodeToString(newContent.toByteArray(), Base64.NO_WRAP)
            val payload = JSONObject().apply {
                // [skip ci] — שינוי ערוצים לא יפעיל בנייה/Release ולא יציג עדכון מדומה
                put("message", "$message [skip ci]")
                put("content", b64)
                put("sha", sha)
            }.toString()
            val req = Request.Builder()
                .url(API)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .put(payload.toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    android.util.Log.w("ChannelAdmin", "commit failed ${resp.code}: ${resp.body?.string()}")
                }
                resp.isSuccessful
            }
        }
}
