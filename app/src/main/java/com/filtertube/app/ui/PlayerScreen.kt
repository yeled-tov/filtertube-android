package com.filtertube.app.ui

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.filtertube.app.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class PlayerState {
    data object Loading : PlayerState()
    data class Ready(val data: StreamData, val audioOnly: Boolean, val upNext: Video?) : PlayerState()
    data class Error(val message: String) : PlayerState()
}

@Composable
fun PlayerScreen(
    videoId: String,
    title: String,
    channelName: String,
    onBack: () -> Unit,
    onPlayNext: (Video) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    var state by remember(videoId) { mutableStateOf<PlayerState>(PlayerState.Loading) }

    LaunchedEffect(videoId) {
        scope.launch {
            state = try {
                val data = StreamRepository.getStream(videoId)
                val channels = ChannelsRepository.getChannels(context)
                val catById = channels.associate { it.youtubeChannelId to it.category }
                val allowed = channels.map { it.youtubeChannelId }.toHashSet()

                val isMusic = catById[data.channelId] == "music"
                val audioOnly = settings.filterLevel == 1 && isMusic

                // הבא בתור — סרטון קשור ראשון מהרשימה הלבנה
                val upNext = data.related.firstOrNull { it.channelId in allowed && it.id != videoId }

                PlayerState.Ready(data, audioOnly, upNext)
            } catch (e: Exception) {
                PlayerState.Error("לא הצלחנו לטעון את הסרטון: ${e.message}")
            }
        }
    }

    BackHandler(onBack = onBack)

    var showUpNext by remember(videoId) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F))) {
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

        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black),
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
                is PlayerState.Ready -> {
                    val playUrl = if (s.audioOnly) (s.data.bestAudioUrl ?: s.data.bestVideoUrl) else s.data.bestVideoUrl
                    MediaPlayerHost(
                        streamUrl = playUrl,
                        audioOnly = s.audioOnly,
                        thumbnailUrl = s.data.thumbnailUrl,
                        onEnded = { if (s.upNext != null) showUpNext = true },
                    )
                    // Up Next overlay
                    if (showUpNext && s.upNext != null) {
                        UpNextOverlay(
                            video = s.upNext,
                            onPlay = { showUpNext = false; onPlayNext(s.upNext) },
                            onCancel = { showUpNext = false },
                        )
                    }
                }
            }
        }

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

/** נגן וידאו או אודיו (עם album art) לפי audioOnly */
@OptIn(UnstableApi::class)
@Composable
private fun MediaPlayerHost(
    streamUrl: String,
    audioOnly: Boolean,
    thumbnailUrl: String?,
    onEnded: () -> Unit,
) {
    val context = LocalContext.current
    val currentOnEnded by rememberUpdatedState(onEnded)

    val exoPlayer = remember(streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(streamUrl) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) currentOnEnded()
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener); exoPlayer.release() }
    }

    if (audioOnly) {
        // מצב אודיו — תמונת אלבום במקום וידאו
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier.size(140.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF272727)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (thumbnailUrl != null) {
                        AsyncImage(model = thumbnailUrl, contentDescription = null,
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
                    player = exoPlayer
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
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
