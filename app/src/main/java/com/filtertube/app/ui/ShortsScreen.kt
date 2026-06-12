package com.filtertube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import com.filtertube.app.data.ChannelsRepository
import com.filtertube.app.data.Video
import com.filtertube.app.data.YouTubeRepository
import kotlinx.coroutines.launch

sealed class ShortsState {
    data object Loading : ShortsState()
    data class Success(val videos: List<Video>) : ShortsState()
    data class Error(val message: String) : ShortsState()
}

@Composable
fun ShortsScreen(onVideoClick: (Video) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ShortsState>(ShortsState.Loading) }

    fun load() {
        state = ShortsState.Loading
        scope.launch {
            state = try {
                val channels = ChannelsRepository.getChannels(context)
                val shorts = YouTubeRepository.fetchShorts(channels)
                if (shorts.isEmpty()) ShortsState.Error("לא נמצאו Shorts מהערוצים המאושרים")
                else ShortsState.Success(shorts)
            } catch (e: Exception) {
                ShortsState.Error(e.message ?: "שגיאה")
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.PlayArrow, null, tint = Color(0xFFFF0000), modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Text("Shorts", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
            IconButton(onClick = ::load) { Icon(Icons.Default.Refresh, "רענן", tint = Color.White) }
        }
        HorizontalDivider(color = Color(0xFF272727))

        when (val s = state) {
            is ShortsState.Loading -> CenteredLoading("טוען Shorts...")
            is ShortsState.Error -> CenteredError(s.message, ::load)
            is ShortsState.Success -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp),
            ) {
                items(s.videos, key = { it.id }) { video ->
                    ShortCard(video, onClick = { onVideoClick(video) })
                }
            }
        }
    }
}

@Composable
private fun ShortCard(video: Video, onClick: () -> Unit) {
    Column(modifier = Modifier.padding(4.dp).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f)
                .clip(RoundedCornerShape(12.dp)).background(Color(0xFF272727)),
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(video.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White,
            maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 15.sp)
        Text(video.channelName, fontSize = 11.sp, color = Color(0xFF888888),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
