package com.filtertube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.filtertube.app.data.SupabaseClient
import com.filtertube.app.data.Video
import com.filtertube.app.data.YouTubeRepository
import kotlinx.coroutines.launch

sealed class HomeState {
    data object Loading : HomeState()
    data class Success(val videos: List<Video>) : HomeState()
    data class Error(val message: String) : HomeState()
}

@Composable
fun HomeScreen(
    onVideoClick: (Video) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<HomeState>(HomeState.Loading) }

    fun load() {
        state = HomeState.Loading
        scope.launch {
            state = try {
                val channels = SupabaseClient.fetchChannels()
                val videos = YouTubeRepository.fetchAllChannelsFeed(channels)
                if (videos.isEmpty()) {
                    HomeState.Error("לא נמצאו סרטונים בערוצים המאושרים")
                } else {
                    HomeState.Success(videos)
                }
            } catch (e: Exception) {
                HomeState.Error(e.message ?: "שגיאה")
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F))) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFF0000)),
                contentAlignment = Alignment.Center,
            ) {
                Text("FT", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "FilterTube",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = ::load) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "רענן",
                    tint = Color.White,
                )
            }
        }

        HorizontalDivider(color = Color(0xFF272727))

        when (val s = state) {
            is HomeState.Loading -> LoadingState()
            is HomeState.Error -> ErrorState(s.message, ::load)
            is HomeState.Success -> VideoFeed(s.videos, onVideoClick)
        }
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFFFF0000))
            Spacer(Modifier.height(16.dp))
            Text("טוען סרטונים...", color = Color(0xFFAAAAAA), fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "ייתכן וייקח כמה שניות בפעם הראשונה",
                color = Color(0xFF666666),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFF0000),
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text("שגיאה בטעינה", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(message, color = Color(0xFFAAAAAA), fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000)),
            ) { Text("נסה שוב") }
        }
    }
}

@Composable
private fun VideoFeed(videos: List<Video>, onVideoClick: (Video) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(videos, key = { it.id }) { video ->
            VideoCard(video, onClick = { onVideoClick(video) })
        }
    }
}

@Composable
private fun VideoCard(video: Video, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(bottom = 16.dp),
    ) {
        // Thumbnail בגודל מלא, יחס 16:9 (כמו YouTube)
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = video.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color(0xFF272727)),
            contentScale = ContentScale.Crop,
        )
        // טקסט מתחת ל-thumbnail
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp),
        ) {
            // אווטר עגול של הערוץ
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(channelColor(video.channelName)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = video.channelName.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${video.channelName} · ${video.timeAgoHe()}",
                    fontSize = 12.sp,
                    color = Color(0xFFAAAAAA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun channelColor(name: String): Color {
    val colors = listOf(
        Color(0xFFFF0000), Color(0xFF3B82F6), Color(0xFF10B981),
        Color(0xFFF59E0B), Color(0xFFA855F7), Color(0xFFEC4899), Color(0xFF14B8A6),
    )
    val hash = name.fold(0) { acc, c -> (acc * 31 + c.code) }
    return colors[Math.floorMod(hash, colors.size)]
}
