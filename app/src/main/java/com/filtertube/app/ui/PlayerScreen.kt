package com.filtertube.app.ui

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.filtertube.app.data.*
import kotlinx.coroutines.delay

sealed class PlayerState {
    data object Loading : PlayerState()
    data class Ready(val data: StreamData, val audioOnly: Boolean, val upNext: Video?) : PlayerState()
    data class Error(val message: String) : PlayerState()
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    videoId: String,
    title: String,
    channelName: String,
    onBack: () -> Unit,
    onPlayNext: (Video) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val settings = remember { SettingsStore(context) }
    var state by remember(videoId) { mutableStateOf<PlayerState>(PlayerState.Loading) }
    var isFullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(videoId) {
        state = try {
            val data = StreamRepository.getStream(videoId)
            val channels = ChannelsRepository.getChannels(context)
            val catById = channels.associate { it.youtubeChannelId to it.category }
            val allowed = channels.map { it.youtubeChannelId }.toHashSet()

            val category = catById[data.channelId]
            // אודיו בלבד: קטגוריית "דתי לייט" תמיד, או רמה 1 מחמירה על מוזיקה
            val audioOnly = category in audioOnlyCategories ||
                (settings.filterLevel == 1 && category == "music")

            val upNext = data.related.firstOrNull { it.channelId in allowed && it.id != videoId }

            PlayerState.Ready(data, audioOnly, upNext)
        } catch (e: Exception) {
            PlayerState.Error("לא הצלחנו לטעון את הסרטון: ${e.message}")
        }
    }

    // מסך מלא — סיבוב לרוחב + הסתרת סרגלי מערכת
    LaunchedEffect(isFullscreen) {
        activity?.let { act ->
            act.requestedOrientation = if (isFullscreen)
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
            if (isFullscreen) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.getInsetsController(act.window, act.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler { if (isFullscreen) isFullscreen = false else onBack() }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F))) {
        if (!isFullscreen) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color.Black)
                    .padding(top = 24.dp, start = 4.dp, end = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזור", tint = Color.White)
                }
                Text("צפייה", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Box(
            modifier = (if (isFullscreen) Modifier.fillMaxSize()
            else Modifier.fillMaxWidth().aspectRatio(16f / 9f)).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            when (val s = state) {
                is PlayerState.Loading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFFFF0000))
                    Spacer(Modifier.height(12.dp))
                    Text("טוען נגן...", color = Color(0xFFAAAAAA), fontSize = 13.sp)
                }
                is PlayerState.Error -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp),
                ) {
                    Icon(Icons.Default.Warning, null, tint = Color(0xFFFF0000), modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(s.message, color = Color(0xFFAAAAAA), fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                is PlayerState.Ready -> VideoPlayer(
                    data = s.data,
                    audioOnly = s.audioOnly,
                    isFullscreen = isFullscreen,
                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                    upNext = s.upNext,
                    onPlayNext = onPlayNext,
                )
            }
        }

        if (!isFullscreen) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                val displayTitle = (state as? PlayerState.Ready)?.data?.title ?: title
                val displayChannel = (state as? PlayerState.Ready)?.data?.uploaderName ?: channelName
                Text(displayTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White, lineHeight = 20.sp)
                Spacer(Modifier.height(8.dp))
                Text(displayChannel, fontSize = 13.sp, color = Color(0xFFAAAAAA))

                val ready = state as? PlayerState.Ready
                if (ready != null) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF272727))
                            .clickable { downloadVideo(context, ready.data.bestVideoUrl, displayTitle) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Download, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("הורד", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/** נגן מלא בסגנון NewPipe — בקרים מותאמים, מחוות, מהירות ובחירת איכות. */
@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(
    data: StreamData,
    audioOnly: Boolean,
    isFullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    upNext: Video?,
    onPlayNext: (Video) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val httpFactory = remember {
        DefaultHttpDataSource.Factory().setUserAgent("Mozilla/5.0 (Linux; Android) FilterTube")
    }

    val tracks = data.tracks
    val initialIndex = remember(data) {
        tracks.indexOfFirst { it.height in 1..720 }.let { if (it >= 0) it else 0 }
            .coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
    }
    var qualityIndex by remember(data) { mutableStateOf(initialIndex) }
    var speed by remember(data) { mutableStateOf(1f) }

    fun sourceFor(index: Int): MediaSource {
        if (audioOnly) {
            val a = data.bestAudioUrl ?: data.bestVideoUrl
            return ProgressiveMediaSource.Factory(httpFactory).createMediaSource(MediaItem.fromUri(a))
        }
        val t = tracks.getOrNull(index)
            ?: return ProgressiveMediaSource.Factory(httpFactory)
                .createMediaSource(MediaItem.fromUri(data.bestVideoUrl))
        val v = ProgressiveMediaSource.Factory(httpFactory).createMediaSource(MediaItem.fromUri(t.videoUrl))
        return if (t.audioUrl != null) {
            val a = ProgressiveMediaSource.Factory(httpFactory).createMediaSource(MediaItem.fromUri(t.audioUrl))
            MergingMediaSource(v, a)
        } else v
    }

    val exo = remember(data) {
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(sourceFor(initialIndex))
            prepare()
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var buffering by remember { mutableStateOf(true) }
    var showUpNext by remember(data) { mutableStateOf(false) }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                buffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_ENDED && upNext != null) showUpNext = true
            }
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
        }
        exo.addListener(listener)
        onDispose { exo.removeListener(listener); exo.release() }
    }

    LaunchedEffect(exo) {
        while (true) {
            position = exo.currentPosition
            duration = exo.duration.coerceAtLeast(0L)
            delay(500)
        }
    }
    LaunchedEffect(speed) { exo.setPlaybackParameters(PlaybackParameters(speed)) }

    fun changeQuality(index: Int) {
        val pos = exo.currentPosition
        val pw = exo.playWhenReady
        qualityIndex = index
        exo.setMediaSource(sourceFor(index))
        exo.prepare()
        exo.seekTo(pos)
        exo.playWhenReady = pw
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(feedback) { if (feedback != null) { delay(900); feedback = null } }
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) { delay(3500); controlsVisible = false }
    }

    Box(modifier = Modifier.fillMaxSize().onSizeChanged { boxSize = it }) {
        // משטח וידאו / כריכת אלבום במצב אודיו
        if (audioOnly) {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(140.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF272727)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (data.thumbnailUrl != null) {
                            AsyncImage(model = data.thumbnailUrl, contentDescription = null,
                                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, null, tint = Color(0xFFAAAAAA), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("מצב אודיו בלבד", color = Color(0xFFAAAAAA), fontSize = 13.sp)
                    }
                }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exo
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // שכבת מחוות (פעילה כשהבקרים מוסתרים)
        Box(
            modifier = Modifier.fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            val w = boxSize.width.coerceAtLeast(1)
                            if (offset.x < w / 2f) {
                                exo.seekTo((exo.currentPosition - 10_000).coerceAtLeast(0))
                                feedback = "⏪ 10 ש׳"
                            } else {
                                exo.seekTo(exo.currentPosition + 10_000)
                                feedback = "10 ש׳ ⏩"
                            }
                        },
                    )
                }
                .pointerInput(audioOnly) {
                    if (audioOnly) return@pointerInput
                    detectVerticalDragGestures { change, dragAmount ->
                        val w = boxSize.width.coerceAtLeast(1)
                        val h = boxSize.height.coerceAtLeast(1)
                        if (change.position.x < w / 2f) {
                            val window = activity?.window ?: return@detectVerticalDragGestures
                            val lp = window.attributes
                            var b = lp.screenBrightness
                            if (b < 0f) b = 0.5f
                            b = (b - dragAmount / h).coerceIn(0.01f, 1f)
                            lp.screenBrightness = b
                            window.attributes = lp
                            feedback = "☀ ${(b * 100).toInt()}%"
                        } else {
                            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val nv = (cur + (-dragAmount / h * max * 1.6f)).toInt().coerceIn(0, max)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nv, 0)
                            feedback = "🔊 ${nv * 100 / max}%"
                        }
                    }
                },
        )

        if (buffering) CircularProgressIndicator(color = Color(0xFFFF0000), modifier = Modifier.align(Alignment.Center))

        feedback?.let {
            Text(
                it, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
                    .clip(RoundedCornerShape(8.dp)).background(Color(0xCC000000))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (controlsVisible) {
            ControlsOverlay(
                title = data.title,
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                speed = speed,
                tracks = tracks,
                qualityIndex = qualityIndex,
                audioOnly = audioOnly,
                isFullscreen = isFullscreen,
                onPlayPause = { if (exo.isPlaying) exo.pause() else exo.play() },
                onSeek = { exo.seekTo(it) },
                onSeekBy = { exo.seekTo((exo.currentPosition + it).coerceIn(0, exo.duration.coerceAtLeast(0))) },
                onSpeedChange = { speed = it },
                onQualityChange = { changeQuality(it) },
                onToggleFullscreen = onToggleFullscreen,
                onHide = { controlsVisible = false },
            )
        }

        if (showUpNext && upNext != null) {
            UpNextOverlay(
                video = upNext,
                onPlay = { showUpNext = false; onPlayNext(upNext) },
                onCancel = { showUpNext = false },
            )
        }
    }
}

@Composable
private fun ControlsOverlay(
    title: String,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    speed: Float,
    tracks: List<StreamTrack>,
    qualityIndex: Int,
    audioOnly: Boolean,
    isFullscreen: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onQualityChange: (Int) -> Unit,
    onToggleFullscreen: () -> Unit,
    onHide: () -> Unit,
) {
    var speedMenu by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0x66000000))
            .pointerInput(Unit) { detectTapGestures(onTap = { onHide() }) },
    ) {
        // עליון: כותרת + מהירות + איכות
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))

            Box {
                IconButton(onClick = { speedMenu = true }) {
                    Icon(Icons.Default.Speed, "מהירות", tint = Color.White)
                }
                DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { sp ->
                        DropdownMenuItem(
                            text = { Text("${sp}x" + if (sp == speed) "  ✓" else "") },
                            onClick = { onSpeedChange(sp); speedMenu = false },
                        )
                    }
                }
            }

            if (!audioOnly && tracks.size > 1) {
                Box {
                    IconButton(onClick = { qualityMenu = true }) {
                        Icon(Icons.Default.HighQuality, "איכות", tint = Color.White)
                    }
                    DropdownMenu(expanded = qualityMenu, onDismissRequest = { qualityMenu = false }) {
                        tracks.forEachIndexed { i, t ->
                            DropdownMenuItem(
                                text = { Text(t.label + if (i == qualityIndex) "  ✓" else "") },
                                onClick = { onQualityChange(i); qualityMenu = false },
                            )
                        }
                    }
                }
            }
        }

        // מרכז: דילוג אחורה / נגן-עצור / דילוג קדימה
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onSeekBy(-10_000) }) {
                Icon(Icons.Default.Replay10, "אחורה 10", tint = Color.White, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.width(24.dp))
            IconButton(onClick = onPlayPause, modifier = Modifier.size(64.dp)) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (isPlaying) "השהה" else "נגן",
                    tint = Color.White, modifier = Modifier.size(56.dp),
                )
            }
            Spacer(Modifier.width(24.dp))
            IconButton(onClick = { onSeekBy(10_000) }) {
                Icon(Icons.Default.Forward10, "קדימה 10", tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }

        // תחתון: זמן + מד התקדמות + מסך מלא
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(fmtTime(position), color = Color.White, fontSize = 11.sp)
            Slider(
                value = position.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(0f)),
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFF0000),
                    activeTrackColor = Color(0xFFFF0000),
                    inactiveTrackColor = Color(0x55FFFFFF),
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(fmtTime(duration), color = Color.White, fontSize = 11.sp)
            if (!audioOnly) {
                IconButton(onClick = onToggleFullscreen) {
                    Icon(
                        if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        "מסך מלא", tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun UpNextOverlay(video: Video, onPlay: () -> Unit, onCancel: () -> Unit) {
    var remaining by remember { mutableStateOf(5) }
    LaunchedEffect(Unit) {
        while (remaining > 0) { delay(1000); remaining-- }
        onPlay()
    }
    Box(modifier = Modifier.fillMaxSize().background(Color(0xE6000000)), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text("הבא בתור בעוד $remaining", color = Color(0xFFAAAAAA), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier.fillMaxWidth(0.6f).aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp)).background(Color(0xFF272727)).clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(model = video.thumbnailUrl, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(50)).background(Color(0xCCFF0000)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(video.title, color = Color.White, fontSize = 13.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.7f))
            Spacer(Modifier.height(12.dp))
            Row {
                Button(onClick = onPlay, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000))) {
                    Text("הפעל עכשיו")
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onCancel) {
                    Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("בטל")
                }
            }
        }
    }
}

private fun fmtTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    return if (m >= 60) "%d:%02d:%02d".format(m / 60, m % 60, s) else "%d:%02d".format(m, s)
}

private fun downloadVideo(context: Context, url: String, title: String) {
    try {
        val safeTitle = title.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().take(60).ifEmpty { "filtertube_video" }
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(safeTitle)
            setDescription("FilterTube — מוריד סרטון")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$safeTitle.mp4")
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)
        Toast.makeText(context, "ההורדה התחילה — בדוק בהתראות", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "שגיאה בהורדה: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
