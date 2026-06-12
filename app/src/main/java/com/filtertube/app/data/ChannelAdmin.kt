package com.filtertube.app.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import java.util.concurrent.TimeUnit

/**
 * ניהול רשימת הערוצים דרך GitHub API — מוסיף/מסיר ערוצים ע"י עריכת channels.json ב-repo.
 * שינויים מתפרסמים אוטומטית לכל המשתמשים (האפליקציה קוראת מ-GitHub raw).
 */
object ChannelAdmin {

    private const val OWNER = "yeled-tov"
    private const val REPO = "filtertube-android"
    private const val PATH = "channels.json"
    private const val API = "https://api.github.com/repos/$OWNER/$REPO/contents/$PATH"

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * מזהה ערוץ YouTube מקישור/handle. מחזיר (channelId, name) או null.
     */
    suspend fun resolveChannel(input: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        val url = normalizeChannelUrl(input.trim())
        try {
            val info = ChannelInfo.getInfo(ServiceList.YouTube, url)
            val channelId = Regex("/channel/(UC[\\w-]+)").find(info.url)?.groupValues?.get(1)
                ?: Regex("(UC[\\w-]{20,})").find(info.id ?: "")?.groupValues?.get(1)
                ?: return@withContext null
            channelId to (info.name ?: channelId)
        } catch (e: Exception) {
            null
        }
    }

    private fun normalizeChannelUrl(input: String): String = when {
        input.startsWith("http") -> input
        input.startsWith("UC") && input.length >= 20 -> "https://www.youtube.com/channel/$input"
        input.startsWith("@") -> "https://www.youtube.com/$input"
        else -> "https://www.youtube.com/@$input"
    }

    /**
     * מושך את הקובץ הנוכחי מ-GitHub: מחזיר (channels, sha).
     */
    suspend fun fetchCurrent(token: String): Pair<MutableList<Channel>, String> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(API)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw RuntimeException("GitHub ${resp.code}: ${resp.message}")
            val body = resp.body?.string() ?: throw RuntimeException("empty")
            val obj = JSONObject(body)
            val sha = obj.getString("sha")
            val contentB64 = obj.getString("content").replace("\n", "")
            val decoded = String(Base64.decode(contentB64, Base64.DEFAULT))
            val channels = json.decodeFromString<List<Channel>>(decoded).toMutableList()
            channels to sha
        }
    }

    /**
     * דוחף רשימה מעודכנת ל-GitHub.
     */
    suspend fun commit(token: String, channels: List<Channel>, sha: String, message: String): Boolean =
        withContext(Dispatchers.IO) {
            val newContent = json.encodeToString(channels)
            val b64 = Base64.encodeToString(newContent.toByteArray(), Base64.NO_WRAP)
            val payload = JSONObject().apply {
                put("message", message)
                put("content", b64)
                put("sha", sha)
            }.toString()
            val req = Request.Builder()
                .url(API)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .put(payload.toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    android.util.Log.w("ChannelAdmin", "commit failed ${resp.code}: ${resp.body?.string()}")
                }
                resp.isSuccessful
            }
        }
}
