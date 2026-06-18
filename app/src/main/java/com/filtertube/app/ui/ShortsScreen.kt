package com.filtertube.app.ui
import com.filtertube.app.ThemeState

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
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

/** מחזיק את רשימת ה-Shorts הנוכחית כדי להעביר אותה למסך הנגן המלא ללא ניווט מסובך. */
object ShortsHolder {
    var videos: List<Video> = emptyList()
    var startIndex: Int = 0
}

sealed class ShortsState {
    data object Loading : ShortsState()
    data class Success(val videos: List<Video>) : ShortsState()
    data class Error(val message: String) : ShortsState()
}

/** טאב Shorts — גריד של תמונות ממוזערות. לחיצה פותחת את הנגן המלא בסגנון טיקטוק. */
@Composable
fun ShortsScreen(onOpenShort: () -> Unit, onSearch: () -> Unit) {
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

    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 28.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Shorts", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ThemeState.text, modifier = Modifier.weight(1f))
            IconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, "חיפוש", tint = ThemeState.text)
            }
        }
        HorizontalDivider(color = ThemeState.divider)

        when (val s = state) {
            is ShortsState.Loading -> CenteredLoading("טוען Shorts...")
            is ShortsState.Error -> CenteredError(s.message) { refresh(showSpinner = true) }
            is ShortsState.Success -> ShortsGrid(s.videos) { index ->
                ShortsHolder.videos = s.videos
                ShortsHolder.startIndex = index
                onOpenShort()
            }
        }
    }
}

@Composable
private fun ShortsGrid(videos: List<Video>, onClick: (Int) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 96.dp),
    ) {
        items(videos, key = { it.id }) { video ->
            val index = videos.indexOf(video)
            Box(
                modifier = Modifier.padding(2.dp).aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(8.dp)).background(ThemeState.card)
                    .clickable { onClick(index) },
            ) {
                AsyncImage(
                    model = video.thumbnailUrl,
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(Color(0xAA000000)).padding(horizontal = 6.dp, vertical = 4.dp)) {
                    Text(video.channelName, color = ThemeState.text, fontSize = 10.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** מסך מלא בסגנון טיקטוק — פיג'ר אנכי עם בקרי נגן. ללא סרגל תחתון. */
@OptIn(UnstableApi::class)
@Composable
fun ShortsPlayerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val videos = remember { ShortsHolder.videos }
    if (videos.isEmpty()) { onBack(); return }

    val pagerState = rememberPagerState(initialPage = ShortsHolder.startIndex, pageCount = { videos.size })
    val exo = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { exo.release() } }

    var loading by remember { mutableStateOf(true) }
    val urlCache = remember { mutableMapOf<String, String>() }
    val prefetchScope = rememberCoroutineScope()

    suspend fun resolveUrl(video: Video): String? {
        urlCache[video.id]?.let { return it }
        val url = withContext(Dispatchers.IO) {
            runCatching {
                val data = StreamRepository.getStream(video.id)
                data.tracks.firstOrNull { it.audioUrl == null }?.videoUrl ?: data.bestVideoUrl
            }.getOrNull()
        }
        if (url != null) urlCache[video.id] = url
        return url
    }

    // טוען ומנגן את הסרטון של העמוד הנוכחי, ומקדים את הבא
    LaunchedEffect(pagerState.currentPage) {
        val page = pagerState.currentPage
        val video = videos.getOrNull(page) ?: return@LaunchedEffect
        loading = urlCache[video.id] == null
        val url = resolveUrl(video)
        if (url != null) {
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.prepare()
            exo.playWhenReady = true
        }
        loading = false
        // טעינה מקדימה של השורט הבא — כך ההחלקה הבאה מיידית
        videos.getOrNull(page + 1)?.let { next -> prefetchScope.launch { resolveUrl(next) } }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            ShortPage(
                video = videos[page],
                isActive = page == pagerState.currentPage,
                loading = loading && page == pagerState.currentPage,
                player = exo,
            )
        }
        // כפתור חזרה לגריד
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(top = 28.dp, start = 4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזור", tint = ThemeState.text)
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun ShortPage(video: Video, isActive: Boolean, loading: Boolean, player: ExoPlayer) {
    var controlsVisible by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var boxWidth by remember { mutableStateOf(1) }

    // בקרים נעלמים אחרי 2.5 שניות בזמן ניגון
    LaunchedEffect(controlsVisible, isPlaying, isActive) {
        if (isActive && controlsVisible && isPlaying) {
            kotlinx.coroutines.delay(2500); controlsVisible = false
        }
    }
    LaunchedEffect(feedback) { if (feedback != null) { kotlinx.coroutines.delay(700); feedback = null } }

    // עדכון פס ההתקדמות
    LaunchedEffect(isActive) {
        while (isActive) {
            isPlaying = player.isPlaying
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.coerceAtLeast(0)
            kotlinx.coroutines.delay(300)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black)
            .onSizeChanged { boxWidth = it.width.coerceAtLeast(1) }
            .pointerInput(isActive) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        if (offset.x < boxWidth / 2f) {
                            player.seekTo((player.currentPosition - 5_000).coerceAtLeast(0)); feedback = "⏪ 5 ש׳"
                        } else {
                            player.seekTo(player.currentPosition + 5_000); feedback = "5 ש׳ ⏩"
                        }
                    },
                    onTap = {
                        if (player.isPlaying) player.pause() else player.play()
                        isPlaying = player.isPlaying
                        controlsVisible = true
                    },
                )
            },
    ) {
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
            if (loading) CircularProgressIndicator(color = ThemeState.text, modifier = Modifier.align(Alignment.Center))
        }

        // כפתור פליי/עצור במרכז — מופיע כשהבקרים גלויים
        if (controlsVisible && !loading) {
            Box(
                modifier = Modifier.size(64.dp).align(Alignment.Center).clip(RoundedCornerShape(50))
                    .background(Color(0x88000000))
                    .clickable {
                        if (player.isPlaying) player.pause() else player.play()
                        isPlaying = player.isPlaying; controlsVisible = true
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null,
                    tint = ThemeState.text, modifier = Modifier.size(36.dp))
            }
        }

        feedback?.let {
            Text(it, color = ThemeState.text, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center).clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC000000)).padding(horizontal = 16.dp, vertical = 8.dp))
        }

        // מידע + פס התקדמות בתחתית
        Column(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp)) {
            Text(video.channelName, color = ThemeState.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(video.title, color = ThemeState.text, fontSize = 13.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f },
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFF0000),
                trackColor = Color(0x55FFFFFF),
            )
        }
    }
}
