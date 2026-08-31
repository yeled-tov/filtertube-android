package com.filtertube.app.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.filtertube.app.MainActivity

/**
 * שירות ניגון ברקע (foreground service) — מחזיק את הנגן ואת ה-MediaSession.
 *
 * בזכותו:
 *  - המוזיקה ממשיכה לנגן גם כשיוצאים מהאפליקציה
 *  - מופיעה חלונית שליטה בהתראות ובמסך הנעילה
 *  - הטלפון מזהה את האפליקציה כנגן מדיה (כפתורי אוזניות וכו')
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val crossfadeHandler = Handler(Looper.getMainLooper())
    private var crossfadeTask: Runnable? = null
    private var incomingPlayer: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()
        // באפר אגרסיבי נגד עצירות: זרמי יוטיוב נחנקים מדי פעם (CDN throttling), אז
        // בונים מאגר גדול קדימה (עד 2 דקות) כדי לגשר על נפילות זמניות בהורדה. התחלה
        // עדיין מהירה (~1.5ש'), ואחרי עצירה בונים כרית של 5ש' לפני שממשיכים שלא ייתקע שוב.
        fun createPlayer(handleAudioFocus: Boolean = true) = ExoPlayer.Builder(this)
            .setMediaSourceFactory(FilterTubeMediaSourceFactory(this))
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        /* minBufferMs = */ 30_000,
                        /* maxBufferMs = */ 120_000,
                        /* bufferForPlaybackMs = */ 1_500,
                        /* bufferForPlaybackAfterRebufferMs = */ 5_000,
                    )
                    .build(),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                handleAudioFocus,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        val player = createPlayer()
        val settings = com.filtertube.app.data.SettingsStore(this)
        var fadingFromIndex = -1
        var fadingToIndex = -1
        var handoffInProgress = false

        fun cancelCrossfade() {
            crossfadeTask?.let(crossfadeHandler::removeCallbacks)
            crossfadeTask = null
            incomingPlayer?.release()
            incomingPlayer = null
            fadingFromIndex = -1
            fadingToIndex = -1
            handoffInProgress = false
            player.volume = 1f
            player.setPauseAtEndOfMediaItems(false)
        }

        fun completeHandoff() {
            val incoming = incomingPlayer ?: run {
                cancelCrossfade()
                return
            }
            val targetIndex = fadingToIndex
            if (targetIndex !in 0 until player.mediaItemCount) {
                cancelCrossfade()
                return
            }
            // The incoming player is already audible.  Seek the session player to exactly
            // the same point, wait a short moment for its buffer, then swap outputs.
            handoffInProgress = true
            player.setPauseAtEndOfMediaItems(false)
            player.volume = 0f
            player.seekTo(targetIndex, incoming.currentPosition)
            player.play()
            crossfadeHandler.postDelayed({
                if (!handoffInProgress) return@postDelayed
                player.volume = 1f
                incoming.release()
                incomingPlayer = null
                fadingFromIndex = -1
                fadingToIndex = -1
                handoffInProgress = false
            }, 180L)
        }

        fun startCrossfade() {
            val sourceIndex = player.currentMediaItemIndex
            val targetIndex = sourceIndex + 1
            val seconds = settings.crossfadeSeconds
            if (targetIndex !in 0 until player.mediaItemCount) return
            val current = player.currentMediaItem
            val next = player.getMediaItemAt(targetIndex)
            val audioOnly = current?.requestMetadata?.extras
                ?.getBoolean(Playback.EXTRA_IS_AUDIO) == true
            val nextAudioOnly = next.requestMetadata.extras
                ?.getBoolean(Playback.EXTRA_IS_AUDIO) == true
            if (seconds <= 0 || !audioOnly || !nextAudioOnly) return
            if (incomingPlayer != null || handoffInProgress) return

            // This player overlaps the session player, so it must not steal audio focus.
            val incoming = createPlayer(handleAudioFocus = false).apply {
                volume = 0f
                setMediaItem(next)
                prepare()
                play()
            }
            incomingPlayer = incoming
            fadingFromIndex = sourceIndex
            fadingToIndex = targetIndex
            // Keep the original item selected until it has completely faded out.
            player.setPauseAtEndOfMediaItems(true)
            val durationMs = seconds * 1_000L

            fun fadeWhenReady() {
                if (!incoming.isPlaying) {
                    crossfadeHandler.postDelayed({ if (incomingPlayer === incoming) fadeWhenReady() }, 40L)
                    return
                }
                val startedAt = android.os.SystemClock.elapsedRealtime()
                val task = object : Runnable {
                    override fun run() {
                        if (incomingPlayer !== incoming || player.currentMediaItemIndex != sourceIndex) return
                        val fraction = ((android.os.SystemClock.elapsedRealtime() - startedAt).toFloat() / durationMs)
                            .coerceIn(0f, 1f)
                        player.volume = 1f - fraction
                        incoming.volume = fraction
                        if (fraction < 1f) crossfadeHandler.postDelayed(this, 25L)
                    }
                }
                crossfadeTask = task
                crossfadeHandler.post(task)
            }
            fadeWhenReady()
        }

        val crossfadeWatch = object : Runnable {
            override fun run() {
                val duration = player.duration
                val seconds = settings.crossfadeSeconds
                val canFade = seconds > 0 && player.isPlaying && player.hasNextMediaItem() && duration > 0
                if (canFade && incomingPlayer == null && !handoffInProgress &&
                    player.currentPosition >= duration - seconds * 1_000L
                ) startCrossfade()
                if (!canFade && incomingPlayer == null) player.setPauseAtEndOfMediaItems(false)
                crossfadeHandler.postDelayed(this, 120L)
            }
        }
        crossfadeHandler.post(crossfadeWatch)

        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_ENDED && incomingPlayer != null) {
                    completeHandoff()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: androidx.media3.common.Player.PositionInfo,
                newPosition: androidx.media3.common.Player.PositionInfo,
                reason: Int,
            ) {
                if (!handoffInProgress && reason != androidx.media3.common.Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    cancelCrossfade()
                }
            }
        })

        // אבחון: מתעד עצירות/באפר באמצע הניגון (משך + שנייה) ושגיאות נגן — כדי לראות
        // בדיוק מה ה"מתנגן ואז נעצר" במקום לנחש.
        player.addListener(object : androidx.media3.common.Player.Listener {
            private var stallStart = 0L
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    androidx.media3.common.Player.STATE_BUFFERING ->
                        if (player.currentPosition > 1500 && player.playWhenReady && stallStart == 0L) {
                            stallStart = android.os.SystemClock.elapsedRealtime()
                        }
                    androidx.media3.common.Player.STATE_READY ->
                        if (stallStart > 0L) {
                            val ms = android.os.SystemClock.elapsedRealtime() - stallStart
                            com.filtertube.app.data.Diagnostics.log("⚠ עצירה ${ms}ms בשנייה ${player.currentPosition / 1000}")
                            stallStart = 0L
                        }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                com.filtertube.app.data.Diagnostics.log("✖ שגיאת נגן: ${error.errorCodeName}")
            }
        })

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openAppIntent())
            .build()
    }

    /** לחיצה על חלונית ההתראה / מסך הנעילה תפתח את האפליקציה. */
    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return PendingIntent.getActivity(this, 0, intent, flags)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        crossfadeTask?.let(crossfadeHandler::removeCallbacks)
        crossfadeHandler.removeCallbacksAndMessages(null)
        incomingPlayer?.release()
        incomingPlayer = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
