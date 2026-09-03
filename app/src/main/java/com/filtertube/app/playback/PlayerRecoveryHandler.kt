package com.filtertube.app.playback

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.filtertube.app.data.Diagnostics
import com.filtertube.app.data.ResolverHealthMonitor
import com.filtertube.app.data.SettingsStore
import com.filtertube.app.data.StreamRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * מנגנון התאוששות אוטומטי (Auto Recovery) כאשר ExoPlayer נתקל בשגיאת HTTP / 403 / רשת.
 * במקום לעצור ולתקוע את המשתמש — מנקה מטמון, מבקש StreamData חדש דרך ה-Resolver הבא,
 * וממשיך בנגינה באותה נקודת זמן.
 */
@UnstableApi
object PlayerRecoveryHandler {

    private const val MAX_RECOVERY_ATTEMPTS = 2
    private val recoveryAttempts = ConcurrentHashMap<String, Int>()

    fun isStreamIoError(error: PlaybackException): Boolean {
        val code = error.errorCode
        val codeName = error.errorCodeName
        return codeName.contains("IO") ||
            codeName.contains("HTTP") ||
            codeName.contains("NETWORK") ||
            code in 2000..2008
    }

    fun handlePlayerError(
        context: Context,
        player: Player,
        error: PlaybackException,
        scope: CoroutineScope
    ) {
        val item = player.currentMediaItem
        val videoId = item?.mediaId
        if (videoId.isNullOrBlank()) {
            Diagnostics.log("PLAYER ERROR: missing videoId code=${error.errorCodeName}")
            return
        }

        val codeName = error.errorCodeName
        val pos = player.currentPosition.coerceAtLeast(0L)
        val attempt = (recoveryAttempts[videoId] ?: 0) + 1

        Diagnostics.log("PLAYER ERROR videoId=$videoId code=$codeName attempt=$attempt pos=${pos / 1000}s")

        if (!isStreamIoError(error)) {
            Diagnostics.log("PLAYER ERROR videoId=$videoId non-IO error, skipping auto recovery")
            return
        }

        if (attempt > MAX_RECOVERY_ATTEMPTS) {
            Diagnostics.log("PLAYER ERROR videoId=$videoId חרג מ-$MAX_RECOVERY_ATTEMPTS ניסיונות התאוששות ✖")
            return
        }

        recoveryAttempts[videoId] = attempt

        // 1. ביטול מטמון עבור הווידאו שנכשל
        StreamRepository.invalidateCache(videoId)

        // 2. תיעוד כשל בבריאות ה-Resolver
        ResolverHealthMonitor.recordFailure("ExoPlayerIO", codeName)

        // 3. חילוץ חדש והחלפה אוטומטית בנגן
        scope.launch(Dispatchers.IO) {
            Diagnostics.log("RECOVERY videoId=$videoId מתחיל חילוץ מחדש (ניסיון $attempt)...")
            val newData = runCatching { StreamRepository.getStream(videoId) }.getOrNull()

            if (newData == null) {
                Diagnostics.log("RECOVERY videoId=$videoId חילוץ מחדש נכשל ✖")
                return@launch
            }

            withContext(Dispatchers.Main) {
                if (player.mediaItemCount == 0) return@withContext
                val settings = SettingsStore(context)
                val audio = Playback.forcedAudio(null, settings.filterLevel)
                val newItem = Playback.buildItem(newData, videoId, audio, Playback.defaultQuality(newData, settings.preferredQuality))

                val currentIndex = player.currentMediaItemIndex
                if (currentIndex in 0 until player.mediaItemCount) {
                    player.replaceMediaItem(currentIndex, newItem)
                    player.seekTo(currentIndex, pos)
                    player.prepare()
                    player.play()
                    Diagnostics.log("RECOVERY videoId=$videoId הצליח! הנגינה חודשה מ-${pos / 1000}s ✓")
                }
            }
        }
    }

    fun resetAttempts(videoId: String) {
        recoveryAttempts.remove(videoId)
    }
}
