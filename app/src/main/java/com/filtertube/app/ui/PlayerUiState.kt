package com.filtertube.app.ui
import com.filtertube.app.ThemeState

import android.content.ComponentName
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.filtertube.app.playback.Playback
import com.filtertube.app.playback.PlaybackService
import kotlinx.coroutines.delay

/** התחברות יחידה לשירות הניגון, חיה לאורך כל האפליקציה. */
@Composable
fun rememberMediaController(): State<MediaController?> {
    val context = LocalContext.current
    val state = remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { state.value = runCatching { future.get() }.getOrNull() },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            state.value = null
            MediaController.releaseFuture(future)
        }
    }
    return state
}

/** מצב הנגן שנצפה ב-Compose — משותף בין המיני-נגן למסך המלא. */
class PlayerUiState {
    var hasMedia by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
    var buffering by mutableStateOf(false)
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var artworkUri by mutableStateOf<Uri?>(null)
    var mediaId by mutableStateOf<String?>(null)
    var isAudio by mutableStateOf(false)
    var position by mutableStateOf(0L)
    var duration by mutableStateOf(0L)
    var hasNext by mutableStateOf(false)
    var hasPrev by mutableStateOf(false)
    var queueVersion by mutableStateOf(0)   // משתנה בכל שינוי בתור — להרענון רשימת "הבא בתור"
}

@Composable
fun rememberPlayerUiState(controller: MediaController?): PlayerUiState {
    val state = remember { PlayerUiState() }

    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose {}
        fun sync() {
            state.hasMedia = c.mediaItemCount > 0
            state.isPlaying = c.isPlaying
            state.buffering = c.playbackState == Player.STATE_BUFFERING
            val mm = c.mediaMetadata
            state.title = mm.title?.toString() ?: ""
            state.artist = mm.artist?.toString() ?: ""
            state.artworkUri = mm.artworkUri
            state.mediaId = c.currentMediaItem?.mediaId
            state.isAudio = c.currentMediaItem?.requestMetadata?.extras?.getBoolean(Playback.EXTRA_IS_AUDIO) ?: false
            state.duration = c.duration.coerceAtLeast(0L)
            state.hasNext = c.hasNextMediaItem()
            state.hasPrev = c.hasPreviousMediaItem()
            state.queueVersion++
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = sync()
        }
        c.addListener(listener)
        sync()
        onDispose { c.removeListener(listener) }
    }

    LaunchedEffect(controller) {
        while (true) {
            controller?.let {
                state.position = it.currentPosition
                state.duration = it.duration.coerceAtLeast(0L)
            }
            delay(500)
        }
    }
    return state
}
