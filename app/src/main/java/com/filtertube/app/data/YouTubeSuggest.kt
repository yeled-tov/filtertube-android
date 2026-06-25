package com.filtertube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * השלמה אוטומטית (חיפוש חי) — דרך נקודת ה-suggest של יוטיוב.
 * client=firefox מחזיר JSON נקי: ["query", ["הצעה1","הצעה2", ...]].
 */
object YouTubeSuggest {

    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    suspend fun suggest(query: String): List<String> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val url = "https://suggestqueries.google.com/complete/search" +
            "?client=firefox&ds=yt&hl=he&q=${URLEncoder.encode(q, "UTF-8")}"
        runCatching {
            val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList<String>()
                val body = resp.body?.string() ?: return@use emptyList<String>()
                val list = JSONArray(body).optJSONArray(1) ?: return@use emptyList<String>()
                (0 until list.length())
                    .mapNotNull { list.optString(it).takeIf { s -> s.isNotBlank() } }
                    .take(8)
            }
        }.getOrDefault(emptyList())
    }
}
