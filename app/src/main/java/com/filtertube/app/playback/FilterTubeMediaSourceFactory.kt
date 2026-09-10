package com.filtertube.app.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * Factory שיודע למזג זרם וידאו-בלבד עם זרם אודיו נפרד (DASH של יוטיוב),
 * שומר על ה-User-Agent של ה-Resolver גם ל-video וגם ל-audio בנפרד.
 *
 * ## למה DefaultDataSource ולא DefaultHttpDataSource ישירות
 * זה ה-Factory של *כל* הניגון באפליקציה, לא רק של זרמי יוטיוב. כשנבנה
 * DefaultMediaSourceFactory מעל מקור HTTP בלבד, הוא מנסה לפתוח גם
 * `content://` ו-`file://` דרך HTTP — וכל ניגון מקומי נכשל מיד. זה מה
 * שהפיל את הניגון של קבצים שהורדו ואת הניגון מהמדיה שבמכשיר, בלי קשר
 * להרשאות.
 *
 * DefaultDataSource פותר לפי הסכימה: content, file ו-asset מקומית, וכל
 * http/https מועבר ל-Factory שנבנה כאן עם ה-User-Agent הנכון.
 */
@UnstableApi
class FilterTubeMediaSourceFactory(context: Context) : MediaSource.Factory {

    private val appContext = context.applicationContext

    private val default = factoryFor(DEFAULT_UA)

    private fun factoryFor(userAgent: String): DefaultMediaSourceFactory {
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
        return DefaultMediaSourceFactory(DefaultDataSource.Factory(appContext, http))
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

    companion object {
        const val EXTRA_AUDIO_URL = "filtertube_audio_url"
        const val EXTRA_USER_AGENT = "filtertube_user_agent"
        private const val DEFAULT_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    }
}
