package com.filtertube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * לקוח InnerTube — ה-API הפנימי של יוטיוב, מאומת באמצעות ה-cookies של המשתמש
 * (כמו YMusic/Metrolist). מאפשר היסטוריה, המלצות מותאמות אישית ולייקים אמיתיים.
 *
 * הפירוש רקורסיבי (אוסף כל videoRenderer בתשובה) כדי להיות חסין לשינויי מבנה.
 */
object InnerTube {

    private const val BASE = "https://www.youtube.com/youtubei/v1/"
    private const val KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
    private const val ORIGIN = "https://www.youtube.com"
    private const val CLIENT_NAME = "WEB"
    private const val CLIENT_VERSION = "2.20241125.01.00"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json".toMediaType()

    // ── אימות ────────────────────────────────────────────────────────────
    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun sapisid(cookies: String): String? =
        Regex("(?:^|;\\s*)(?:SAPISID|__Secure-3PAPISID)=([^;]+)").find(cookies)?.groupValues?.get(1)

    private fun authHeader(cookies: String): String? {
        val sid = sapisid(cookies) ?: return null
        val ts = System.currentTimeMillis() / 1000
        return "SAPISIDHASH ${ts}_${sha1("$ts $sid $ORIGIN")}"
    }

    private fun context(): JSONObject = JSONObject().apply {
        put("client", JSONObject().apply {
            put("clientName", CLIENT_NAME)
            put("clientVersion", CLIENT_VERSION)
            put("hl", "he")
            put("gl", "IL")
        })
    }

    /** POST מאומת לנקודת קצה של InnerTube. */
    private suspend fun post(endpoint: String, cookies: String, body: JSONObject): JSONObject? =
        withContext(Dispatchers.IO) {
            body.put("context", context())
            val auth = authHeader(cookies) ?: return@withContext null
            val builder = Request.Builder()
                .url("$BASE$endpoint?key=$KEY&prettyPrint=false")
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .header("Cookie", cookies)
                .header("Authorization", auth)
                .header("X-Goog-AuthUser", "0")
                .header("Origin", ORIGIN)
                .header("X-Origin", ORIGIN)
                .post(body.toString().toRequestBody(jsonMedia))
            runCatching {
                http.newCall(builder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    resp.body?.string()?.let { JSONObject(it) }
                }
            }.getOrNull()
        }

    // ── תכונות ───────────────────────────────────────────────────────────
    /** היסטוריית הצפייה של המשתמש. */
    suspend fun history(cookies: String): List<Video> {
        val resp = post("browse", cookies, JSONObject().put("browseId", "FEhistory")) ?: return emptyList()
        return collectVideos(resp)
    }

    /** המלצות הבית המותאמות אישית (לפי מה שצפית/אהבת). */
    suspend fun recommendations(cookies: String): List<Video> {
        val resp = post("browse", cookies, JSONObject().put("browseId", "FEwhat_to_watch")) ?: return emptyList()
        return collectVideos(resp)
    }

    /** סימון/ביטול לייק אמיתי דרך InnerTube. */
    suspend fun rate(cookies: String, videoId: String, like: Boolean): Boolean {
        val endpoint = if (like) "like/like" else "like/removelike"
        val resp = post(endpoint, cookies, JSONObject().put("target", JSONObject().put("videoId", videoId)))
        return resp != null
    }

    // ── פירוש רקורסיבי ───────────────────────────────────────────────────
    private fun collectVideos(root: JSONObject): List<Video> {
        val out = LinkedHashMap<String, Video>()
        walk(root) { vr ->
            val id = vr.optString("videoId")
            if (id.isNullOrEmpty() || out.containsKey(id)) return@walk
            val title = textOf(vr.optJSONObject("title")) ?: return@walk
            val channel = textOf(vr.optJSONObject("ownerText"))
                ?: textOf(vr.optJSONObject("longBylineText"))
                ?: textOf(vr.optJSONObject("shortBylineText")) ?: ""
            val channelId = bylineChannelId(vr) ?: ""
            val thumb = "https://i.ytimg.com/vi/$id/hqdefault.jpg"
            out[id] = Video(id, title, channel, channelId, thumb, System.currentTimeMillis())
        }
        return out.values.toList()
    }

    /** עובר רקורסיבית ומפעיל [onVideo] על כל videoRenderer / compactVideoRenderer / gridVideoRenderer. */
    private fun walk(node: Any?, onVideo: (JSONObject) -> Unit) {
        when (node) {
            is JSONObject -> {
                for (key in listOf("videoRenderer", "compactVideoRenderer", "gridVideoRenderer", "playlistVideoRenderer")) {
                    node.optJSONObject(key)?.let(onVideo)
                }
                val keys = node.keys()
                while (keys.hasNext()) walk(node.opt(keys.next()), onVideo)
            }
            is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), onVideo)
        }
    }

    private fun textOf(obj: JSONObject?): String? {
        if (obj == null) return null
        obj.optString("simpleText").takeIf { it.isNotEmpty() }?.let { return it }
        val runs = obj.optJSONArray("runs") ?: return null
        return (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text") ?: "" }
            .takeIf { it.isNotEmpty() }
    }

    private fun bylineChannelId(vr: JSONObject): String? {
        for (key in listOf("ownerText", "longBylineText", "shortBylineText")) {
            val runs = vr.optJSONObject(key)?.optJSONArray("runs") ?: continue
            for (i in 0 until runs.length()) {
                val id = runs.optJSONObject(i)?.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")?.optString("browseId")
                if (!id.isNullOrEmpty() && id.startsWith("UC")) return id
            }
        }
        return null
    }
}
