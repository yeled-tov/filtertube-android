package com.filtertube.app.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * מקור רשימת הערוצים המאושרים (Approved Channels).
 *
 * ארכיטקטורת M-P-F-G:
 * 1. Memory Cache
 * 2. Persistent Local Snapshot (מוצג מיד לשיפור מהירות)
 * 3. Firebase approvedChannels (מקור האמת הראשי)
 * 4. GitHub fallback
 * 5. Asset fallback
 */
object ChannelsRepository {

    private const val GITHUB_RAW =
        "https://raw.githubusercontent.com/yeled-tov/filtertube-android/main/channels.json"
    private const val APPROVED_API =
        "https://europe-west1-filter-tube-52d8e.cloudfunctions.net/listApprovedChannels"
    private const val SNAPSHOT_FILE_NAME = "channels_snapshot.json"
    private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 דקות TTL למטמון זיכרון

    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var cached: List<Channel>? = null

    @Volatile
    private var lastFetchTime: Long = 0L

    @Volatile
    private var isRefreshing: Boolean = false

    @Synchronized
    fun invalidate() {
        cached = null
        lastFetchTime = 0L
    }

    /**
     * מחזיר ערוצים מהמטמון/snapshot המקומי מיד, ומבצע רענון ברקע במידת הצורך.
     */
    suspend fun getChannels(context: Context): List<Channel> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val mem = cached

        if (mem != null && (now - lastFetchTime < CACHE_TTL_MS)) {
            return@withContext mem
        }

        // טעינה מ-snapshot מקומי
        val snapshot = loadFromSnapshot(context)
        if (snapshot.isNotEmpty()) {
            cached = snapshot
            // אם עבר TTL, מרעננים ברקע בלי לעכב את הקורא
            if (now - lastFetchTime >= CACHE_TTL_MS) {
                backgroundRefresh(context)
            }
            return@withContext snapshot
        }

        // אם אין snapshot מקומי, מבצעים רענון מלא (Firebase -> GitHub -> Asset)
        refresh(context)
    }

    /**
     * מחזיר מיד את רשימת הערוצים הקיימת בזיכרון או ב-snapshot, ללא המתנה לרשת.
     */
    fun getCachedChannelsFast(context: Context): List<Channel> {
        cached?.let { return it }
        val snapshot = loadFromSnapshot(context)
        if (snapshot.isNotEmpty()) {
            cached = snapshot
            return snapshot
        }
        val asset = loadFromAsset(context)
        if (asset.isNotEmpty()) {
            cached = asset
            return asset
        }
        return emptyList()
    }

    /**
     * מבצע רענון לרשימת הערוצים מול Firebase -> GitHub -> Asset ועדכון המטמון.
     */
    suspend fun refresh(context: Context): List<Channel> = withContext(Dispatchers.IO) {
        val fromServer = runCatching { fetchFromServer() }.getOrNull().orEmpty()
        val channels = if (fromServer.isNotEmpty()) {
            fromServer
        } else {
            val fromGithub = runCatching { fetchFromGithub() }.getOrNull().orEmpty()
            if (fromGithub.isNotEmpty()) fromGithub else loadFromAsset(context)
        }

        if (channels.isNotEmpty()) {
            cached = channels
            lastFetchTime = System.currentTimeMillis()
            saveToSnapshot(context, channels)
        }
        channels
    }

    private fun backgroundRefresh(context: Context) {
        if (isRefreshing) return
        isRefreshing = true
        GlobalScope.launch(Dispatchers.IO) {
            try {
                refresh(context)
            } catch (_: Exception) {
            } finally {
                isRefreshing = false
            }
        }
    }

    private fun loadFromSnapshot(context: Context): List<Channel> {
        return runCatching {
            val file = File(context.filesDir, SNAPSHOT_FILE_NAME)
            if (!file.exists()) return emptyList()
            val text = file.readText()
            json.decodeFromString<List<Channel>>(text)
        }.getOrNull().orEmpty()
    }

    private fun saveToSnapshot(context: Context, channels: List<Channel>) {
        runCatching {
            val file = File(context.filesDir, SNAPSHOT_FILE_NAME)
            val text = json.encodeToString(channels)
            file.writeText(text)
        }
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
                    if (id.isNotBlank()) add(
                        Channel(
                            id,
                            item.optString("name", id),
                            item.optString("category", "general"),
                            item.optString("gender", "all")
                        )
                    )
                }
            }
        }
    }

    private suspend fun fetchFromGithub(): List<Channel> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("$GITHUB_RAW?t=${System.currentTimeMillis()}").build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use emptyList()
            val body = response.body?.string() ?: return@use emptyList()
            json.decodeFromString<List<Channel>>(body)
        }
    }

    private fun loadFromAsset(context: Context): List<Channel> {
        return runCatching {
            val text = context.assets.open("channels.json").bufferedReader().use { it.readText() }
            json.decodeFromString<List<Channel>>(text)
        }.getOrNull().orEmpty()
    }
}
