package com.filtertube.app.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Playlist(val name: String, val videos: List<Video> = emptyList())

/**
 * Local library storage. Every instance is bound to the Firebase/account-data
 * generation that created it, so a stale coroutine cannot read or mutate the
 * next account's SharedPreferences after sign-out or account switching.
 */
class LibraryStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(
        "filtertube_library",
        Context.MODE_PRIVATE,
    )
    private val json = Json { ignoreUnknownKeys = true }
    private val sessionUid = FirebaseAuth.getInstance().currentUser?.uid
    private val sessionVerified =
        FirebaseAuth.getInstance().currentUser?.isEmailVerified == true
    private val sessionGeneration = AccountDataGuard.generation()

    private fun sessionMatches(): Boolean {
        val current = FirebaseAuth.getInstance().currentUser
        return AccountDataGuard.generation() == sessionGeneration &&
            current?.uid == sessionUid &&
            (sessionUid == null || current?.isEmailVerified == sessionVerified)
    }

    private fun videos(key: String): List<Video> = AccountDataGuard.withLock {
        if (!sessionMatches()) return@withLock emptyList()
        prefs.getString(key, null)?.let {
            runCatching { json.decodeFromString<List<Video>>(it) }.getOrNull()
        } ?: emptyList()
    }

    private fun saveVideos(key: String, list: List<Video>): Boolean =
        AccountDataGuard.withLock {
            if (!sessionMatches()) return@withLock false
            prefs.edit().putString(key, json.encodeToString(list)).apply()
            true
        }

    private fun queueCloudBackup() {
        AccountDataGuard.withLock {
            if (sessionMatches()) CloudSync.enqueueUpload(appContext)
        }
    }

    fun likes(): List<Video> = videos(KEY_LIKES)

    fun isLiked(videoId: String): Boolean = likes().any { it.id == videoId }

    fun toggleLike(video: Video): Boolean {
        val current = likes().toMutableList()
        val existed = current.removeAll { it.id == video.id }
        if (!existed) current.add(0, video)
        if (!saveVideos(KEY_LIKES, current)) return false
        queueCloudBackup()
        return !existed
    }

    fun downloads(): List<Video> = videos(KEY_DOWNLOADS)

    fun addDownload(video: Video) {
        val current = downloads().toMutableList()
        current.removeAll { it.id == video.id }
        current.add(0, video)
        if (saveVideos(KEY_DOWNLOADS, current)) queueCloudBackup()
    }

    fun playlists(): List<Playlist> = AccountDataGuard.withLock {
        if (!sessionMatches()) return@withLock emptyList()
        prefs.getString(KEY_PLAYLISTS, null)?.let {
            runCatching { json.decodeFromString<List<Playlist>>(it) }.getOrNull()
        } ?: emptyList()
    }

    private fun savePlaylists(list: List<Playlist>): Boolean =
        AccountDataGuard.withLock {
            if (!sessionMatches()) return@withLock false
            prefs.edit().putString(KEY_PLAYLISTS, json.encodeToString(list)).apply()
            true
        }

    fun createPlaylist(name: String) {
        val normalized = name.trim()
        if (normalized.isEmpty()) return
        val list = playlists()
        if (list.any { it.name == normalized }) return
        if (savePlaylists(list + Playlist(normalized))) queueCloudBackup()
    }

    fun deletePlaylist(name: String) {
        if (savePlaylists(playlists().filter { it.name != name })) {
            queueCloudBackup()
        }
    }

    fun addToPlaylist(name: String, video: Video) {
        val list = playlists().toMutableList()
        val index = list.indexOfFirst { it.name == name }
        if (index < 0) return
        val playlist = list[index]
        if (playlist.videos.any { it.id == video.id }) return
        list[index] = playlist.copy(videos = playlist.videos + video)
        if (savePlaylists(list)) queueCloudBackup()
    }

    /** Removes a video from all local library collections and syncs the change. */
    fun removeVideo(video: Video) {
        var changed = false
        fun removeFrom(key: String) {
            val current = videos(key)
            val filtered = current.filterNot { it.id == video.id }
            if (filtered.size != current.size) {
                changed = true
                saveVideos(key, filtered)
            }
        }
        listOf(KEY_LIKES, KEY_DOWNLOADS, KEY_HISTORY, KEY_LOCAL_HISTORY, KEY_RECS, KEY_NEW_VIDEOS, KEY_YT_LIKES)
            .forEach(::removeFrom)
        val updatedPlaylists = playlists().map { playlist ->
            val filtered = playlist.videos.filterNot { it.id == video.id }
            if (filtered.size != playlist.videos.size) changed = true
            playlist.copy(videos = filtered)
        }
        if (changed) {
            savePlaylists(updatedPlaylists)
            queueCloudBackup()
        }
    }

    fun youtubeLikes(): List<Video> = videos(KEY_YT_LIKES)

    fun setYoutubeLikes(list: List<Video>) {
        if (saveVideos(KEY_YT_LIKES, list)) queueCloudBackup()
    }

    fun subscriptions(): List<SubChannel> = AccountDataGuard.withLock {
        if (!sessionMatches()) return@withLock emptyList()
        prefs.getString(KEY_SUBS, null)?.let {
            runCatching { json.decodeFromString<List<SubChannel>>(it) }.getOrNull()
        } ?: emptyList()
    }

    fun setSubscriptions(list: List<SubChannel>) {
        val saved = AccountDataGuard.withLock {
            if (!sessionMatches()) return@withLock false
            prefs.edit().putString(KEY_SUBS, json.encodeToString(list)).apply()
            true
        }
        if (saved) queueCloudBackup()
    }

    fun history(): List<Video> = videos(KEY_HISTORY)

    fun setHistory(list: List<Video>) {
        if (saveVideos(KEY_HISTORY, list)) queueCloudBackup()
    }

    fun recommendations(): List<Video> = videos(KEY_RECS)

    fun setRecommendations(list: List<Video>) {
        if (saveVideos(KEY_RECS, list)) queueCloudBackup()
    }

    fun localHistory(): List<Video> = videos(KEY_LOCAL_HISTORY)

    fun addToHistory(video: Video) {
        if (video.id.isBlank()) return
        val current = localHistory().toMutableList()
        current.removeAll { it.id == video.id }
        current.add(0, video.copy(publishedAt = System.currentTimeMillis()))
        while (current.size > HISTORY_CAP) current.removeAt(current.lastIndex)
        if (saveVideos(KEY_LOCAL_HISTORY, current)) queueCloudBackup()
    }

    fun clearLocalHistory() {
        if (saveVideos(KEY_LOCAL_HISTORY, emptyList())) queueCloudBackup()
    }

    fun localSubscriptions(): Set<String> = AccountDataGuard.withLock {
        if (!sessionMatches()) return@withLock emptySet()
        prefs.getStringSet(KEY_LOCAL_SUBS, emptySet())?.toSet() ?: emptySet()
    }

    fun isSubscribed(channelId: String): Boolean =
        channelId in localSubscriptions()

    fun toggleSubscription(channelId: String): Boolean {
        if (channelId.isBlank()) return false
        val result = AccountDataGuard.withLock {
            if (!sessionMatches()) return@withLock null
            val current =
                prefs.getStringSet(KEY_LOCAL_SUBS, emptySet())?.toMutableSet()
                    ?: mutableSetOf()
            val added = if (channelId in current) {
                current.remove(channelId)
                false
            } else {
                current.add(channelId)
                true
            }
            prefs.edit().putStringSet(KEY_LOCAL_SUBS, current).apply()
            added
        } ?: return false
        queueCloudBackup()
        return result
    }

    fun newVideos(): List<Video> = videos(KEY_NEW_VIDEOS)

    fun addNewVideos(list: List<Video>) {
        if (list.isEmpty()) return
        val current = newVideos().toMutableList()
        list.asReversed().forEach { video ->
            current.removeAll { it.id == video.id }
            current.add(0, video)
        }
        while (current.size > 50) current.removeAt(current.lastIndex)
        if (saveVideos(KEY_NEW_VIDEOS, current)) queueCloudBackup()
    }

    fun clearNewVideos() {
        if (saveVideos(KEY_NEW_VIDEOS, emptyList())) queueCloudBackup()
    }

    // Restore helpers intentionally do not enqueue another upload.
    fun replaceLikes(list: List<Video>) {
        saveVideos(KEY_LIKES, list)
    }

    fun replaceDownloads(list: List<Video>) {
        saveVideos(KEY_DOWNLOADS, list)
    }

    fun replacePlaylists(list: List<Playlist>) {
        savePlaylists(list)
    }

    fun replaceYoutubeLikes(list: List<Video>) {
        saveVideos(KEY_YT_LIKES, list)
    }

    fun replaceSubscriptions(list: List<SubChannel>) {
        AccountDataGuard.withLock {
            if (sessionMatches()) {
                prefs.edit().putString(KEY_SUBS, json.encodeToString(list)).apply()
            }
        }
    }

    fun replaceHistory(list: List<Video>) {
        saveVideos(KEY_HISTORY, list)
    }

    fun replaceRecommendations(list: List<Video>) {
        saveVideos(KEY_RECS, list)
    }

    fun replaceLocalHistory(list: List<Video>) {
        saveVideos(KEY_LOCAL_HISTORY, list.take(HISTORY_CAP))
    }

    fun replaceNewVideos(list: List<Video>) {
        saveVideos(KEY_NEW_VIDEOS, list.take(50))
    }

    fun replaceLocalSubscriptions(channels: Collection<String>) {
        AccountDataGuard.withLock {
            if (sessionMatches()) {
                prefs.edit().putStringSet(
                    KEY_LOCAL_SUBS,
                    channels.filter { it.isNotBlank() }.toSet(),
                ).apply()
            }
        }
    }

    fun clearAccountData() {
        AccountDataGuard.withLock {
            if (sessionMatches()) prefs.edit().clear().apply()
        }
    }

    companion object {
        private const val KEY_LIKES = "likes"
        private const val KEY_DOWNLOADS = "downloads"
        private const val KEY_PLAYLISTS = "playlists"
        private const val KEY_YT_LIKES = "youtube_likes"
        private const val KEY_SUBS = "youtube_subscriptions"
        private const val KEY_HISTORY = "youtube_history"
        private const val KEY_RECS = "youtube_recommendations"
        private const val KEY_LOCAL_HISTORY = "local_history"
        private const val HISTORY_CAP = 200
        private const val KEY_LOCAL_SUBS = "local_subscriptions"
        private const val KEY_NEW_VIDEOS = "new_videos_inbox"
    }
}
