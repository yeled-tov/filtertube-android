package com.filtertube.app.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

@Serializable
data class SnapshotMetadata(
    val timestamp: Long,
    val source: String,
    val version: Int = 1,
    val channels: List<Channel>
)

/**
 * מקור אמת יחיד לרשימת הערוצים המאושרים (Approved Channels Single Source of Truth).
 *
 * היררכיית סמכות (Authority Order):
 * 1. Firebase approvedChannels (מקור האמת הבלעדי כשהוא זמין — ללא מיזוג עם GitHub)
 * 2. Last Known Good Snapshot בלתי תלוי ברשת
 * 3. GitHub fallback במידה ואין snapshot
 * 4. Asset fallback במידה ואין תמונת מצב כלל
 */
object ChannelsRepository {

    private const val GITHUB_RAW =
        "https://raw.githubusercontent.com/yeled-tov/filtertube-android/main/channels.json"
    private const val APPROVED_API =
        "https://europe-west1-filter-tube-52d8e.cloudfunctions.net/listApprovedChannels"
    private const val SNAPSHOT_FILE_NAME = "channels_snapshot.json"
    private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 דקות TTL למטמון

    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val refreshMutex = Mutex()
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _approvedChannelsFlow = MutableStateFlow<List<Channel>>(emptyList())
    val approvedChannelsFlow: StateFlow<List<Channel>> = _approvedChannelsFlow.asStateFlow()

    @Volatile
    private var cached: List<Channel>? = null

    @Volatile
    private var lastFetchTime: Long = 0L

    fun invalidate() {
        lastFetchTime = 0L
        cached = null
        Diagnostics.log("ChannelsRepository: invalidate - מטמון ערוצים בוטל יזמית")
    }

    /**
     * מחזיר ערוצים מהמטמון/snapshot המקומי מיד, ומבצע רענון ברקע במידת הצורך.
     */
    suspend fun getChannels(context: Context): List<Channel> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val mem = cached ?: _approvedChannelsFlow.value.takeIf { it.isNotEmpty() }

        if (mem != null && (now - lastFetchTime < CACHE_TTL_MS)) {
            return@withContext mem
        }

        val snapshot = loadFromSnapshot(context)
        if (snapshot.isNotEmpty()) {
            cached = snapshot
            _approvedChannelsFlow.value = snapshot
            if (now - lastFetchTime >= CACHE_TTL_MS) {
                backgroundRefresh(context)
            }
            return@withContext snapshot
        }

        refresh(context)
    }

    /**
     * מחזיר מיד את רשימת הערוצים הקיימת בזיכרון או ב-snapshot, ללא המתנה לרשת.
     */
    fun getCachedChannelsFast(context: Context): List<Channel> {
        cached?.takeIf { it.isNotEmpty() }?.let { return it }
        val flowVal = _approvedChannelsFlow.value
        if (flowVal.isNotEmpty()) {
            cached = flowVal
            return flowVal
        }
        val snapshot = loadFromSnapshot(context)
        if (snapshot.isNotEmpty()) {
            cached = snapshot
            _approvedChannelsFlow.value = snapshot
            return snapshot
        }
        val asset = loadFromAsset(context)
        if (asset.isNotEmpty()) {
            cached = asset
            _approvedChannelsFlow.value = asset
            return asset
        }
        return emptyList()
    }

    /**
     * מבצע רענון יחיד ומאובטח (מניעת מרוץ באמצעות Mutex) של רשימת הערוצים המאושרים.
     */
    suspend fun refresh(context: Context): List<Channel> = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            val fromServer = runCatching { fetchFromServer() }.getOrNull().orEmpty()
            val (channels, source) = if (fromServer.isNotEmpty()) {
                fromServer to "firebase"
            } else {
                val snapshot = loadFromSnapshot(context)
                if (snapshot.isNotEmpty()) {
                    snapshot to "snapshot"
                } else {
                    val fromGithub = runCatching { fetchFromGithub() }.getOrNull().orEmpty()
                    if (fromGithub.isNotEmpty()) fromGithub to "github" else loadFromAsset(context) to "asset"
                }
            }

            if (channels.isNotEmpty()) {
                cached = channels
                lastFetchTime = System.currentTimeMillis()
                _approvedChannelsFlow.value = channels
                if (source == "firebase" || source == "github") {
                    saveToSnapshot(context, channels, source)
                }
            }
            channels
        }
    }

    private fun backgroundRefresh(context: Context) {
        repoScope.launch {
            try {
                refresh(context)
            } catch (_: Exception) {
            }
        }
    }

    private fun loadFromSnapshot(context: Context): List<Channel> {
        return runCatching {
            val file = File(context.filesDir, SNAPSHOT_FILE_NAME)
            if (!file.exists()) return emptyList()
            val text = file.readText()
            val metadata = json.decodeFromString<SnapshotMetadata>(text)
            metadata.channels
        }.getOrNull().orEmpty()
    }

    private fun saveToSnapshot(context: Context, channels: List<Channel>, source: String) {
        runCatching {
            val file = File(context.filesDir, SNAPSHOT_FILE_NAME)
            val metadata = SnapshotMetadata(
                timestamp = System.currentTimeMillis(),
                source = source,
                version = 1,
                channels = channels
            )
            val text = json.encodeToString(metadata)
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
