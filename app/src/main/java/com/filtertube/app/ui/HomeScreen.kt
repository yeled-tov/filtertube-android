package com.filtertube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
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
import coil.compose.AsyncImage
import com.filtertube.app.ThemeState
import com.filtertube.app.data.ChannelsRepository
import com.filtertube.app.data.FeedCache
import com.filtertube.app.data.SettingsStore
import com.filtertube.app.data.Video
import com.filtertube.app.data.YouTubeRepository
import com.filtertube.app.data.forLevel
import kotlinx.coroutines.launch

sealed class HomeState {
    data object Loading : HomeState()
    data class Success(val videos: List<Video>) : HomeState()
    data class Error(val message: String) : HomeState()
}

@Composable
fun HomeScreen(onVideoClick: (Video) -> Unit, onSearch: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context) }
    var state by remember { mutableStateOf<HomeState>(HomeState.Loading) }
    var refreshing by remember { mutableStateOf(false) }

    fun refresh(showSpinner: Boolean) {
        if (showSpinner) state = HomeState.Loading
        refreshing = true
        scope.launch {
            try {
                val channels = ChannelsRepository.getChannels(context).forLevel(settings.filterLevel)
                val videos = YouTubeRepository.fetchAllChannelsFeed(channels)
                if (videos.isNotEmpty()) {
                    FeedCache.saveFeed(context, videos)
                    state = HomeState.Success(videos)
                } else if (state !is HomeState.Success) {
                    state = HomeState.Error("לא נמצאו סרטונים בערוצים המאושרים")
                }
            } catch (e: Exception) {
                if (state !is HomeState.Success) state = HomeState.Error(e.message ?: "שגיאה")
            } finally {
                refreshing = false
            }
        }
    }

    // טעינה מיידית מהקאש (אם יש), ואז רענון ברקע
    LaunchedEffect(Unit) {
        val cached = FeedCache.loadFeed(context)
        if (!cached.isNullOrEmpty()) state = HomeState.Success(cached)
        refresh(showSpinner = cached.isNullOrEmpty())
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(ThemeState.accent),
                contentAlignment = Alignment.Center,
            ) { Text("FT", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(8.dp))
            Text("FilterTube", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
            IconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, contentDescription = "חיפוש", tint = Color.White)
            }
        }
        if (refreshing && state is HomeState.Success) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ThemeState.accent, trackColor = Color(0xFF272727))
        }
        HorizontalDivider(color = Color(0xFF272727))

        when (val s = state) {
            is HomeState.Loading -> CenteredLoading("טוען סרטונים...")
            is HomeState.Error -> CenteredError(s.message) { refresh(showSpinner = true) }
            is HomeState.Success -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(s.videos, key = { it.id }) { video ->
                    VideoRow(video, onClick = { onVideoClick(video) })
                }
            }
        }
    }
}

@Composable
fun CenteredLoading(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFFFF0000))
            Spacer(Modifier.height(16.dp))
            Text(text, color = Color(0xFFAAAAAA), fontSize = 14.sp)
        }
    }
}

@Composable
fun CenteredError(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Default.Warning, null, tint = Color(0xFFFF0000), modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text("שגיאה בטעינה", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(message, color = Color(0xFFAAAAAA), fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000))) {
                Text("נסה שוב")
            }
        }
    }
}

@Composable
fun VideoRow(video: Video, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(bottom = 16.dp)) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = video.title,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF272727)),
            contentScale = ContentScale.Crop,
        )
        Row(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp)) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(channelColor(video.channelName)),
                contentAlignment = Alignment.Center,
            ) {
                Text(video.channelName.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(video.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                Spacer(Modifier.height(4.dp))
                Text("${video.channelName} · ${video.timeAgoHe()}", fontSize = 12.sp, color = Color(0xFFAAAAAA),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

fun channelColor(name: String): Color {
    val colors = listOf(
        Color(0xFFFF0000), Color(0xFF3B82F6), Color(0xFF10B981),
        Color(0xFFF59E0B), Color(0xFFA855F7), Color(0xFFEC4899), Color(0xFF14B8A6),
    )
    val hash = name.fold(0) { acc, c -> (acc * 31 + c.code) }
    return colors[Math.floorMod(hash, colors.size)]
}
