package com.filtertube.app.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Playlist(val name: String, val videos: List<Video> = emptyList())

/**
 * ספריה מקומית: "אהבתי", הורדות, אלבומים (פלייליסטים), ומטמון של "אהבתי" מיוטיוב.
 * נשמר ב-SharedPreferences כ-JSON.
 */
class LibraryStore(context: Context) {
    private val prefs = context.getSharedPreferences("filtertube_library", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private fun videos(key: String): List<Video> =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString<List<Video>>(it) }.getOrNull() } ?: emptyList()

    private fun saveVideos(key: String, list: List<Video>) =
        prefs.edit().putString(key, json.encodeToString(list)).apply()

    // ── אהבתי (מקומי) ────────────────────────────────────────────────────
    fun likes(): List<Video> = videos(KEY_LIKES)
    fun isLiked(videoId: String): Boolean = likes().any { it.id == videoId }
    fun toggleLike(video: Video): Boolean {
        val current = likes().toMutableList()
        val existed = current.removeAll { it.id == video.id }
        if (!existed) current.add(0, video)
        saveVideos(KEY_LIKES, current)
        return !existed
    }

    // ── הורדות ───────────────────────────────────────────────────────────
    fun downloads(): List<Video> = videos(KEY_DOWNLOADS)
    fun addDownload(video: Video) {
        val current = videos(KEY_DOWNLOADS).toMutableList()
        current.removeAll { it.id == video.id }
        current.add(0, video)
        saveVideos(KEY_DOWNLOADS, current)
    }

    // ── אלבומים (פלייליסטים) ─────────────────────────────────────────────
    fun playlists(): List<Playlist> =
        prefs.getString(KEY_PLAYLISTS, null)?.let { runCatching { json.decodeFromString<List<Playlist>>(it) }.getOrNull() } ?: emptyList()

    private fun savePlaylists(list: List<Playlist>) =
        prefs.edit().putString(KEY_PLAYLISTS, json.encodeToString(list)).apply()

    fun createPlaylist(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        val list = playlists()
        if (list.any { it.name == n }) return
        savePlaylists(list + Playlist(n))
    }

    fun deletePlaylist(name: String) = savePlaylists(playlists().filter { it.name != name })

    fun addToPlaylist(name: String, video: Video) {
        val list = playlists().toMutableList()
        val idx = list.indexOfFirst { it.name == name }
        if (idx < 0) return
        val pl = list[idx]
        if (pl.videos.any { it.id == video.id }) return
        list[idx] = pl.copy(videos = pl.videos + video)
        savePlaylists(list)
    }

    // ── אהבתי מיוטיוב (מטמון של מה שנמשך מהחשבון) ────────────────────────
    fun youtubeLikes(): List<Video> = videos(KEY_YT_LIKES)
    fun setYoutubeLikes(list: List<Video>) = saveVideos(KEY_YT_LIKES, list)

    companion object {
        private const val KEY_LIKES = "likes"
        private const val KEY_DOWNLOADS = "downloads"
        private const val KEY_PLAYLISTS = "playlists"
        private const val KEY_YT_LIKES = "youtube_likes"
    }
}
