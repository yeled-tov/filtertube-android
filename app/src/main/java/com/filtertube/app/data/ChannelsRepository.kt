package com.filtertube.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import com.google.firebase.auth.FirebaseAuth

/**
 * מקור רשימת הערוצים המאושרים — ללא Supabase.
 *
 * 1. מנסה למשוך מ-GitHub raw (כך אפשר לעדכן ערוצים ע"י עריכת channels.json ב-GitHub)
 * 2. נופל ל-asset מקומי שמוטמע באפליקציה (עובד offline)
 *
 * לעדכון רשימת הערוצים: ערוך את channels.json ב-repo ב-GitHub. האפליקציה תמשוך אוטומטית.
 */
object ChannelsRepository {

    private const val GITHUB_RAW =
        "https://raw.githubusercontent.com/yeled-tov/filtertube-android/main/channels.json"
    private const val APPROVED_API =
        "https://europe-west1-filter-tube-52d8e.cloudfunctions.net/listApprovedChannels"

    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cached: List<Channel>? = null

    suspend fun getChannels(context: Context): List<Channel> {
        cached?.let { return it }

        val fromGithub = runCatching { fetchFromGithub() }.getOrNull().orEmpty()
        val fromServer = runCatching { fetchFromServer() }.getOrNull().orEmpty()
        val merged = (fromGithub + fromServer).associateBy { it.youtubeChannelId }.values.toList()
        if (merged.isNotEmpty()) {
            cached = merged
            return merged
        }
        val fromAsset = runCatching { loadFromAsset(context) }.getOrNull().orEmpty()
        cached = fromAsset
        return fromAsset
    }

    private suspend fun fetchFromServer(): List<Channel> = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser?.takeIf { it.isEmailVerified }
            ?: return@withContext emptyList()
        val token = user.getIdToken(false).await().token ?: return@withContext emptyList()
        val request = Request.Builder().url(APPROVED_API)
            .header("Authorization", "Bearer $token").get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use emptyList()
            val root = org.json.JSONObject(response.body?.string().orEmpty())
            val items = root.optJSONArray("channels") ?: return@use emptyList()
            buildList {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val id = item.optString("youtubeChannelId")
                    if (id.isNotBlank()) add(Channel(id, item.optString("name", id), item.optString("category", "general"), item.optString("gender", "all")))
                }
            }
        }
    }

    private suspend fun fetchFromGithub(): List<Channel> = withContext(Dispatchers.IO) {
        // חותמת-זמן עוקפת את מטמון ה-CDN של GitHub raw (~5 דק') כדי לקבל עדכוני ערוצים
        // מפאנל הניהול כמעט מיד — בשתי האפליקציות (אותו URL בדיוק).
        val request = Request.Builder().url("$GITHUB_RAW?t=${System.currentTimeMillis()}").build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use emptyList()
            val body = response.body?.string() ?: return@use emptyList()
            json.decodeFromString<List<Channel>>(body)
        }
    }

    private suspend fun loadFromAsset(context: Context): List<Channel> = withContext(Dispatchers.IO) {
        val text = context.assets.open("channels.json").bufferedReader().use { it.readText() }
        json.decodeFromString<List<Channel>>(text)
    }
}
