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
    IOS
}

/**
 * מנוע פתרון זרמי InnerTube עם אימות תגובה, תמיכה ב-visitorData ואימות נגישות זרם חיה (HTTP check).
 */
class InnerTubeResolver(
    private val clientType: InnerTubeClientType
) : StreamResolver {

    override val name: String = "InnerTube ${clientType.name}"

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

        @Volatile
        var visitorData: String? = null

        @Volatile
        var poToken: String? = null

        private val jsonMedia = "application/json".toMediaType()

        private val http = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(6, TimeUnit.SECONDS)
            .build()

        private val streamCheckHttp = OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        fun compareVersions(v1: String, v2: String): Int {
            val p1 = v1.split(".").mapNotNull { it.toIntOrNull() }
            val p2 = v2.split(".").mapNotNull { it.toIntOrNull() }
            val maxLen = maxOf(p1.size, p2.size)
            for (i in 0 until maxLen) {
                val n1 = p1.getOrElse(i) { 0 }
                val n2 = p2.getOrElse(i) { 0 }
                if (n1 != n2) return n1.compareTo(n2)
            }
            return 0
        }
    }

    private fun getIosVersion(): String {
        val rc = RemoteConfig.iosVersion(DEF_IOS_VER)
        return if (compareVersions(rc, DEF_IOS_VER) >= 0) rc else DEF_IOS_VER
    }

    private fun getIosUserAgent(): String {
        val rcVer = RemoteConfig.iosVersion(DEF_IOS_VER)
        val rcUa = RemoteConfig.iosUserAgent(DEF_IOS_UA)
        return if (compareVersions(rcVer, DEF_IOS_VER) >= 0) rcUa else DEF_IOS_UA
    }

    private fun getVrVersion(): String {
        val rc = RemoteConfig.vrVersion(DEF_VR_VER)
        return if (compareVersions(rc, DEF_VR_VER) >= 0) rc else DEF_VR_VER
    }

    private fun getVrUserAgent(): String {
        val rcVer = RemoteConfig.vrVersion(DEF_VR_VER)
        val rcUa = RemoteConfig.vrUserAgent(DEF_VR_UA)
        return if (compareVersions(rcVer, DEF_VR_VER) >= 0) rcUa else DEF_VR_UA
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
        }
        visitorData?.takeIf { it.isNotBlank() }?.let { client.put("visitorData", it) }
        return client to ua
    }

    override suspend fun resolve(videoId: String): StreamData? = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        val (clientObj, userAgent) = buildClientConfig()
        val cname = clientObj.optString("clientName")

        val body = JSONObject().apply {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            val contextObj = JSONObject().put("client", clientObj)
            poToken?.takeIf { it.isNotBlank() }?.let { token ->
                contextObj.put("user", JSONObject().put("poToken", token))
            }
            put("context", contextObj)
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
            Diagnostics.log("$name $videoId: HTTP $httpCode FAILED (${elapsedMs}ms)")
            return@withContext null
        }

        // Save visitorData if returned
        json.optJSONObject("responseContext")?.optString("visitorData")?.takeIf { it.isNotBlank() }?.let {
            visitorData = it
        }

        val playability = json.optJSONObject("playabilityStatus")
        val status = playability?.optString("status")
        val reason = playability?.optString("reason") ?: playability?.optString("errorScreen")
        if (status != "OK") {
            Diagnostics.log("$name $videoId: status=$status reason=$reason FAILED (${elapsedMs}ms)")
            return@withContext null
        }

        val sd = json.optJSONObject("streamingData")
        if (sd == null) {
            Diagnostics.log("$name $videoId: missing streamingData FAILED (${elapsedMs}ms)")
            return@withContext null
        }

        val muxedTracks = mutableListOf<StreamTrack>()
        val videoOnly = mutableListOf<Pair<Int, String>>()
        var bestAudioUrl: String? = null
        var bestAudioBitrate = -1

        fun processFormat(f: JSONObject, adaptive: Boolean) {
            val url = f.optString("url")
            if (url.isEmpty()) return // Ciphered or missing direct URL
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

        if (tracks.isEmpty()) {
            Diagnostics.log("$name $videoId: no direct playable URLs FAILED (${elapsedMs}ms)")
            return@withContext null
        }

        val testUrl = tracks.first().videoUrl
        if (!verifyStreamUrl(testUrl, userAgent)) {
            Diagnostics.log("$name $videoId: stream URL probe 403/failed FAILED (${elapsedMs}ms)")
            return@withContext null
        }

        val bestMuxed = if (isLive && hls.isNotEmpty()) hls
        else muxedTracks.maxByOrNull { it.height }?.videoUrl ?: tracks.first().videoUrl

        Diagnostics.log("$name $videoId: status=OK streamingData=OK tracks=${tracks.size} SUCCESS (${elapsedMs}ms)")

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
            related = emptyList(),
            streamUserAgent = userAgent
        )
    }

    private fun verifyStreamUrl(url: String, userAgent: String): Boolean {
        if (url.isEmpty()) return false
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Range", "bytes=0-1")
                .get()
                .build()
            streamCheckHttp.newCall(req).execute().use { resp ->
                resp.isSuccessful || resp.code == 206
            }
        }.getOrDefault(false)
    }
}
