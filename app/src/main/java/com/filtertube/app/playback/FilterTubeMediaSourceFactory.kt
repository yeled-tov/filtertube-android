package com.filtertube.app.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/**
 * Factory שיודע למזג זרם וידאו-בלבד עם זרם אודיו נפרד (DASH של יוטיוב),
 * כדי לאפשר איכויות גבוהות. כתובת האודיו מועברת ב-extras של ה-MediaItem.
 *
 * כך גם השירות (שמנגן ברקע) וגם ה-UI משתמשים באותה לוגיקת מיזוג.
 */
@UnstableApi
class FilterTubeMediaSourceFactory(context: Context) : MediaSource.Factory {

    private val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Linux; Android) FilterTube")
        .setAllowCrossProtocolRedirects(true)

    private val default = DefaultMediaSourceFactory(httpFactory)

    override fun getSupportedTypes(): IntArray = default.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val audioUrl = mediaItem.requestMetadata.extras?.getString(EXTRA_AUDIO_URL)
        val video = default.createMediaSource(mediaItem)
        return if (!audioUrl.isNullOrEmpty()) {
            val audio = default.createMediaSource(MediaItem.fromUri(audioUrl))
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
    }
}
