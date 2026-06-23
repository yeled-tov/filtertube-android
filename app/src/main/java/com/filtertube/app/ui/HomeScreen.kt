package com.filtertube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
fun HomeScreen(onVideoClick: (Video) -> Unit, onSearch: () -> Unit, onSettings: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context) }
    var state by remember { mutableStateOf<HomeState>(HomeState.Loading) }
    var refreshing by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    if (showMenu) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showMenu = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(ThemeState.surface).padding(vertical = 8.dp),
            ) {
                Text("FilterTube", color = ThemeState.subtext, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 20.dp, top = 6.dp, bottom = 4.dp))
                listOf("הגדרות", "חיבור חשבון Google", "אודות").forEach { label ->
                    Text(label, color = ThemeState.text, fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth()
                            .clickable { showMenu = false; onSettings() }
                            .padding(horizontal = 20.dp, vertical = 14.dp))
                }
            }
        }
    }

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

    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        // טופ-בר מינימלי ונקי — הגדרות (עיגול) בפינה הימנית, חיפוש בשמאלית
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(50))
                    .background(ThemeState.surface).clickable { showMenu = true },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Settings, "הגדרות", tint = ThemeState.text, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, contentDescription = "חיפוש", tint = ThemeState.text)
            }
        }
        if (refreshing && state is HomeState.Success) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ThemeState.accent, trackColor = ThemeState.divider)
        }

        when (val s = state) {
            is HomeState.Loading -> CenteredLoading("טוען סרטונים...")
            is HomeState.Error -> CenteredError(s.message) { refresh(showSpinner = true) }
            is HomeState.Success -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
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
            Text(text, color = ThemeState.subtext2, fontSize = 14.sp)
        }
    }
}

@Composable
fun CenteredError(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Default.Warning, null, tint = Color(0xFFFF0000), modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(16.dp))
            Text("שגיאה בטעינה", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = ThemeState.text)
            Spacer(Modifier.height(8.dp))
            Text(message, color = ThemeState.subtext2, fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000))) {
                Text("נסה שוב")
            }
        }
    }
}

@Composable
fun VideoRow(video: Video, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 12.dp).padding(bottom = 18.dp),
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = video.title,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp)).background(ThemeState.card),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(50))
                    .background(Brush.linearGradient(listOf(ThemeState.accent, Color(0xFFFF6A5C)))),
                contentAlignment = Alignment.Center,
            ) {
                Text(video.channelName.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(video.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = ThemeState.text,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                Spacer(Modifier.height(3.dp))
                Text("${video.channelName} · ${video.timeAgoHe()}", fontSize = 12.sp, color = ThemeState.subtext,
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
