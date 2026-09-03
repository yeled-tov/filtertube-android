package com.filtertube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * קונפיגורציה מהענן — מנגנון שליטה מרכזי במנועי ניגון.
 *
 * מאפשר להשבית מנוע שנשבר, לשנות עדיפות (priority), עדכון User-Agent ופרמטרי בריאות
 * מ-GitHub remote_config.json ללא צורך בהוצאת APK חדש.
 */
object RemoteConfig {

    private const val URL =
        "https://raw.githubusercontent.com/yeled-tov/filtertube-android/main/remote_config.json"

    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cfg: JSONObject? = null

    /** מושכים פעם אחת בהפעלה (fire-and-forget). חותמת-זמן עוקפת מטמון CDN. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$URL?t=${System.currentTimeMillis()}").build()
            http.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    cfg = resp.body?.string()?.let { JSONObject(it) }
                    Diagnostics.log("RemoteConfig נטען מהענן ✓")
                }
            }
        }
        Unit
    }

    private fun client(name: String): JSONObject? = cfg?.optJSONObject("innertube")?.optJSONObject(name)

    private fun str(name: String, field: String, default: String): String =
        client(name)?.optString(field)?.takeIf { it.isNotBlank() } ?: default

    fun iosVersion(default: String) = str("ios", "clientVersion", default)
    fun iosUserAgent(default: String) = str("ios", "userAgent", default)
    fun iosDeviceModel(default: String) = str("ios", "deviceModel", default)
    fun iosOsVersion(default: String) = str("ios", "osVersion", default)

    fun vrVersion(default: String) = str("vr", "clientVersion", default)
    fun vrUserAgent(default: String) = str("vr", "userAgent", default)

    fun isResolverEnabled(resolverName: String, default: Boolean = true): Boolean {
        val resolvers = cfg?.optJSONObject("resolvers") ?: return default
        val res = resolvers.optJSONObject(resolverName) ?: return default
        return res.optBoolean("enabled", default)
    }

    fun resolverPriority(): List<String> {
        val list = mutableListOf<String>()
        val arr = cfg?.optJSONObject("resolvers")?.optJSONArray("priority")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val name = arr.optString(i)
                if (name.isNotBlank()) list.add(name)
            }
        }
        // ברירת מחדל: IOS קודם, לאחר מכן ANDROID_VR, ובסוף NewPipe
        if (list.isEmpty()) {
            return listOf("IOS", "ANDROID_VR", "NewPipe")
        }
        return list
    }

    fun maxConsecutiveFailures(default: Int = 3): Int {
        return cfg?.optJSONObject("health")?.optInt("maxConsecutiveFailures", default) ?: default
    }

    fun cooldownDurationMinutes(default: Long = 5): Long {
        return cfg?.optJSONObject("health")?.optLong("cooldownDurationMinutes", default) ?: default
    }
}
