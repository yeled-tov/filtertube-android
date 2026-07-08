package com.filtertube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    // לקוח IOS משתמש ב-host ומפתח API ייעודיים — שליחה ל-www.youtube.com מחזירה HTTP 400
    private const val IOS_BASE = "https://youtubei.googleapis.com/youtubei/v1/"
    private const val IOS_KEY = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc"
    private const val ORIGIN = "https://www.youtube.com"
    private const val CLIENT_NAME = "WEB"
    private const val CLIENT_VERSION = "2.20241125.01.00"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
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

    // ── נגן מהיר (כתובות ישירות, בלי פענוח חתימות, ללא התחברות) ──
    // מנסים לקוח IOS, ואם נכשל/חסום — ANDROID_VR (שניהם בד"כ מחזירים כתובות ישירות
    // ללא PoToken). אם שניהם נכשלים מחזירים null ו-StreamRepository נופל ל-NewPipe.
    suspend fun player(videoId: String): StreamData? = coroutineScope {
        // מריצים את שני הלקוחות במקביל (מרוץ) במקום בטור — חוסך עד ~15ש' בטעינת סרטון.
        // מעדיפים ANDROID_VR: הזרמים שלו יציבים יותר ולרוב לא נחנקים/נחתכים אחרי כמה
        // שניות (בניגוד ל-IOS שהחל להיחנק). IOS נשאר כגיבוי אם VR נכשל.
        val vr = async { playerWithClient(videoId, vrClient(), VR_UA) }
        val ios = async { playerWithClient(videoId, iosClient(), IOS_UA) }
        val first = vr.await()
        if (first != null) {
            ios.cancel(); Diagnostics.log("InnerTube: ANDROID_VR ניצח"); first
        } else {
            val i = ios.await()
            Diagnostics.log("InnerTube: ${if (i != null) "IOS ניצח (VR נכשל)" else "VR+IOS נכשלו → NewPipe"}")
            i
        }
    }

    // ערכי ברירת מחדל מוטמעים — נעשה בהם שימוש אם RemoteConfig (הענן) לא נטען.
    private const val DEF_IOS_UA = "com.google.ios.youtube/20.50.3 (iPhone16,2; U; CPU iOS 18_1 like Mac OS X)"
    private const val DEF_VR_UA = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12; GB) gzip"
    private const val DEF_IOS_VER = "20.50.3"
    private const val DEF_IOS_MODEL = "iPhone16,2"
    private const val DEF_IOS_OS = "17.5.1.21F90"
    private const val DEF_VR_VER = "1.60.19"

    // ה-UA בפועל — מהענן אם קיים, אחרת ברירת המחדל. עדכון יוטיוב = עריכת JSON ב-GitHub.
    private val IOS_UA get() = RemoteConfig.iosUserAgent(DEF_IOS_UA)
    private val VR_UA get() = RemoteConfig.vrUserAgent(DEF_VR_UA)

    private fun iosClient(): JSONObject = JSONObject().apply {
        put("clientName", "IOS")
        put("clientVersion", RemoteConfig.iosVersion(DEF_IOS_VER))
        put("deviceMake", "Apple")
        put("deviceModel", RemoteConfig.iosDeviceModel(DEF_IOS_MODEL))
        put("osName", "iPhone")
        put("osVersion", RemoteConfig.iosOsVersion(DEF_IOS_OS))
        put("userAgent", RemoteConfig.iosUserAgent(DEF_IOS_UA))   // נדרש ב-context של iOS
        put("timeZone", "UTC")
        put("utcOffsetMinutes", 0)
        put("hl", "he"); put("gl", "IL")
    }

    private fun vrClient(): JSONObject = JSONObject().apply {
        put("clientName", "ANDROID_VR")
        put("clientVersion", RemoteConfig.vrVersion(DEF_VR_VER))
        put("deviceMake", "Oculus")
        put("deviceModel", "Quest 3")
        put("osName", "Android")
        put("osVersion", "12")
        put("androidSdkVersion", 32)
        put("hl", "he"); put("gl", "IL")
    }

    private suspend fun playerWithClient(videoId: String, client: JSONObject, userAgent: String): StreamData? = withContext(Dispatchers.IO) {
        val cname = client.optString("clientName")
        val body = JSONObject().apply {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            put("context", JSONObject().put("client", client))
        }
        // IOS ל-host+key הייעודיים; שאר הלקוחות ל-youtubei הרגיל של www.youtube.com
        val url = if (cname == "IOS")
            "${IOS_BASE}player?key=$IOS_KEY&prettyPrint=false"
        else
            "${BASE}player?prettyPrint=false"
        val req = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("User-Agent", userAgent)
            .header("X-Goog-Api-Format-Version", "2")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        val json = runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) { Diagnostics.log("InnerTube $cname: HTTP ${resp.code}"); null }
                else resp.body?.string()?.let(::JSONObject)
            }
        }.getOrNull() ?: return@withContext null

        val status = json.optJSONObject("playabilityStatus")?.optString("status")
        if (status != "OK") { Diagnostics.log("InnerTube $cname: status=$status"); return@withContext null }
        val sd = json.optJSONObject("streamingData")
            ?: run { Diagnostics.log("InnerTube $cname: אין streamingData"); return@withContext null }

        val muxedTracks = mutableListOf<StreamTrack>()
        val videoOnly = mutableListOf<Pair<Int, String>>()
        var bestAudioUrl: String? = null
        var bestAudioBitrate = -1

        fun handle(f: JSONObject, adaptive: Boolean) {
            val url = f.optString("url")
            if (url.isEmpty()) return            // ciphered (אין url ישיר) — ניפול ל-NewPipe
            val mime = f.optString("mimeType")
            when {
                mime.startsWith("audio/") -> {
                    val br = f.optInt("bitrate")
                    if (br > bestAudioBitrate) { bestAudioBitrate = br; bestAudioUrl = url }
                }
                mime.startsWith("video/") -> {
                    val h = f.optInt("height")
                    if (h > 0) {
                        if (adaptive) videoOnly.add(h to url)
                        else muxedTracks.add(StreamTrack(h, "${h}p", url, null))
                    }
                }
            }
        }
        sd.optJSONArray("formats")?.let { for (i in 0 until it.length()) it.optJSONObject(i)?.let { f -> handle(f, false) } }
        sd.optJSONArray("adaptiveFormats")?.let { for (i in 0 until it.length()) it.optJSONObject(i)?.let { f -> handle(f, true) } }

        val au = bestAudioUrl
        val dashTracks = if (au != null) videoOnly.map { StreamTrack(it.first, "${it.first}p", it.second, au) } else emptyList()
        val vodTracks = (muxedTracks + dashTracks).distinctBy { it.height }.sortedByDescending { it.height }

        val vd = json.optJSONObject("videoDetails")
        val isLive = vd?.optBoolean("isLive") == true || vd?.optBoolean("isLiveContent") == true
        val hls = sd.optString("hlsManifestUrl")
        // שידור חי: אין זרמים מתקדמים — מנגנים את ה-HLS manifest (m3u8) ישירות
        val tracks = if (vodTracks.isEmpty() && hls.isNotEmpty())
            listOf(StreamTrack(0, "שידור חי", hls, null)) else vodTracks
        if (tracks.isEmpty()) { Diagnostics.log("InnerTube $cname: כתובות מוצפנות (אין URL ישיר)"); return@withContext null }

        val bestMuxed = if (isLive && hls.isNotEmpty()) hls
            else muxedTracks.maxByOrNull { it.height }?.videoUrl ?: tracks.first().videoUrl
        // הסרטונים הקשורים נטענים בנפרד ב-Playback אחרי שהניגון כבר התחיל — לא כאן.
        // קריאת רשת ל-related כאן הייתה מעכבת את הופעת הסרטון על המסך ב-round-trip שלם.
        val related = emptyList<Video>()

        StreamData(
            title = vd?.optString("title") ?: "",
            uploaderName = vd?.optString("author") ?: "",
            channelId = vd?.optString("channelId") ?: "",
            durationSec = vd?.optString("lengthSeconds")?.toLongOrNull() ?: 0L,
            viewCount = vd?.optString("viewCount")?.toLongOrNull() ?: 0L,
            description = vd?.optString("shortDescription"),
            thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            tracks = tracks,
            bestAudioUrl = if (isLive) null else au,
            bestVideoUrl = bestMuxed,
            related = related,
            // חובה לנגן את הזרם ב-UA של אותו לקוח (IOS/VR) שביקש אותו, אחרת ה-CDN חותך.
            streamUserAgent = userAgent,
        )
    }

    /** סרטונים קשורים (לתור הרדיו) — אנונימי, לקוח WEB. */
    suspend fun related(videoId: String): List<Video> = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("videoId", videoId)
            put("context", context())
        }
        val req = Request.Builder()
            .url("${BASE}next?prettyPrint=false")
            .header("Content-Type", "application/json")
            .header("User-Agent", USER_AGENT)
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        val json = runCatching {
            http.newCall(req).execute().use { if (!it.isSuccessful) null else it.body?.string()?.let(::JSONObject) }
        }.getOrNull() ?: return@withContext emptyList()
        collectVideos(json)
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
