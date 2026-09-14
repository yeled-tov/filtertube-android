package com.filtertube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

    private val http = Http.newBuilder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json".toMediaType()

    // ── אימות ────────────────────────────────────────────────────────────
    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun sapisid(cookies: String): String? =
        Regex("(?:^|;\\s*)(?:SAPISID|__Secure-3PAPISID)=([^;]+)").find(cookies)?.groupValues?.get(1)

    /**
     * כותרת ההזדהות של גוגל, חתומה על **מארח היעד**.
     *
     * ## הבאג שזה תיקן
     * החתימה היא sha1 של "חותמת_זמן SAPISID origin", ו-origin חייב להיות
     * אותו origin שנשלח בכותרת Origin. חתמתי תמיד על www.youtube.com גם
     * כששלחתי ל-music.youtube.com — והשרת החזיר 400 על כל בקשה למיוזיק.
     * זה מה שהפיל את משיכת "מוזיקה שאהבתי".
     */
    private fun authHeader(cookies: String, origin: String = ORIGIN): String? {
        val sid = sapisid(cookies) ?: return null
        val ts = System.currentTimeMillis() / 1000
        return "SAPISIDHASH ${ts}_${sha1("$ts $sid $origin")}"
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
                    if (!resp.isSuccessful) {
                        // בלי זה כישלון רשת וכישלון פענוח נראים זהים לגמרי:
                        // שניהם "0 התקבלו". הגוף מקוצץ כי גוגל מחזירה הסבר
                        // קצר ומועיל בתחילתו.
                        val why = resp.body?.string().orEmpty().take(160)
                        Diagnostics.log("INNERTUBE $endpoint: HTTP ${resp.code} · $why")
                        return@use null
                    }
                    resp.body?.string()?.let { JSONObject(it) }
                }
            }.getOrElse {
                Diagnostics.log("INNERTUBE $endpoint: נכשל — ${it.message}")
                null
            }
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

    /**
     * הסרטונים שסומנו ב"אהבתי" ביוטיוב.
     *
     * "VLLL" הוא מזהה הדפדוף של פלייליסט ה-Liked videos. הוא זמין דרך אותה
     * הזדהות בעוגיות שכבר משמשת להיסטוריה, ולכן לא נדרש כאן שום OAuth,
     * שום מפתח API ושום הגדרה בצד גוגל.
     */
    suspend fun likedVideos(cookies: String): List<Video> {
        val resp = post("browse", cookies, JSONObject().put("browseId", "VLLL"))
            ?: return emptyList()
        val items = collectVideos(resp)
        if (items.isEmpty()) {
            // תשובה תקינה שממנה לא חולץ כלום היא מקרה אחר לגמרי מבקשה
            // שנכשלה, ובלי ההבחנה הזו שניהם נראים "0".
            Diagnostics.log("INNERTUBE אהבתי: התשובה התקבלה אבל לא חולצו ממנה פריטים")
        }
        return items
    }

    /**
     * הערוצים שהמשתמש מנוי אליהם.
     *
     * מוחזרים כזוגות (מזהה, שם). הפענוח מחפש browseId שמתחיל ב-UC בתוך
     * הצומת של כל פריט — אותה גישה רקורסיבית שמשמשת בשאר הקובץ, ומאותה
     * סיבה: מבנה התשובה של יוטיוב משתנה, מזהה ערוץ לא.
     */
    suspend fun subscriptions(cookies: String): List<Pair<String, String>> {
        val resp = post("browse", cookies, JSONObject().put("browseId", "FEchannels"))
            ?: return emptyList()
        val out = LinkedHashMap<String, String>()
        // walkAll ולא ה-walk הישן: זה בדיוק מה ששבר את המנויים. ה-walk הישן
        // הפעיל את הקריאה החוזרת רק על רכיבי *סרטון*, וערוץ אינו סרטון —
        // ולכן הלולאה הזו מעולם לא רצה אפילו פעם אחת.
        walkAll(resp) { node ->
            val id = node.optJSONObject("navigationEndpoint")
                ?.optJSONObject("browseEndpoint")?.optString("browseId")
                ?: node.optJSONObject("browseEndpoint")?.optString("browseId")
            if (id.isNullOrBlank() || !id.startsWith("UC") || out.containsKey(id)) return@walkAll
            val name = textOf(node.optJSONObject("title"))
                ?: textOf(node.optJSONObject("displayName"))
                ?: return@walkAll
            out[id] = name
        }
        return out.entries.map { it.key to it.value }
    }

    /**
     * "מוזיקה שאהבתי" מיוטיוב מיוזיק, דרך אותן עוגיות.
     *
     * מסלול חלופי ל-YouTubeMusicApi שעובד עם access token. שניהם מגיעים
     * לאותו פלייליסט; ההבדל הוא בזהות שמציגים — וזה מה שמאפשר למשוך את
     * המוזיקה גם כשההתחברות עם גוגל לא זמינה.
     */
    suspend fun likedMusic(cookies: String): List<Video> = withContext(Dispatchers.IO) {
        val musicOrigin = "https://music.youtube.com"
        val auth = authHeader(cookies, musicOrigin) ?: return@withContext emptyList()
        val body = JSONObject().apply {
            put("browseId", "FEmusic_liked_videos")
            put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240103.01.00")
                        put("hl", "he")
                        put("gl", "IL")
                    },
                ),
            )
        }
        val request = Request.Builder()
            .url("https://music.youtube.com/youtubei/v1/browse?prettyPrint=false")
            .header("Content-Type", "application/json")
            .header("User-Agent", USER_AGENT)
            .header("Cookie", cookies)
            .header("Authorization", auth)
            .header("X-Goog-AuthUser", "0")
            .header("Origin", musicOrigin)
            .header("Referer", "$musicOrigin/")
            .header("X-Origin", musicOrigin)
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        runCatching {
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val why = resp.body?.string().orEmpty().take(160)
                    Diagnostics.log("YT MUSIC (עוגיות): HTTP ${resp.code} · $why")
                    return@use emptyList()
                }
                resp.body?.string()?.let { collectVideos(JSONObject(it)) }.orEmpty()
            }
        }.getOrElse {
            Diagnostics.log("YT MUSIC (עוגיות): נכשל — ${it.message}")
            emptyList()
        }
    }


    /**
     * מזהה הערוץ ה**אמיתי** שהעלה את הסרטון, מתוך videoDetails.
     *
     * ## למה זה נדרש
     * ביוטיוב מיוזיק הטקסט שמתחת לשם השיר הוא ה*אמן*, ולא מי שהעלה: הקישור
     * שם מצביע על ערוץ האמן האוטומטי ("Topic") או על ישות אמן שאין לה בכלל
     * ערוץ. לכן הסינון מול הרשימה הלבנה נכשל על כל שיר — 25 הגיעו, 0 אושרו:
     * הערוצים המאושרים הם המעלים, וההשוואה נעשתה מול האמן.
     *
     * הקריאה הזאת אינה דורשת הזדהות, ולכן היא גם לא תלויה בעוגיות שפג תוקפן.
     */
    suspend fun ownerChannel(videoId: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            put("context", JSONObject().put("client", JSONObject().apply {
                put("clientName", CLIENT_NAME)
                put("clientVersion", CLIENT_VERSION)
                put("hl", "he")
                put("gl", "IL")
            }))
        }
        val req = Request.Builder()
            .url("${BASE}player?prettyPrint=false")
            .header("Content-Type", "application/json")
            .header("User-Agent", USER_AGENT)
            .post(body.toString().toRequestBody(jsonMedia))
            .build()
        runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val details = resp.body?.string()
                    ?.let(::JSONObject)?.optJSONObject("videoDetails") ?: return@use null
                val cid = details.optString("channelId")
                if (cid.isBlank()) null else cid to details.optString("author")
            }
        }.getOrNull()
    }

    /**
     * משלים מזהה ערוץ אמיתי לפריטים ש[needsOwner] מסמן — בזה אחר זה, עד
     * שישה במקביל, כדי לא להציף את יוטיוב בסנכרון של רשימה ארוכה.
     *
     * פריט שלא הצלחנו לזהות חוזר כמו שהוא, וממילא ייפסל בהמשך: "לא ידוע"
     * אינו "מותר" באפליקציית רשימה לבנה.
     */
    suspend fun fillOwners(items: List<Video>, needsOwner: (Video) -> Boolean): List<Video> =
        coroutineScope {
            val gate = Semaphore(6)
            items.map { video ->
                async {
                    if (!needsOwner(video)) return@async video
                    val owner = gate.withPermit { ownerChannel(video.id) } ?: return@async video
                    video.copy(
                        channelId = owner.first,
                        channelName = video.channelName.ifBlank { owner.second },
                    )
                }
            }.map { it.await() }
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
    /**
     * פענוח משותף של תשובת InnerTube.
     *
     * חשוף כדי ש-[InnerTubeOAuth] יפענח בדיוק אותו דבר: התשובה זהה, רק
     * ההזדהות שונה. פענוח כפול היה נשבר בנפרד בכל עדכון של יוטיוב.
     */
    fun parseVideos(root: JSONObject): List<Video> = collectVideos(root)

    /**
     * מחלץ סרטונים מכל תשובה של InnerTube.
     *
     * ## למה זה לא מחפש שמות של רכיבים
     * הגרסה הקודמת חיפשה ארבעה שמות קבועים — videoRenderer, compactVideoRenderer,
     * gridVideoRenderer, playlistVideoRenderer. זה עבד עד שיוטיוב שינתה פריסות,
     * ואז שלושה דברים נשברו בבת אחת בלי שאף בקשה נכשלה:
     *
     *   "אהבתי"  → עבר ל-lockupViewModel (הפריסה החדשה של פלייליסטים)
     *   מיוזיק   → משתמש ב-musicResponsiveListItemRenderer מלכתחילה
     *   מנויים   → ערוצים אינם סרטונים, ולכן ה-walk בכלל לא קרא להם
     *
     * היומן אמר "התשובה התקבלה אבל לא חולצו ממנה פריטים" — כלומר ההזדהות
     * והרשת תקינות לגמרי, רק הקריאה לא ידעה לזהות את הצורה.
     *
     * לכן כאן לא בודקים שמות. עוברים על **כל** צומת בתשובה, ומי שאפשר לחלץ
     * ממנו גם מזהה סרטון וגם כותרת — הוא סרטון. שינוי פריסה עתידי לא ישבור
     * את זה, כי מזהה סרטון וכותרת הם מה שלא משתנה.
     */
    private fun collectVideos(root: JSONObject): List<Video> {
        val out = LinkedHashMap<String, Video>()
        walkAll(root) { node ->
            val id = videoIdIn(node) ?: return@walkAll
            if (out.containsKey(id)) return@walkAll
            val title = titleIn(node) ?: return@walkAll
            // Shorts נשארים בטאב שלהם ולא מתערבבים בספרייה.
            if (node.optString("navigationEndpoint").contains("shorts", ignoreCase = true)) {
                return@walkAll
            }
            out[id] = Video(
                id = id,
                title = title,
                channelName = channelNameIn(node).orEmpty(),
                channelId = bylineChannelId(node).orEmpty(),
                thumbnailUrl = "https://i.ytimg.com/vi/$id/hqdefault.jpg",
                publishedAt = System.currentTimeMillis(),
            )
        }
        return out.values.toList()
    }

    /** מזהה הסרטון, בכל אחת מהצורות שיוטיוב משתמשת בהן. */
    private fun videoIdIn(node: JSONObject): String? {
        node.optString("videoId").takeIf { it.isNotBlank() }?.let { return it }
        node.optJSONObject("playlistItemData")?.optString("videoId")
            ?.takeIf { it.isNotBlank() }?.let { return it }
        // הפריסה החדשה: contentId עם סוג תוכן מפורש. בלי בדיקת הסוג היינו
        // אוספים גם פלייליסטים וערוצים כאילו היו סרטונים.
        val contentId = node.optString("contentId")
        if (contentId.isNotBlank() &&
            node.optString("contentType").contains("VIDEO", ignoreCase = true)
        ) return contentId
        return null
    }

    /** הכותרת — טקסט רגיל, פריסה חדשה, או עמודה ראשונה ברשימת מיוזיק. */
    private fun titleIn(node: JSONObject): String? {
        textOf(node.optJSONObject("title"))?.let { return it }
        textOf(node.optJSONObject("headline"))?.let { return it }
        node.optJSONObject("metadata")
            ?.optJSONObject("lockupMetadataViewModel")
            ?.optJSONObject("title")?.optString("content")
            ?.takeIf { it.isNotBlank() }?.let { return it }
        flexColumnText(node, 0)?.let { return it }
        return null
    }

    private fun channelNameIn(node: JSONObject): String? =
        textOf(node.optJSONObject("ownerText"))
            ?: textOf(node.optJSONObject("longBylineText"))
            ?: textOf(node.optJSONObject("shortBylineText"))
            ?: flexColumnText(node, 1)

    /** עמודה מתוך musicResponsiveListItemRenderer של יוטיוב מיוזיק. */
    private fun flexColumnText(node: JSONObject, index: Int): String? {
        val columns = node.optJSONArray("flexColumns") ?: return null
        val runs = columns.optJSONObject(index)
            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
            ?.optJSONObject("text")?.optJSONArray("runs") ?: return null
        val sb = StringBuilder()
        for (i in 0 until runs.length()) sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
        return sb.toString().takeIf { it.isNotBlank() }
    }

    private fun textOf(obj: JSONObject?): String? {
        if (obj == null) return null
        obj.optString("simpleText").takeIf { it.isNotEmpty() }?.let { return it }
        val runs = obj.optJSONArray("runs") ?: return null
        return (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text") ?: "" }
            .takeIf { it.isNotEmpty() }
    }

    /**
     * מזהה הערוץ של פריט.
     *
     * ## למה יש כאן שלב שני
     * שלושת השדות המוכרים (ownerText וחבריו) קיימים ברשימות רגילות, אבל
     * **לא** ברשימות פלייליסט: playlistVideoRenderer מציג את שם היוצר
     * במבנה אחר. התוצאה הייתה שכל הלייקים נמשכו בלי מזהה ערוץ, ואז נפסלו
     * בסינון לרשימה הלבנה — כלומר ההתחברות "עבדה" ולא הוסיפה כלום.
     *
     * הנפילה לחיפוש רקורסיבי בתוך הפריט פותרת את זה בלי להיות תלויה במבנה
     * מסוים, בדיוק כמו בשאר הקובץ.
     */
    private fun bylineChannelId(vr: JSONObject): String? {
        for (key in listOf("ownerText", "longBylineText", "shortBylineText")) {
            val runs = vr.optJSONObject(key)?.optJSONArray("runs") ?: continue
            for (i in 0 until runs.length()) {
                val id = runs.optJSONObject(i)?.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("browseEndpoint")?.optString("browseId")
                if (!id.isNullOrEmpty() && id.startsWith("UC")) return id
            }
        }
        var found: String? = null
        walkAll(vr) { node ->
            if (found != null) return@walkAll
            val id = node.optJSONObject("browseEndpoint")?.optString("browseId")
            if (!id.isNullOrEmpty() && id.startsWith("UC")) found = id
        }
        return found
    }

    /** מעבר רקורסיבי על כל צומת, בלי לחפש מפתח מסוים. */
    private fun walkAll(node: Any?, visit: (JSONObject) -> Unit) {
        when (node) {
            is JSONObject -> {
                visit(node)
                val keys = node.keys()
                while (keys.hasNext()) walkAll(node.opt(keys.next()), visit)
            }
            is JSONArray -> for (i in 0 until node.length()) walkAll(node.opt(i), visit)
        }
    }
}
