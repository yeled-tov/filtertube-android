package com.filtertube.app.playback

import android.app.PendingIntent
import android.content.Intent
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

    override fun onCreate() {
        super.onCreate()
        // buffer קצר להתחלת ניגון מהירה: מתחיל אחרי ~1 שניה במקום 2.5 (ברירת מחדל)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 50_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_000,
            )
            .build()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(FilterTubeMediaSourceFactory(this))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

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
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
