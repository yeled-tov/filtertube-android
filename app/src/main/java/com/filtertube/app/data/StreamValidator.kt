package com.filtertube.app.data

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * מנגנון אימות מרכזי ל-StreamData, בודק זרמי וידאו ואודיו בנפרד.
 */
object StreamValidator {

    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    sealed class ValidationResult {
        object OK : ValidationResult()
        data class VideoError(val code: Int, val message: String) : ValidationResult()
        data class AudioError(val code: Int, val message: String) : ValidationResult()
        data class GeneralError(val message: String) : ValidationResult()
    }

    /**
     * בודק ש-StreamData כולל רשימת איכויות תקינה ולא מוצפנת.
     */
    fun validateBasic(data: StreamData?): Boolean {
        if (data == null) return false
        if (data.tracks.isEmpty()) return false
        val first = data.tracks.firstOrNull() ?: return false
        if (first.videoUrl.isBlank()) return false
        return true
    }

    /**
     * בודק נגישות רשת קלה (HTTP Range request) לזרם הווידאו והאודיו הנבחרים בנפרד.
     */
    fun validateTrackNetwork(
        track: StreamTrack,
        streamUserAgent: String?
    ): ValidationResult {
        if (track.videoUrl.isBlank()) {
            return ValidationResult.GeneralError("videoUrl is blank")
        }

        // 1. בדיקת video URL
        val vRes = probeUrl(track.videoUrl, streamUserAgent)
        if (!vRes.first) {
            val code = vRes.second
            Diagnostics.log("StreamValidator: VIDEO_HTTP_$code (${track.label}) FAILED")
            return ValidationResult.VideoError(code, "VIDEO_HTTP_$code")
        }

        // 2. בדיקת audio URL במידה וקיים (DASH)
        if (!track.audioUrl.isNullOrBlank()) {
            val aRes = probeUrl(track.audioUrl, streamUserAgent)
            if (!aRes.first) {
                val code = aRes.second
                Diagnostics.log("StreamValidator: AUDIO_HTTP_$code (${track.label}) FAILED")
                return ValidationResult.AudioError(code, "AUDIO_HTTP_$code")
            }
        }

        return ValidationResult.OK
    }

    private fun probeUrl(url: String, userAgent: String?): Pair<Boolean, Int> {
        return runCatching {
            val builder = Request.Builder()
                .url(url)
                .header("Range", "bytes=0-1")
                .get()
            if (!userAgent.isNullOrBlank()) {
                builder.header("User-Agent", userAgent)
            }
            probeClient.newCall(builder.build()).execute().use { resp ->
                val ok = resp.isSuccessful || resp.code == 206
                ok to resp.code
            }
        }.getOrElse { false to -1 }
    }
}
