package com.filtertube.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

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

    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cached: List<Channel>? = null

    suspend fun getChannels(context: Context): List<Channel> {
        cached?.let { return it }

        // 1. נסה GitHub raw
        val fromGithub = runCatching { fetchFromGithub() }.getOrNull()
        if (!fromGithub.isNullOrEmpty()) {
            cached = fromGithub
            return fromGithub
        }

        // 2. fallback ל-asset מקומי
        val fromAsset = runCatching { loadFromAsset(context) }.getOrNull().orEmpty()
        cached = fromAsset
        return fromAsset
    }

    private suspend fun fetchFromGithub(): List<Channel> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(GITHUB_RAW).build()
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
