package com.filtertube.app.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * בקשות הוספת ערוץ מהמשתמשים → תיקיית `channel-requests/` ב-GitHub.
 * השליחה משתמשת בטוקן המוטמע (BUG_REPORT_TOKEN, קיים בכל לקוח) — בדיוק כמו דיווחי הבאגים.
 * המנהל קורא את הבקשות (ציבורי) ומאשר/דוחה (אישור = הוספה ל-channels.json דרך ה-PAT).
 */
object ChannelRequests {

    private const val OWNER = "yeled-tov"
    private const val REPO = "filtertube-android"
    private const val DIR = "channel-requests"
    private const val API = "https://api.github.com/repos/$OWNER/$REPO/contents/$DIR"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class Req(
        val fileName: String,
        val sha: String,
        val name: String,
        val url: String,
        val category: String,
        val gender: String,
        val description: String,
        val requestedAt: String,
    )

    /** שולח בקשת הוספת ערוץ. true בהצלחה. */
    suspend fun submit(name: String, url: String, category: String, gender: String, description: String): Boolean =
        withContext(Dispatchers.IO) {
            val token = com.filtertube.app.BuildConfig.BUG_REPORT_TOKEN
            if (token.isBlank()) return@withContext false
            val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val fileName = "$ts-${(1000..9999).random()}.json"
            val body = JSONObject().apply {
                put("name", name.trim())
                put("url", url.trim())
                put("category", category)
                put("gender", gender)
                put("description", description.trim())
                put("requestedAt", ts)
                put("device", android.os.Build.MODEL)
            }.toString()
            val b64 = Base64.encodeToString(body.toByteArray(), Base64.NO_WRAP)
            val payload = JSONObject().apply {
                put("message", "channel request: ${name.trim()} [skip ci]")
                put("content", b64)
            }.toString()
            val req = Request.Builder()
                .url("$API/$fileName")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .put(payload.toRequestBody("application/json".toMediaType()))
                .build()
            runCatching { http.newCall(req).execute().use { it.isSuccessful } }.getOrDefault(false)
        }

    /** טוען את כל הבקשות הממתינות (תיקייה ציבורית; [token] אופציונלי למניעת rate-limit). */
    suspend fun list(token: String = ""): List<Req> = withContext(Dispatchers.IO) {
        val listReq = Request.Builder().url(API)
            .header("Accept", "application/vnd.github+json")
            .apply { if (token.isNotBlank()) header("Authorization", "Bearer $token") }
            .build()
        val items = runCatching {
            http.newCall(listReq).execute().use { resp ->
                if (!resp.isSuccessful) return@use JSONArray()
                JSONArray(resp.body?.string() ?: "[]")
            }
        }.getOrDefault(JSONArray())

        val out = mutableListOf<Req>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            if (item.optString("type") != "file") continue
            val fileName = item.optString("name")
            if (!fileName.endsWith(".json")) continue
            val sha = item.optString("sha")
            val dl = item.optString("download_url")
            runCatching {
                http.newCall(Request.Builder().url(dl).build()).execute().use { r ->
                    val obj = JSONObject(r.body?.string() ?: return@use)
                    out.add(
                        Req(
                            fileName = fileName, sha = sha,
                            name = obj.optString("name"), url = obj.optString("url"),
                            category = obj.optString("category", "general"),
                            gender = obj.optString("gender", ""),
                            description = obj.optString("description"),
                            requestedAt = obj.optString("requestedAt"),
                        ),
                    )
                }
            }
        }
        out.sortedByDescending { it.requestedAt }
    }

    /** מוחק בקשה (אחרי אישור/דחייה) — דורש PAT. */
    suspend fun delete(token: String, fileName: String, sha: String): Boolean = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("message", "resolve channel request $fileName [skip ci]")
            put("sha", sha)
        }.toString()
        val req = Request.Builder()
            .url("$API/$fileName")
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .delete(payload.toRequestBody("application/json".toMediaType()))
            .build()
        runCatching { http.newCall(req).execute().use { it.isSuccessful } }.getOrDefault(false)
    }
}
