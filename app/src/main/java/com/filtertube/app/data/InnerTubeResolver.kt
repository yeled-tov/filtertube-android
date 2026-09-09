package com.filtertube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class InnerTubeClientType {
    ANDROID_VR,
    IOS,

    /**
     * נגן מוטמע של ממשק הטלוויזיה. הוא לא דורש PO token והוא המנוע שנוטה
     * להחזיק כשהאחרים מקבלים LOGIN_REQUIRED, ולכן הוא הגיבוי הטוב ביותר
     * ל-IOS. דורש thirdParty.embedUrl בבקשה.
     */
    TVHTML5_EMBED,

    /** יוטיוב לנייד בדפדפן. ההתנהגות הכי קרובה לגלישה רגילה. */
    MWEB,
}

/**
 * מנוע פתרון זרמי InnerTube עם אימות תגובה, ניטור בריאות (Health Monitor) ותמיכה ב-RemoteConfig.
 */
class InnerTubeResolver(
    private val clientType: InnerTubeClientType
) : StreamResolver {

    val clientKey: String = when (clientType) {
        InnerTubeClientType.IOS -> "IOS"
        InnerTubeClientType.ANDROID_VR -> "ANDROID_VR"
        InnerTubeClientType.TVHTML5_EMBED -> "TVHTML5_EMBED"
        InnerTubeClientType.MWEB -> "MWEB"
    }

    override val name: String = "InnerTube $clientKey"

    companion object {
        private const val BASE = "https://www.youtube.com/youtubei/v1/"
        private const val IOS_BASE = "https://youtubei.googleapis.com/youtubei/v1/"
        private const val IOS_KEY = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc"

        // embedded defaults
        private const val DEF_IOS_VER = "20.50.3"
        private const val DEF_IOS_UA = "com.google.ios.youtube/20.50.3 (iPhone16,2; U; CPU iOS 18_1 like Mac OS X)"
        private const val DEF_IOS_MODEL = "iPhone16,2"
        private const val DEF_IOS_OS = "18.1"

        private const val DEF_VR_VER = "1.60.19"
        private const val DEF_VR_UA = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12; GB) gzip"

        private const val DEF_TV_VER = "2.0"
        private const val DEF_TV_UA =
            "Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Safari/605.1.15"

        private const val DEF_MWEB_VER = "2.20260901.00.00"
        private const val DEF_MWEB_UA =
            "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"

        /**
         * visitorData לכל סוג לקוח בנפרד.
         *
         * קודם לכן זה היה שדה סטטי אחד משותף ל-IOS ול-ANDROID_VR. מכיוון
         * שהמנועים רצים במקביל, אסימון שנוצר עבור לקוח אחד נשלח בבקשה של
         * הלקוח השני — ויוטיוב דוחה visitorData שלא תואם לזהות הלקוח
         * ב-HTTP 400. זו הסיבה שמנוע IOS נכשל ב-400 באופן עקבי על המכשיר,
         * בעוד שאותה בקשה בדיוק בלי האסימון מוחזרת תקינה.
         */
        private val visitorDataByClient = java.util.concurrent.ConcurrentHashMap<String, String>()

        @Volatile
        var poToken: String? = null

        private val jsonMedia = "application/json".toMediaType()

        private val http = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()
    }

    private fun getIosVersion(): String {
        return RemoteConfig.iosVersion(DEF_IOS_VER)
    }

    private fun getIosUserAgent(): String {
        return RemoteConfig.iosUserAgent(DEF_IOS_UA)
    }

    private fun getVrVersion(): String {
        return RemoteConfig.vrVersion(DEF_VR_VER)
    }

    private fun getVrUserAgent(): String {
        return RemoteConfig.vrUserAgent(DEF_VR_UA)
    }

    private fun buildClientConfig(): Pair<JSONObject, String> {
        val client = JSONObject()
        val ua: String
        when (clientType) {
            InnerTubeClientType.IOS -> {
                val ver = getIosVersion()
                ua = getIosUserAgent()
                client.apply {
                    put("clientName", "IOS")
                    put("clientVersion", ver)
                    put("deviceMake", "Apple")
                    put("deviceModel", RemoteConfig.iosDeviceModel(DEF_IOS_MODEL))
                    put("osName", "iPhone")
                    put("osVersion", RemoteConfig.iosOsVersion(DEF_IOS_OS))
                    put("userAgent", ua)
                    put("timeZone", "UTC")
                    put("utcOffsetMinutes", 0)
                    put("hl", "he")
                    put("gl", "IL")
                }
            }
            InnerTubeClientType.ANDROID_VR -> {
                val ver = getVrVersion()
                ua = getVrUserAgent()
                client.apply {
                    put("clientName", "ANDROID_VR")
                    put("clientVersion", ver)
                    put("deviceMake", "Oculus")
                    put("deviceModel", "Quest 3")
                    put("osName", "Android")
                    put("osVersion", "12")
                    put("androidSdkVersion", 32)
                    put("hl", "he")
                    put("gl", "IL")
                }
            }
            InnerTubeClientType.TVHTML5_EMBED -> {
                ua = RemoteConfig.clientUserAgent("tv", DEF_TV_UA)
                client.apply {
                    put("clientName", "TVHTML5_SIMPLY_EMBEDDED_PLAYER")
                    put("clientVersion", RemoteConfig.clientVersion("tv", DEF_TV_VER))
                    put("hl", "he")
                    put("gl", "IL")
                }
            }
            InnerTubeClientType.MWEB -> {
                ua = RemoteConfig.clientUserAgent("mweb", DEF_MWEB_UA)
                client.apply {
                    put("clientName", "MWEB")
                    put("clientVersion", RemoteConfig.clientVersion("mweb", DEF_MWEB_VER))
                    put("hl", "he")
                    put("gl", "IL")
                }
            }
        }
        visitorDataByClient[clientKey]?.takeIf { it.isNotBlank() }?.let { client.put("visitorData", it) }
        return client to ua
    }

    override suspend fun resolve(videoId: String): StreamData? = withContext(Dispatchers.IO) {
        if (!RemoteConfig.isResolverEnabled(clientKey, default = true)) {
            Diagnostics.log("$name $videoId: מנוע מבוטל ב-RemoteConfig")
            return@withContext null
        }

        if (!ResolverHealthMonitor.isAvailable(clientKey)) {
            Diagnostics.log("$name $videoId: מנוע ב-cooldown (נכשל לאחרונה)")
            return@withContext null
        }

        val t0 = System.currentTimeMillis()
        val (clientObj, userAgent) = buildClientConfig()
        val cname = clientObj.optString("clientName")

        val body = JSONObject().apply {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            put("context", JSONObject().put("client", clientObj))
            // נגן מוטמע חייב להצהיר מאיזה דף הוא מוטמע. בלי זה יוטיוב מחזיר
            // status=ERROR עבור TVHTML5_SIMPLY_EMBEDDED_PLAYER.
            if (clientType == InnerTubeClientType.TVHTML5_EMBED) {
                put(
                    "context",
                    JSONObject()
                        .put("client", clientObj)
                        .put(
                            "thirdParty",
                            JSONObject().put("embedUrl", "https://www.youtube.com/watch?v=$videoId"),
                        ),
                )
            }
            // המיקום הנכון הוא serviceIntegrityDimensions ברמה העליונה.
            // כשדה לא מוכר בתוך context.user, הוא גורם ל-HTTP 400 בגלל
            // הכותרת X-Goog-Api-Format-Version: 2.
            poToken?.takeIf { it.isNotBlank() }?.let { token ->
                put("serviceIntegrityDimensions", JSONObject().put("poToken", token))
            }
        }

        val endpointUrl = if (cname == "IOS")
            "${IOS_BASE}player?key=$IOS_KEY&prettyPrint=false"
        else
            "${BASE}player?prettyPrint=false"

        val req = Request.Builder()
            .url(endpointUrl)
            .header("Content-Type", "application/json")
            .header("User-Agent", userAgent)
            .header("X-Goog-Api-Format-Version", "2")
            .post(body.toString().toRequestBody(jsonMedia))
            .build()

        var httpCode = -1
        val json = runCatching {
            http.newCall(req).execute().use { resp ->
                httpCode = resp.code
                if (!resp.isSuccessful) null
                else resp.body?.string()?.let(::JSONObject)
            }
        }.getOrNull()

        val elapsedMs = System.currentTimeMillis() - t0

        if (json == null) {
            val reason = "HTTP $httpCode"
            Diagnostics.log("$name $videoId: $reason FAILED (${elapsedMs}ms)")
            ResolverHealthMonitor.recordFailure(clientKey, reason)
            return@withContext null
        }

        json.optJSONObject("responseContext")?.optString("visitorData")?.takeIf { it.isNotBlank() }?.let {
            visitorDataByClient[clientKey] = it
        }

        val playability = json.optJSONObject("playabilityStatus")
        val status = playability?.optString("status")
        val reason = playability?.optString("reason") ?: playability?.optString("errorScreen")
        if (status != "OK") {
            val failMsg = "status=$status reason=${reason ?: "none"}"
            Diagnostics.log("$name $videoId: $failMsg FAILED (${elapsedMs}ms)")
            ResolverHealthMonitor.recordFailure(clientKey, status ?: "NOT_OK")
            return@withContext null
        }

        val sd = json.optJSONObject("streamingData")
        if (sd == null) {
            Diagnostics.log("$name $videoId: missing streamingData FAILED (${elapsedMs}ms)")
            ResolverHealthMonitor.recordFailure(clientKey, "missing streamingData")
            return@withContext null
        }

        val muxedTracks = mutableListOf<StreamTrack>()
        val videoOnly = mutableListOf<Pair<Int, String>>()
        var bestAudioUrl: String? = null
        var bestAudioBitrate = -1

        fun processFormat(f: JSONObject, adaptive: Boolean) {
            val url = f.optString("url")
            if (url.isEmpty()) return
            val mime = f.optString("mimeType")
            when {
                mime.startsWith("audio/") -> {
                    val br = f.optInt("bitrate")
                    if (br > bestAudioBitrate) {
                        bestAudioBitrate = br
                        bestAudioUrl = url
                    }
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

        sd.optJSONArray("formats")?.let { arr ->
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { f -> processFormat(f, false) }
        }
        sd.optJSONArray("adaptiveFormats")?.let { arr ->
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { f -> processFormat(f, true) }
        }

        val au = bestAudioUrl
        val dashTracks = if (au != null) videoOnly.map { StreamTrack(it.first, "${it.first}p", it.second, au) } else emptyList()
        val vodTracks = (muxedTracks + dashTracks).distinctBy { it.height }.sortedByDescending { it.height }

        val vd = json.optJSONObject("videoDetails")
        val isLive = vd?.optBoolean("isLive") == true || vd?.optBoolean("isLiveContent") == true
        val hls = sd.optString("hlsManifestUrl")

        val tracks = if (vodTracks.isEmpty() && hls.isNotEmpty())
            listOf(StreamTrack(0, "שידור חי", hls, null))
        else vodTracks

        val bestMuxed = if (isLive && hls.isNotEmpty()) hls
        else muxedTracks.maxByOrNull { it.height }?.videoUrl ?: tracks.firstOrNull()?.videoUrl ?: ""

        val streamData = StreamData(
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
            related = emptyList(),
            streamUserAgent = userAgent
        )

        if (!StreamValidator.validateBasic(streamData)) {
            Diagnostics.log("$name $videoId: basic stream validation FAILED (${elapsedMs}ms)")
            ResolverHealthMonitor.recordFailure(clientKey, "invalid StreamData")
            return@withContext null
        }

        ResolverHealthMonitor.recordSuccess(clientKey)
        Diagnostics.log("$name $videoId: status=OK streamingData=OK tracks=${tracks.size} SUCCESS (${elapsedMs}ms)")
        streamData
    }
}
