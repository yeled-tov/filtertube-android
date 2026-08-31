package com.filtertube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import com.filtertube.app.data.Channel
import com.filtertube.app.data.ChannelsRepository
import com.filtertube.app.data.BugReport
import com.filtertube.app.data.DownloadEngine
import com.filtertube.app.data.FeedCache
import com.filtertube.app.data.LibraryStore
import com.filtertube.app.data.SettingsStore
import com.filtertube.app.data.Video
import com.filtertube.app.data.YouTubeRepository
import com.filtertube.app.playback.Playback
import com.filtertube.app.data.categoryLabelHe
import com.filtertube.app.data.forLevel
import com.filtertube.app.data.personalizeFeed
import com.filtertube.app.data.sortedCategories
import kotlinx.coroutines.launch

sealed class HomeState {
    data object Loading : HomeState()
    data class Success(val videos: List<Video>) : HomeState()
    data class Error(val message: String) : HomeState()
}

@Composable
fun HomeScreen(
    onVideoClick: (Video) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit = {},
    onAccount: () -> Unit = {},
    onInbox: () -> Unit = {},
    onChannels: () -> Unit = {},
    onLive: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context) }
    val store = remember { LibraryStore(context) }
    var state by remember { mutableStateOf<HomeState>(HomeState.Loading) }
    var refreshing by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    val newCount = remember { store.newVideos().size }   // מספר הסרטונים החדשים לתג הפעמון

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("סגור") } },
            title = { Text("FilterTube") },
            text = {
                Text(
                    "פלטפורמת וידאו מסוננת — מציגה אך ורק ערוצים מאושרים. " +
                        "כל התוכן מסונן לפי רמת הסינון שנבחרה.\n\nגרסה ${com.filtertube.app.BuildConfig.VERSION_NAME}",
                    color = ThemeState.subtext2, fontSize = 13.sp, lineHeight = 18.sp,
                )
            },
            containerColor = ThemeState.surface,
            titleContentColor = ThemeState.text, textContentColor = ThemeState.text,
        )
    }

    fun refresh(showSpinner: Boolean) {
        if (showSpinner) state = HomeState.Loading
        refreshing = true
        scope.launch {
            try {
                val chans = ChannelsRepository.getChannels(context).forLevel(settings.filterLevel, settings.userGender)
                channels = chans
                val videos = YouTubeRepository.fetchAllChannelsFeed(chans)
                if (videos.isNotEmpty()) {
                    FeedCache.saveFeed(context, videos)   // שומרים גולמי; מתאימים אישית בתצוגה
                    state = HomeState.Success(sanitizeFeed(personalizeFeed(videos, store.localHistory())))
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
        runCatching { channels = ChannelsRepository.getChannels(context).forLevel(settings.filterLevel, settings.userGender) }
        val cached = FeedCache.loadFeed(context)
        if (!cached.isNullOrEmpty()) {
            state = HomeState.Success(sanitizeFeed(personalizeFeed(cached, store.localHistory())))
        }
        refresh(showSpinner = cached.isNullOrEmpty())
    }

    Box(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        // התוכן הראשי — מטושטש כשהתפריט הצף פתוח (אפקט זכוכית)
        Column(modifier = Modifier.fillMaxSize().blur(if (showMenu) 18.dp else 0.dp)) {
            // טופ-בר מרווח — כפתורי הפעולה אינם נחתכים גם במכשירים עם אזור מצלמה גדול.
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(50))
                        .background(Brush.linearGradient(ThemeState.accentColors))
                        .clickable { showMenu = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Person, "תפריט", tint = Color.White, modifier = Modifier.size(23.dp)) }
                Spacer(Modifier.width(10.dp))
                // פעמון "סרטונים חדשים" עם נקודה אדומה אם יש חדשים
                Box(
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(50))
                        .background(ThemeState.surface).clickable { onInbox() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Notifications, "סרטונים חדשים", tint = ThemeState.text, modifier = Modifier.size(23.dp))
                    if (newCount > 0) {
                        Box(modifier = Modifier.align(Alignment.TopEnd).padding(2.dp).size(9.dp)
                            .clip(RoundedCornerShape(50)).background(Color(0xFFFF3B30)))
                    }
                }
                Spacer(Modifier.width(10.dp))
                // שידורים חיים
                Box(
                    modifier = Modifier.size(42.dp).clip(RoundedCornerShape(50))
                        .background(ThemeState.surface).clickable { onLive() },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.LiveTv, "שידורים חיים", tint = Color(0xFFFF3B30), modifier = Modifier.size(22.dp)) }
                Spacer(Modifier.weight(1f))
                Text("Filter Tube", color = ThemeState.text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            }
            // טאבים של קטגוריות (כמו ביוטיוב) — לחיצה מסננת את הפיד לפי תחום
            if (channels.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CategoryChip("הכל", selectedCategory == null) { selectedCategory = null }
                    sortedCategories(channels.map { it.category }).forEach { cat ->
                        CategoryChip(categoryLabelHe(cat), selectedCategory == cat) { selectedCategory = cat }
                    }
                }
            }
            if (refreshing && state is HomeState.Success) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = ThemeState.accent, trackColor = ThemeState.divider)
            }

            when (val s = state) {
                is HomeState.Loading -> CenteredLoading("טוען סרטונים...")
                is HomeState.Error -> CenteredError(s.message) { refresh(showSpinner = true) }
                is HomeState.Success -> {
                    val catByChannel = channels.associate { it.youtubeChannelId to it.category }
                    val displayed = if (selectedCategory == null) s.videos
                        else s.videos.filter { catByChannel[it.channelId] == selectedCategory }
                    if (displayed.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("אין סרטונים בקטגוריה זו", color = ThemeState.subtext, fontSize = 14.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
                        ) {
                            items(displayed, key = { it.id }) { video ->
                                VideoRow(video, onClick = { onVideoClick(video) })
                            }
                        }
                    }
                }
            }
        }

        // תפריט צף בסגנון זכוכית — scrim כהה מעל הרקע המטושטש + כרטיס שקוף-למחצה
        if (showMenu) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { showMenu = false },
            )
            Column(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 28.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(ThemeState.surface.copy(alpha = 0.75f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
                    .padding(vertical = 10.dp),
            ) {
                Text("FilterTube", color = ThemeState.subtext, fontSize = 12.sp,
                    modifier = Modifier.padding(start = 22.dp, top = 8.dp, bottom = 6.dp))
                val menuItems = listOf<Pair<String, () -> Unit>>(
                    "ערוצים — עקוב" to { showMenu = false; onChannels() },
                    "הגדרות" to { showMenu = false; onSettings() },
                    "חיבור חשבון YouTube (אופציונלי)" to { showMenu = false; onAccount() },
                    "אודות" to { showMenu = false; showAbout = true },
                )
                menuItems.forEach { (label, action) ->
                    Text(label, color = ThemeState.text, fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth()
                            .clickable { action() }
                            .padding(horizontal = 22.dp, vertical = 15.dp))
                }
            }
        }
    }
}

/** A stale cache or a repeated upstream item must never create duplicate LazyColumn keys. */
private fun sanitizeFeed(videos: List<Video>): List<Video> =
    videos.asSequence()
        .filter { it.id.isNotBlank() }
        .distinctBy { it.id }
        .toList()

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .then(
                if (selected) Modifier.background(
                    Brush.horizontalGradient(ThemeState.accentColors),
                ) else Modifier.background(ThemeState.surface),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) Color.White else ThemeState.subtext2,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
fun CenteredLoading(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = ThemeState.accent)
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
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = ThemeState.accent)) {
                Text("נסה שוב")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoRow(video: Video, onClick: () -> Unit) {
    var showActions by remember(video.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onClick,
            onLongClick = { showActions = true },
        )
            .padding(horizontal = 12.dp).padding(bottom = 18.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp)).background(ThemeState.card),
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // ברק עדין מלמעלה לעומק
            Box(
                modifier = Modifier.matchParentSize().background(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.06f), Color.Transparent, Color.Black.copy(alpha = 0.12f))),
                ),
            )
            // באדג' "מאושר" (כל הסרטונים מערוצים מאושרים)
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(9.dp)
                    .clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(alpha = 0.5f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Check, null, tint = Color(0xFF7CF2C0), modifier = Modifier.size(11.dp))
                Spacer(Modifier.width(4.dp))
                Text("מאושר", color = Color(0xFF7CF2C0), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.size(34.dp).clip(RoundedCornerShape(50))
                    .background(Brush.linearGradient(ThemeState.accentColors)),
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
    if (showActions) VideoActionMenu(video, onDismiss = { showActions = false })
}

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun VideoActionMenu(video: Video, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { LibraryStore(context) }
    var playlistOpen by remember { mutableStateOf(false) }
    var reportOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("פעולות לסרטון", color = ThemeState.text) },
        text = {
            Column {
                VideoAction("הבא בתור", Icons.Default.QueueMusic) {
                    busy = true
                    scope.launch {
                        val immediate = Playback.enqueueNext(context, video)
                        busy = false; onDismiss()
                        android.widget.Toast.makeText(context, if (immediate) "נוסף לתור הבא" else "נשמר לתור הבא", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                VideoAction("הורד סרטון", Icons.Default.Download) {
                    busy = true
                    scope.launch {
                        val ok = DownloadEngine.enqueueByVideo(context, video, isAudio = false)
                        busy = false; onDismiss()
                        android.widget.Toast.makeText(context, if (ok) "ההורדה התחילה" else "לא ניתן להתחיל הורדה", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                val liked = store.isLiked(video.id)
                VideoAction(if (liked) "הסר מסרטונים שאהבתי" else "הוסף לסרטונים שאהבתי", if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder) {
                    store.toggleLike(video); onDismiss()
                }
                VideoAction("שתף סרטון", Icons.Default.Share) {
                    val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, "https://www.youtube.com/watch?v=${video.id}")
                    }
                    context.startActivity(android.content.Intent.createChooser(share, "שתף סרטון")); onDismiss()
                }
                VideoAction("הוסף לפלייליסט", Icons.Default.PlaylistAdd) { playlistOpen = true }
                VideoAction("דווח על הסרטון", Icons.Default.Flag) { reportOpen = true }
                VideoAction("הסר סרטון", Icons.Default.Delete) {
                    store.removeVideo(video); onDismiss()
                    android.widget.Toast.makeText(context, "הסרטון הוסר מהספרייה", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("ביטול") } },
    )
    if (playlistOpen) PlaylistPicker(video, store, onDismiss = { playlistOpen = false })
    if (reportOpen) ReportVideoDialog(video, onDismiss = { reportOpen = false })
}

@Composable
private fun VideoAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null, tint = ThemeState.accent)
        Spacer(Modifier.width(12.dp))
        Text(label, color = ThemeState.text, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PlaylistPicker(video: Video, store: LibraryStore, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var newName by remember { mutableStateOf("") }
    val playlists by remember { mutableStateOf(store.playlists()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("הוסף לפלייליסט", color = ThemeState.text) },
        text = {
            Column {
                OutlinedTextField(newName, { newName = it }, label = { Text("פלייליסט חדש") }, singleLine = true)
                playlists.forEach { playlist ->
                    TextButton(onClick = { store.addToPlaylist(playlist.name, video); onDismiss(); android.widget.Toast.makeText(context, "נוסף לפלייליסט", android.widget.Toast.LENGTH_SHORT).show() }, modifier = Modifier.fillMaxWidth()) {
                        Text(playlist.name, color = ThemeState.text, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (newName.isNotBlank()) { store.createPlaylist(newName); store.addToPlaylist(newName.trim(), video); onDismiss() } }) { Text("צור והוסף") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("ביטול") } },
    )
}

@Composable
private fun ReportVideoDialog(video: Video, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("דיווח על סרטון", color = ThemeState.text) },
        text = { OutlinedTextField(reason, { reason = it }, label = { Text("מה הבעיה בסרטון?") }, minLines = 3) },
        confirmButton = {
            TextButton(enabled = !sending && reason.isNotBlank(), onClick = {
                sending = true
                scope.launch {
                    val ok = BugReport.submit("דיווח על סרטון ${video.id}\nכותרת: ${video.title}", reason.trim())
                    sending = false; onDismiss()
                    android.widget.Toast.makeText(context, if (ok) "הדיווח נשלח" else "שליחת הדיווח נכשלה", android.widget.Toast.LENGTH_SHORT).show()
                }
            }) { Text(if (sending) "שולח…" else "שלח") }
        },
        dismissButton = { TextButton(enabled = !sending, onClick = onDismiss) { Text("ביטול") } },
    )
}

fun channelColor(name: String): Color {
    val colors = listOf(
        Color(0xFFFF0000), Color(0xFF3B82F6), Color(0xFF10B981),
        Color(0xFFF59E0B), Color(0xFFA855F7), Color(0xFFEC4899), Color(0xFF14B8A6),
    )
    val hash = name.fold(0) { acc, c -> (acc * 31 + c.code) }
    return colors[Math.floorMod(hash, colors.size)]
}
