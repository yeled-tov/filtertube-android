package com.filtertube.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.filtertube.app.data.StreamData
import com.filtertube.app.data.StreamRepository
import kotlinx.coroutines.launch

sealed class PlayerState {
    data object Loading : PlayerState()
    data class Ready(val data: StreamData) : PlayerState()
    data class Error(val message: String) : PlayerState()
}

@Composable
fun PlayerScreen(
    videoId: String,
    title: String,
    channelName: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember(videoId) { mutableStateOf<PlayerState>(PlayerState.Loading) }

    LaunchedEffect(videoId) {
        scope.launch {
            state = try {
                PlayerState.Ready(StreamRepository.getStream(videoId))
            } catch (e: Exception) {
                e.printStackTrace()
                PlayerState.Error("לא הצלחנו לטעון את הסרטון: ${e.message}")
            }
        }
    }

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F)),
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(top = 24.dp, start = 4.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "חזור",
                    tint = Color.White,
                )
            }
            Text(
                text = "צפייה",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Player area (16:9)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            when (val s = state) {
                is PlayerState.Loading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFFFF0000))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "טוען נגן...",
                            color = Color(0xFFAAAAAA),
                            fontSize = 13.sp,
                        )
                    }
                }
                is PlayerState.Error -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFF0000),
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            s.message,
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                is PlayerState.Ready -> {
                    ExoPlayerHost(streamUrl = s.data.bestVideoUrl)
                }
            }
        }

        // Title + metadata
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Use the title from stream if loaded, else fallback to passed title
            val displayTitle = (state as? PlayerState.Ready)?.data?.title ?: title
            val displayChannel = (state as? PlayerState.Ready)?.data?.uploaderName ?: channelName
            val data = (state as? PlayerState.Ready)?.data

            Text(
                text = displayTitle,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = buildString {
                    append(displayChannel)
                    if (data != null && data.viewCount > 0) {
                        append(" · ")
                        append(formatViews(data.viewCount))
                    }
                },
                fontSize = 13.sp,
                color = Color(0xFFAAAAAA),
            )

            // Description (collapsible)
            data?.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Spacer(Modifier.height(16.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF1F1F1F),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "תיאור",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = desc,
                            fontSize = 12.sp,
                            color = Color(0xFFCCCCCC),
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun ExoPlayerHost(streamUrl: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val exoPlayer = remember(streamUrl) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(streamUrl))
            prepare()
            playWhenReady = true
        }
    }

    // השהה כשהאפליקציה מתמזערת, המשך כשהיא חוזרת
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                // הגדרות לעיצוב נקי ללא Branding
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun formatViews(n: Long): String {
    if (n >= 1_000_000) return "%.1fM צפיות".format(n / 1_000_000.0)
    if (n >= 1_000) return "%.1fK צפיות".format(n / 1_000.0)
    return "$n צפיות"
}
