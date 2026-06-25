package com.filtertube.app.data

import android.content.Context
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * תמונות פרופיל של ערוצים — נמשכות בבת אחת דרך YouTube Data API
 * (`channels.list`, 50 מזהים לקריאה, עלות 1 יחידת מכסה בלבד) ונשמרות במטמון מקומי.
 * המטמון הוא Compose-state כך שה-UI מתעדכן אוטומטית כשתמונה מגיעה.
 */
object ChannelAvatars {
    private const val KEY = "AIzaSyDLAo5cUv4lt1Tsad50aMGFE0jl-mfRtOk"
    private const val PREFS = "channel_avatars"

    /** channelId → URL של תמונת הפרופיל. */
    val cache: SnapshotStateMap<String, String> = mutableStateMapOf()

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Volatile private var prefsLoaded = false

    fun avatar(channelId: String?): String? = channelId?.let { cache[it] }

    /** מוודא שיש תמונות ל-[ids] — מושך רק את החסרים (בקבוצות של 50) ושומר במטמון. */
    suspend fun warm(context: Context, ids: List<String>) = withContext(Dispatchers.IO) {
        loadPrefs(context)
        val missing = ids.asSequence().filter { it.isNotBlank() && it !in cache }.distinct().toList()
        if (missing.isEmpty()) return@withContext
        missing.chunked(50).forEach { chunk ->
            runCatching {
                val idParam = chunk.joinToString(",")
                val url = "https://www.googleapis.com/youtube/v3/channels?part=snippet&id=$idParam&maxResults=50&key=$KEY"
                http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val items = JSONObject(resp.body?.string() ?: return@use).optJSONArray("items") ?: return@use
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val cid = item.optString("id")
                        val thumbs = item.optJSONObject("snippet")?.optJSONObject("thumbnails")
                        val thumb = thumbs?.optJSONObject("medium")?.optString("url")
                            ?: thumbs?.optJSONObject("default")?.optString("url")
                        if (cid.isNotBlank() && !thumb.isNullOrBlank()) cache[cid] = thumb
                    }
                }
            }
        }
        savePrefs(context)
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
