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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * שירות ניגון ברקע (foreground service) — מחזיק את הנגן ואת ה-MediaSession,
 * ומשלב מנגנון התאוששות אוטומטי (Auto Recovery) משגיאות ניגון/רשת.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val crossfadeHandler = Handler(Looper.getMainLooper())
    private var crossfadeTask: Runnable? = null
    private var incomingPlayer: ExoPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        fun createPlayer(handleAudioFocus: Boolean = true) = ExoPlayer.Builder(this)
            .setMediaSourceFactory(FilterTubeMediaSourceFactory(this))
            .setLoadControl(
                DefaultLoadControl.Builder()
                    // bufferForPlaybackMs הוא כמה מדיה חייבת להיות בבאפר לפני
                    // שהצליל יוצא בכלל. 1,500ms פירושו שנייה וחצי של שקט אחרי
                    // שהזרם כבר נפתר והורד — זמן שנוסף ישירות למה שהמשתמש חווה
                    // כ"לחצתי והוא חושב". 500ms מספיק כדי להתחיל בבטחה, וה-30
                    // שניות של minBufferMs ממילא ממשיכות להתמלא תוך כדי ניגון.
                    //
                    // maxBufferMs ירד מ-120 שניות ל-60: שתי דקות של קריאה מראש
                    // מתחרות על אותה רשת עם החימום מראש ועם העשרת המטא-דאטה,
                    // ודקה אחת כבר מכסה כל הפרעת רשת סבירה.
                    .setBufferDurationsMs(
                        /* minBufferMs = */ 30_000,
                        /* maxBufferMs = */ 60_000,
                        /* bufferForPlaybackMs = */ 500,
                        /* bufferForPlaybackAfterRebufferMs = */ 2_000,
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

            val incoming = createPlayer(handleAudioFocus = false).apply {
                volume = 0f
                setMediaItem(next)
                prepare()
                play()
            }
            incomingPlayer = incoming
            fadingFromIndex = sourceIndex
            fadingToIndex = targetIndex
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

        // ── הגדרות FilterMusic, מוחלות על הנגן החי ───────────────────────
        // הן נקראות מחדש כל 800ms במקום להיות מוזרקות מהמסך, כי המסך והשירות
        // הם שני תהליכים לוגיים נפרדים: שינוי בהגדרות חייב להישמע מיד גם
        // כשהניגון כבר רץ ברקע והמסך סגור. הבדיקה זולה — קריאה מ-SharedPreferences
        // שכבר במטמון — ומוחלת רק כשהערך באמת השתנה.
        val applySettings = object : Runnable {
            private var lastSpeed = -1
            private var lastPitch = -1
            private var lastSkipSilence: Boolean? = null
            private var enhancer: android.media.audiofx.LoudnessEnhancer? = null
            private var enhancerSession = 0
            private var sleepArmedAt = 0L
            private var sleepMinutes = 0

            override fun run() {
                val speed = settings.playbackSpeed
                val pitch = settings.playbackPitch
                if (speed != lastSpeed || pitch != lastPitch) {
                    lastSpeed = speed
                    lastPitch = pitch
                    player.playbackParameters = androidx.media3.common.PlaybackParameters(
                        speed / 100f, pitch / 100f,
                    )
                }

                val skip = settings.skipSilence
                if (skip != lastSkipSilence) {
                    lastSkipSilence = skip
                    player.skipSilenceEnabled = skip
                }

                // ── הגברת עוצמה ──────────────────────────────────────
                // LoudnessEnhancer מוסיף הגבר קבוע לפלט. הוא נקשר ל-session
                // של הנגן, וה-session מתחלף כשהנגן נבנה מחדש — לכן בודקים
                // גם את המזהה ולא רק את ההגדרה, אחרת האפקט היה נשאר תלוי
                // באוויר על session מת.
                val wantBoost = settings.audioNormalization
                val session = player.audioSessionId
                if (!wantBoost || session != enhancerSession) {
                    runCatching { enhancer?.release() }
                    enhancer = null
                    enhancerSession = 0
                }
                if (wantBoost && enhancer == null && session != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
                    runCatching {
                        enhancer = android.media.audiofx.LoudnessEnhancer(session).apply {
                            setTargetGain(700)   // 7dB — מורגש בלי לעוות
                            enabled = true
                        }
                        enhancerSession = session
                    }.onFailure {
                        com.filtertube.app.data.Diagnostics.log("AUDIO: הגברת עוצמה לא נתמכת — ${it.message}")
                    }
                }

                // טיימר שינה: נמדד מרגע ההדלקה, ומכבה את הניגון כשהזמן עבר.
                val minutes = settings.sleepTimerMinutes
                if (minutes != sleepMinutes) {
                    sleepMinutes = minutes
                    sleepArmedAt = if (minutes > 0) System.currentTimeMillis() else 0L
                }
                if (sleepMinutes > 0 && sleepArmedAt > 0L &&
                    System.currentTimeMillis() - sleepArmedAt >= sleepMinutes * 60_000L
                ) {
                    player.pause()
                    settings.sleepTimerMinutes = 0
                    sleepMinutes = 0
                    sleepArmedAt = 0L
                    com.filtertube.app.data.Diagnostics.log("SLEEP TIMER: הניגון נעצר")
                }

                crossfadeHandler.postDelayed(this, 800L)
            }
        }
        crossfadeHandler.post(applySettings)

        player.addListener(object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_ENDED && incomingPlayer != null) {
                    completeHandoff()
                }
                // ── "סגור" חייב לעצור גם את נגן העמעום ────────────────────
                // העמעום המוצלב בונה ExoPlayer *שני* שכבר מנגן את השיר הבא
                // בפלט נפרד. stop() על הבקר נגע רק בנגן הראשי, והשני המשיך
                // להשמיע — בדיוק מה שנראה כמו "לחצתי איקס והשיר הבא ממשיך".
                // stop() מעביר את הנגן ל-IDLE, וזו הנקודה לתפוס.
                if (playbackState == androidx.media3.common.Player.STATE_IDLE) {
                    cancelCrossfade()
                }
                if (playbackState == androidx.media3.common.Player.STATE_READY) {
                    player.currentMediaItem?.mediaId?.let { PlayerRecoveryHandler.resetAttempts(it) }
                }
            }

            override fun onTimelineChanged(
                timeline: androidx.media3.common.Timeline,
                reason: Int,
            ) {
                // clearMediaItems() לא עובר דרך STATE_IDLE. תור ריק פירושו
                // שאין למה לעמעם.
                if (player.mediaItemCount == 0) cancelCrossfade()
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

        // אבחון והתאוששות אוטומטית (Auto Recovery) בשגיאות ניגון/רשת
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
                // כשההתאוששות מוותרת המשתמש חייב לדעת. עד עכשיו המסך פשוט
                // נשאר תקוע בלי שום הסבר, וזה נראה כמו אפליקציה תקועה.
                PlayerRecoveryHandler.onGaveUp = {
                    android.widget.Toast.makeText(
                        this@PlaybackService,
                        "לא הצלחנו לנגן את הסרטון הזה — מדלגים לבא",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                    // תור שנתקע על פריט מת הוא תור שבור. אם יש המשך — ממשיכים.
                    if (player.hasNextMediaItem()) {
                        player.seekToNextMediaItem()
                        player.prepare()
                        player.play()
                    }
                }
                PlayerRecoveryHandler.handlePlayerError(
                    context = this@PlaybackService,
                    player = player,
                    error = error,
                    scope = serviceScope
                )
            }
        })

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openAppIntent())
            .build()
    }

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
        serviceScope.cancel()
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
