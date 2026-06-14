package com.filtertube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.filtertube.app.data.ChannelsRepository
import com.filtertube.app.data.FeedCache
import com.filtertube.app.data.SettingsStore
import com.filtertube.app.data.StreamRepository
import com.filtertube.app.data.Video
import com.filtertube.app.data.YouTubeRepository
import com.filtertube.app.data.forLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ShortsState {
    data object Loading : ShortsState()
    data class Success(val videos: List<Video>) : ShortsState()
    data class Error(val message: String) : ShortsState()
}

@Composable
fun ShortsScreen(onVideoClick: (Video) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context) }
    var state by remember { mutableStateOf<ShortsState>(ShortsState.Loading) }

    fun refresh(showSpinner: Boolean) {
        if (showSpinner) state = ShortsState.Loading
        scope.launch {
            try {
                val channels = ChannelsRepository.getChannels(context).forLevel(settings.filterLevel)
                val shorts = YouTubeRepository.fetchShorts(channels)
                if (shorts.isNotEmpty()) {
                    FeedCache.saveShorts(context, shorts)
                    state = ShortsState.Success(shorts)
                } else if (state !is ShortsState.Success) {
                    state = ShortsState.Error("לא נמצאו Shorts מהערוצים המאושרים")
                }
            } catch (e: Exception) {
                if (state !is ShortsState.Success) state = ShortsState.Error(e.message ?: "שגיאה")
            }
        }
    }

    LaunchedEffect(Unit) {
        val cached = FeedCache.loadShorts(context)
        if (!cached.isNullOrEmpty()) state = ShortsState.Success(cached)
        refresh(showSpinner = cached.isNullOrEmpty())
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (val s = state) {
            is ShortsState.Loading -> CenteredLoading("טוען Shorts...")
            is ShortsState.Error -> CenteredError(s.message) { refresh(showSpinner = true) }
            is ShortsState.Success -> ShortsPager(s.videos)
        }

        IconButton(
            onClick = { refresh(showSpinner = false) },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 8.dp),
        ) {
            Icon(Icons.Default.Refresh, "רענן", tint = Color.White)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ShortsPager(videos: List<Video>) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { videos.size })

    val exo = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { exo.release() } }

    var loading by remember { mutableStateOf(true) }

    // טוען ומנגן את הסרטון של העמוד הנוכחי
    LaunchedEffect(pagerState.currentPage, videos) {
        val video = videos.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        loading = true
        val url = withContext(Dispatchers.IO) {
            runCatching {
                val data = StreamRepository.getStream(video.id)
                data.tracks.firstOrNull { it.audioUrl == null }?.videoUrl ?: data.bestVideoUrl
            }.getOrNull()
        }
        if (url != null) {
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.prepare()
            exo.playWhenReady = true
        }
        loading = false
    }

    VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
        ShortPage(
            video = videos[page],
            isActive = page == pagerState.currentPage,
            loading = loading && page == pagerState.currentPage,
            player = exo,
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ShortPage(video: Video, isActive: Boolean, loading: Boolean, player: ExoPlayer) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = video.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        if (isActive) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize(),
            )
            if (loading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
        }

        // מידע על הסרטון
        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp),
        ) {
            Text(video.channelName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(video.title, color = Color.White, fontSize = 13.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
        }
    }
}
