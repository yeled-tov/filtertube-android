package com.filtertube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * קונפיגורציה מהענן — "מערכת עדכון API" בלי לשנות קוד.
 *
 * האפליקציה מושכת בהפעלה קובץ `remote_config.json` מ-GitHub. כשיוטיוב משתנה
 * (גרסת לקוח, User-Agent וכו'), עורכים את ה-JSON ב-GitHub וכל הטלפונים מתעדכנים —
 * בלי בנייה חדשה ובלי עדכון אפליקציה. אם המשיכה נכשלת, נופלים לערכי ברירת המחדל
 * המוטמעים בקוד, כך שתמיד יש בסיס עובד. GitHub חינמי ולא חוסם על חוסר פעילות.
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
}
