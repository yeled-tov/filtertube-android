package com.filtertube.app.ui
import com.filtertube.app.ThemeState

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
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
import com.filtertube.app.data.LibraryStore
import com.filtertube.app.data.SubChannel
import com.filtertube.app.data.Video
import com.filtertube.app.data.YouTubeRepository

/** סרגל עליון אחיד עם כפתור חזרה לכל מסכי הפירוט. */
@Composable
fun DetailTopBar(title: String, onBack: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, start = 4.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזור", tint = ThemeState.text)
            }
            Text(title, color = ThemeState.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        HorizontalDivider(color = ThemeState.divider)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = ThemeState.subtext, fontSize = 14.sp,
            modifier = Modifier.padding(32.dp))
    }
}

/**
 * אוסף סרטונים מקומי לפי סוג: likes / ytlikes / downloads.
 * (כל קוביה בספריה פותחת את המסך הזה.)
 */
@Composable
fun CollectionScreen(type: String, onVideoClick: (Video) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { LibraryStore(context) }
    val (title, videos) = remember(type) {
        when (type) {
            "likes" -> "אהבתי" to store.likes()
            "ytlikes" -> "אהבתי ביוטיוב" to store.youtubeLikes()
            "downloads" -> "הורדות" to store.downloads()
            "history" -> "היסטוריה" to store.history()
            "recs" -> "מומלצים" to store.recommendations()
            else -> "אוסף" to emptyList()
        }
    }
    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        DetailTopBar("$title (${videos.size})", onBack)
        if (videos.isEmpty()) EmptyHint("האוסף ריק")
        else LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
            items(videos, key = { it.id }) { v -> VideoRow(v, onClick = { onVideoClick(v) }) }
        }
    }
}

/** רשימת כל המנויים של המשתמש מיוטיוב. לחיצה פותחת את סרטוני הערוץ. */
@Composable
fun SubscriptionsScreen(onOpenChannel: (String, String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { LibraryStore(context) }
    val subs = remember { store.subscriptions() }
    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        DetailTopBar("המנויים שלי (${subs.size})", onBack)
        if (subs.isEmpty()) EmptyHint("התחבר לחשבון גוגל בספריה כדי למשוך את המנויים שלך")
        else LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
            items(subs, key = { it.channelId }) { sub -> SubRow(sub) { onOpenChannel(sub.channelId, sub.title) } }
        }
    }
}

@Composable
private fun SubRow(sub: SubChannel, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (sub.thumbnailUrl.isNotEmpty()) {
            AsyncImage(
                model = sub.thumbnailUrl,
                contentDescription = sub.title,
                modifier = Modifier.size(48.dp).clip(CircleShape).background(ThemeState.divider),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(channelColor(sub.title)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, null, tint = ThemeState.text)
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(sub.title, color = ThemeState.text, fontSize = 15.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** סרטוני ערוץ בודד (נמשך ב-RSS, מהיר). */
@Composable
fun ChannelVideosScreen(
    channelId: String,
    channelName: String,
    onVideoClick: (Video) -> Unit,
    onBack: () -> Unit,
) {
    var state by remember { mutableStateOf<HomeState>(HomeState.Loading) }
    var retry by remember { mutableStateOf(0) }
    LaunchedEffect(channelId, retry) {
        state = HomeState.Loading
        state = try {
            val videos = YouTubeRepository.fetchChannelVideos(channelId, channelName)
            if (videos.isEmpty()) HomeState.Error("אין סרטונים להצגה") else HomeState.Success(videos)
        } catch (e: Exception) {
            HomeState.Error(e.message ?: "שגיאה בטעינה")
        }
    }
    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        DetailTopBar(channelName, onBack)
        when (val s = state) {
            is HomeState.Loading -> CenteredLoading("טוען סרטונים...")
            is HomeState.Error -> CenteredError(s.message) { retry++ }
            is HomeState.Success -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
                items(s.videos, key = { it.id }) { v -> VideoRow(v, onClick = { onVideoClick(v) }) }
            }
        }
    }
}
