package com.filtertube.app.data

import android.content.Context
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo

/**
 * תמונות פרופיל של ערוצים — מחולצות דרך NewPipe ונשמרות במטמון קבוע.
 *
 * ## למה לא YouTube Data API
 * הגרסה הקודמת משכה `channels.list` עם מפתח API. זה היה זול במכסה (50 ערוצים
 * לקריאה), אבל השתמש באותו מפתח ובאותה מכסה יומית משותפת כמו שאר האפליקציה —
 * כך שברגע שהמכסה נגמרה גם התמונות הפסיקו להופיע. NewPipe עובד בלי מפתח
 * ובלי מכסה, אז אין תלות משותפת שיכולה להיגמר.
 *
 * ## העלות והאיזון
 * NewPipe דורש חילוץ נפרד לכל ערוץ, בעוד ה-API משך 50 בבקשה אחת. לכן:
 * המטמון קבוע (SharedPreferences) וכל ערוץ נמשך **פעם אחת בלבד לכל החיים**,
 * ובכל קריאה נמשכים לכל היותר [MAX_PER_CALL] ערוצים חסרים. הרשימה מתמלאת
 * הדרגתית על פני כמה כניסות למסך במקום לחנוק את הרשת בכניסה אחת.
 */
object ChannelAvatars {
    private const val PREFS = "channel_avatars"

    /** כמה ערוצים חסרים לחלץ בכל קריאה. השאר יימשכו בכניסה הבאה למסך. */
    private const val MAX_PER_CALL = 16

    /** חילוצים מקבילים — גבוה מדי חונק את מאגר ה-IO. */
    private const val CONCURRENCY = 4

    /** channelId → URL של תמונת הפרופיל. */
    val cache: SnapshotStateMap<String, String> = mutableStateMapOf()

    @Volatile private var prefsLoaded = false

    fun avatar(channelId: String?): String? = channelId?.let { cache[it] }

    /** מוודא שיש תמונות ל-[ids] — מחלץ רק את החסרים ושומר במטמון הקבוע. */
    suspend fun warm(context: Context, ids: List<String>) = coroutineScope {
        loadPrefs(context)
        val missing = ids.asSequence()
            .filter { it.startsWith("UC") && it !in cache }
            .distinct()
            .take(MAX_PER_CALL)
            .toList()
        if (missing.isEmpty()) return@coroutineScope

        val gate = Semaphore(CONCURRENCY)
        val found = missing.map { channelId ->
            async(Dispatchers.IO) {
                gate.withPermit {
                    channelId to runCatching { extractAvatar(channelId) }.getOrNull()
                }
            }
        }.awaitAll()

        var added = 0
        found.forEach { (channelId, url) ->
            if (!url.isNullOrBlank()) { cache[channelId] = url; added++ }
        }
        if (added > 0) {
            Diagnostics.log("AVATARS: $added תמונות ערוצים חולצו דרך NewPipe (אפס מכסה)")
            savePrefs(context)
        }
    }

    /**
     * בוחר את התמונה הקטנה ביותר שעדיין חדה מספיק לרשימה (120px ומעלה),
     * ורק אם אין כזו נופל לגדולה ביותר — כדי לא לטעון תמונת ענק לכל שורה.
     */
    private fun extractAvatar(channelId: String): String? {
        val info = ChannelInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/channel/$channelId")
        val avatars = info.avatars ?: return null
        val pick = avatars.filter { it.height >= 120 }.minByOrNull { it.height }
            ?: avatars.maxByOrNull { it.height }
        return pick?.url
    }

    private fun loadPrefs(context: Context) {
        if (prefsLoaded) return
        synchronized(this) {
            if (prefsLoaded) return
            runCatching {
                val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("map", null)
                if (raw != null) {
                    val obj = JSONObject(raw)
                    obj.keys().forEach { k -> cache[k] = obj.optString(k) }
                }
            }
            prefsLoaded = true
        }
    }

    private fun savePrefs(context: Context) {
        runCatching {
            val obj = JSONObject()
            cache.forEach { (k, v) -> obj.put(k, v) }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("map", obj.toString()).apply()
        }
    }
}
