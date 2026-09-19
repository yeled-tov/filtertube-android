package com.filtertube.app.playback

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.filtertube.app.data.Diagnostics
import com.filtertube.app.data.Http
import java.util.concurrent.TimeUnit

/**
 * Factory שיודע למזג זרם וידאו-בלבד עם זרם אודיו נפרד (DASH של יוטיוב),
 * שומר על ה-User-Agent של ה-Resolver גם ל-video וגם ל-audio בנפרד.
 *
 * ## למה DefaultDataSource ולא מקור HTTP ישירות
 * זה ה-Factory של *כל* הניגון באפליקציה, לא רק של זרמי יוטיוב. כשנבנה
 * DefaultMediaSourceFactory מעל מקור HTTP בלבד, הוא מנסה לפתוח גם
 * `content://` ו-`file://` דרך HTTP — וכל ניגון מקומי נכשל מיד. זה מה
 * שהפיל את הניגון של קבצים שהורדו ואת הניגון מהמדיה שבמכשיר, בלי קשר
 * להרשאות.
 *
 * DefaultDataSource פותר לפי הסכימה: content, file ו-asset מקומית, וכל
 * http/https מועבר ל-Factory שנבנה כאן עם ה-User-Agent הנכון.
 *
 * ## למה OkHttp ולא DefaultHttpDataSource — שורש העצירות באמצע שיר
 * ביומן מהמכשיר הופיעו עצירות של 24 ו-39 שניות באמצע ניגון, בלי שום
 * שורת שגיאה לידן. זה בדיוק החתימה של חיבור keep-alive מת:
 *
 * DefaultHttpDataSource עובדת מעל HttpURLConnection, ששומרת חיבורים
 * פתוחים במאגר ואינה יודעת לזהות שהצד השני כבר סגר אותו. googlevideo
 * סוגרת חיבורי סרק — וזה קורה בדיוק כשהבאפר מלא והנגן הפסיק לקרוא
 * לרגע. הקריאה הבאה נתקעת עד ל-timeout של 8 שניות, ואז ExoPlayer מנסה
 * שוב עם השהיה שגדלה: 8 + 0 + 8 + 1,000 + 8 + 2,000 + 8 ≈ 39 שניות.
 * המספר ביומן אינו מקרי.
 *
 * OkHttp מזהה חיבור שנסגר ופותחת אחד חדש בעצמה (retryOnConnectionFailure),
 * בלי שהנגן יראה שגיאה בכלל — כלומר הסיבה הנפוצה לעצירה נעלמת במקום
 * להתקצר. בנוסף זה אותו מאגר חיבורים של כל האפליקציה, ולכן חיבור
 * ל-googlevideo שכבר נפתח בחילוץ הזרם משמש גם לניגון.
 */
@UnstableApi
class FilterTubeMediaSourceFactory(context: Context) : MediaSource.Factory {

    private val appContext = context.applicationContext

    /**
     * לקוח הניגון — נגזר מהמשותף, עם סבלנות קצרה בהרבה.
     *
     * 20 שניות המתנה לבייט הבא הגיוניות לבקשת API שמחזירה JSON; בזרם
     * מדיה הן אסון. זרם חי שולח ברצף, ולכן שקט של ארבע שניות פירושו
     * שהחיבור מת ולא שהרשת איטית — וכל שנייה שממתינים לפני שמוותרים
     * עליו היא שנייה שהמשתמש שומע כשקט.
     */
    private val callFactory = Http.newBuilder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val default = factoryFor(DEFAULT_UA)

    private fun factoryFor(userAgent: String): DefaultMediaSourceFactory {
        val http = OkHttpDataSource.Factory(callFactory).setUserAgent(userAgent)
        return DefaultMediaSourceFactory(DefaultDataSource.Factory(appContext, http))
            .setLoadErrorHandlingPolicy(FastRetryPolicy)
    }

    override fun getSupportedTypes(): IntArray = default.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val extras = mediaItem.requestMetadata.extras
        val audioUrl = extras?.getString(EXTRA_AUDIO_URL)
        val ua = extras?.getString(EXTRA_USER_AGENT)
        val srcFactory = if (ua.isNullOrEmpty()) default else factoryFor(ua)

        val video = srcFactory.createMediaSource(mediaItem)
        return if (!audioUrl.isNullOrEmpty()) {
            val audioItem = MediaItem.Builder()
                .setUri(audioUrl)
                .setRequestMetadata(
                    MediaItem.RequestMetadata.Builder()
                        .setExtras(extras)
                        .build()
                )
                .build()
            val audio = srcFactory.createMediaSource(audioItem)
            MergingMediaSource(video, audio)
        } else {
            video
        }
    }

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory {
        default.setDrmSessionManagerProvider(provider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory {
        default.setLoadErrorHandlingPolicy(policy)
        return this
    }

    /**
     * ניסיון חוזר מהיר במקום המתנה שמכפילה את עצמה.
     *
     * ההשהיה המובנית של ExoPlayer היא 0, 1,000 ואז 2,000 מילישניות —
     * הגיונית מול שרת עמוס שצריך זמן להתאושש, ולא מול חיבור שנסגר,
     * שאפשר לפתוח מחדש מיד. בזרם מדיה ההשהיה הזו נשמעת אחד לאחד כשקט.
     *
     * **מה לא משתנה כאן**: ההחלטה *האם* בכלל לנסות שוב נשארת של מחלקת
     * הבסיס. היא מחזירה TIME_UNSET לשגיאות שאין טעם לחזור עליהן — 403
     * (כתובת שפגה), 404 ו-410 — וכך הן ממשיכות לצוף מיד אל
     * [PlayerRecoveryHandler], שמחלץ כתובת חדשה. זו התגובה הנכונה לשגיאות
     * האלה, וניסיון חוזר עליהן רק היה מבזבז עוד שניות של שקט.
     */
    private object FastRetryPolicy : DefaultLoadErrorHandlingPolicy() {
        override fun getRetryDelayMsFor(info: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            val base = super.getRetryDelayMsFor(info)
            if (base == C.TIME_UNSET) return base
            Diagnostics.log(
                "LOAD ניסיון ${info.errorCount}: ${info.exception.javaClass.simpleName} — " +
                    "מנסה שוב מיד",
            )
            return (info.errorCount * 200L).coerceAtMost(800L)
        }
    }

    companion object {
        const val EXTRA_AUDIO_URL = "filtertube_audio_url"
        const val EXTRA_USER_AGENT = "filtertube_user_agent"
        private const val DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    }
}
