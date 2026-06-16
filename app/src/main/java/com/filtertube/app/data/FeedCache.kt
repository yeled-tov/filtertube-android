package com.filtertube.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * קאש מקומי לפיד הראשי וה-Shorts.
 *
 * מטרה: פתיחת האפליקציה תהיה מיידית — מציגים את התוכן השמור מהפעם הקודמת,
 * ובמקביל מרעננים ברקע. כך אין יותר המתנה ארוכה למשיכת RSS מ-~100 ערוצים.
 */
object FeedCache {

    private val json = Json { ignoreUnknownKeys = true }

    // קאש בזיכרון — ניווט בין טאבים לא מושך מחדש
    @Volatile private var feedMemory: List<Video>? = null
    @Volatile private var shortsMemory: List<Video>? = null

    private fun file(context: Context, name: String) = File(context.cacheDir, name)

    // ── פיד ראשי ─────────────────────────────────────────────────────────
    suspend fun loadFeed(context: Context): List<Video>? = load(context, "feed_cache.json") { feedMemory }
        ?.also { feedMemory = it }

    suspend fun saveFeed(context: Context, videos: List<Video>) {
        feedMemory = videos
        save(context, "feed_cache.json", videos)
    }

    // ── Shorts ───────────────────────────────────────────────────────────
    suspend fun loadShorts(context: Context): List<Video>? = load(context, "shorts_cache.json") { shortsMemory }
        ?.also { shortsMemory = it }

    suspend fun saveShorts(context: Context, videos: List<Video>) {
        shortsMemory = videos
        save(context, "shorts_cache.json", videos)
    }

    // ── עזר ──────────────────────────────────────────────────────────────
    private suspend inline fun load(
        context: Context,
        name: String,
        memory: () -> List<Video>?,
    ): List<Video>? {
        memory()?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val f = file(context, name)
                if (!f.exists()) return@runCatching null
                json.decodeFromString<List<Video>>(f.readText())
            }.getOrNull()
        }
    }

    private suspend fun save(context: Context, name: String, videos: List<Video>) {
        withContext(Dispatchers.IO) {
            runCatching { file(context, name).writeText(json.encodeToString(videos)) }
        }
    }
}
