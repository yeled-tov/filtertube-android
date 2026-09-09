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

    /**
     * אחרי כמה זמן מונה הניסיונות מתאפס.
     *
     * זה היה הבאג שהשבית סרטון לכל אורך חיי התהליך. המונה עלה ל-2, ומאותו
     * רגע כל שגיאה חדשה חישבה attempt=3, ראתה שזה מעל המקסימום, ויצאה מיד
     * בלי לנסות שום דבר. ביומן זה נראה כך, חמש פעמים ברצף על אותו סרטון:
     *
     *   PLAYER ERROR videoId=197DFGcL8wE code=ERROR_CODE_IO_BAD_HTTP_STATUS attempt=3
     *   PLAYER ERROR videoId=197DFGcL8wE חרג מ-2 ניסיונות התאוששות ✖
     *
     * המשתמש לחץ נגן שוב ושוב לאורך שתי דקות, והאפליקציה סירבה אפילו לנסות.
     * שתי דקות שקט מספיקות כדי להניח שזו לחיצה חדשה ולא לולאת כשל.
     */
    private const val ATTEMPT_WINDOW_MS = 120_000L

    private data class Attempts(val count: Int, val lastAt: Long)

    private val recoveryAttempts = ConcurrentHashMap<String, Attempts>()

    /** מוצג למשתמש כשההתאוששות ויתרה — במקום מסך שנשאר תקוע בלי הסבר. */
    @Volatile
    var onGaveUp: ((String) -> Unit)? = null

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
        val now = System.currentTimeMillis()
        val previous = recoveryAttempts[videoId]
            ?.takeIf { now - it.lastAt < ATTEMPT_WINDOW_MS }
        val attempt = (previous?.count ?: 0) + 1

        Diagnostics.log("PLAYER ERROR videoId=$videoId code=$codeName attempt=$attempt pos=${pos / 1000}s")

        if (!isStreamIoError(error)) {
            Diagnostics.log("PLAYER ERROR videoId=$videoId non-IO error, skipping auto recovery")
            return
        }

        if (attempt > MAX_RECOVERY_ATTEMPTS) {
            Diagnostics.log("PLAYER ERROR videoId=$videoId חרג מ-$MAX_RECOVERY_ATTEMPTS ניסיונות התאוששות ✖")
            onGaveUp?.invoke(videoId)
            return
        }

        recoveryAttempts[videoId] = Attempts(attempt, now)

        // המנוע שהפיק את הכתובת המתה — כדי לא לבקש ממנו בדיוק אותה כתובת שוב.
        val failedBy = StreamRepository.getCached(videoId)?.resolvedBy

        // תיעוד כשל בבריאות ה-Resolver
        ResolverHealthMonitor.recordFailure("ExoPlayerIO", codeName)

        // חילוץ חדש והחלפה אוטומטית בנגן
        scope.launch(Dispatchers.IO) {
            Diagnostics.log(
                "RECOVERY videoId=$videoId חילוץ מחדש (ניסיון $attempt" +
                    (if (failedBy.isNullOrBlank()) ")" else ", בלי $failedBy)") + "...",
            )
            val newData = runCatching { StreamRepository.resolveFresh(videoId, failedBy) }.getOrNull()

            if (newData == null) {
                Diagnostics.log("RECOVERY videoId=$videoId חילוץ מחדש נכשל ✖")
                withContext(Dispatchers.Main) { onGaveUp?.invoke(videoId) }
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
                    Diagnostics.log(
                        "RECOVERY videoId=$videoId הצליח דרך ${newData.resolvedBy} — " +
                            "הנגינה חודשה מ-${pos / 1000}s ✓",
                    )
                }
            }
        }
    }

    fun resetAttempts(videoId: String) {
        recoveryAttempts.remove(videoId)
    }
}
