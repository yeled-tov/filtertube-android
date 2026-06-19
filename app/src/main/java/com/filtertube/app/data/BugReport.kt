package com.filtertube.app.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * דיווח באגים מובנה — שולח דוח קריסה/שגיאה ל-GitHub (תיקיית crash-reports/) דרך
 * טוקן האדמין השמור בהגדרות. כך אפשר למשוך את הדוחות בקלות בלי להעתיק ידנית מהטלפון.
 *
 * שם הקובץ כולל זמן + דגם מכשיר + מספר אקראי כדי להיות ייחודי (יצירה, ללא sha).
 * ה-[skip ci] מבטיח שדוח באג לא יפעיל בנייה/Release.
 */
object BugReport {

    private const val OWNER = "yeled-tov"
    private const val REPO = "filtertube-android"
    private const val API = "https://api.github.com/repos/$OWNER/$REPO/contents/crash-reports"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * שולח דוח ל-GitHub. מחזיר true בהצלחה. דורש טוקן אדמין עם הרשאת contents.
     * @param note הערה חופשית של המשתמש (מה הוא עשה כשזה קרה) — אופציונלי.
     */
    suspend fun submit(token: String, report: String, note: String = ""): Boolean = withContext(Dispatchers.IO) {
        if (token.isBlank()) return@withContext false
        val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val device = android.os.Build.MODEL.replace(Regex("[^A-Za-z0-9]"), "")
        val name = "$ts-$device-${(1000..9999).random()}.txt"
        val full = buildString {
            if (note.isNotBlank()) append("הערת משתמש: ").append(note).append("\n\n")
            append(report)
        }
        val b64 = Base64.encodeToString(full.toByteArray(), Base64.NO_WRAP)
        val payload = JSONObject().apply {
            put("message", "bug report $name [skip ci]")
            put("content", b64)
        }.toString()
        val req = Request.Builder()
            .url("$API/$name")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .put(payload.toRequestBody("application/json".toMediaType()))
            .build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    android.util.Log.w("BugReport", "submit failed ${resp.code}: ${resp.body?.string()}")
                }
                resp.isSuccessful
            }
        }.getOrDefault(false)
    }
}
