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
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
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
import com.filtertube.app.data.Diagnostics
import com.filtertube.app.data.BugReport
import com.filtertube.app.data.DownloadEngine
import com.filtertube.app.data.FeedCache
import com.filtertube.app.data.LibraryBadges
import com.filtertube.app.data.LibraryStore
import com.filtertube.app.data.SettingsStore
import com.filtertube.app.data.StreamRepository
import com.filtertube.app.data.Video
import com.filtertube.app.data.VideoMetadata
import com.filtertube.app.data.YouTubeRepository
import com.filtertube.app.playback.Playback
import com.filtertube.app.data.categoryLabelHe
import com.filtertube.app.data.forLevel
import com.filtertube.app.data.personalizeFeed
import com.filtertube.app.data.sortedCategories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class HomeState {
    data object Loading : HomeState()
    data class Success(val videos: List<Video>) : HomeState()
    data class Error(val message: String) : HomeState()
}

@Composable
fun HomeScreen(
    onVideoClick: (Video) -> Unit,
    onSearch: () -> Unit,
    onInbox: () -> Unit = {},
    onLive: () -> Unit = {},
    onStartRadio: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context) }
    val store = remember { LibraryStore(context) }
    var state by remember { mutableStateOf<HomeState>(HomeState.Loading) }
    var refreshing by remember { mutableStateOf(false) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    /** true בזמן שנבחר סרטון הפתיחה לרדיו — מונע לחיצה כפולה. */
    var radioStarting by remember { mutableStateOf(false) }
    val newCount = remember { store.newVideos().size }   // מספר הסרטונים החדשים לתג הפעמון

    fun refresh(showSpinner: Boolean) {
        if (showSpinner) state = HomeState.Loading
        refreshing = true
        scope.launch {
            try {
                val chans = ChannelsRepository.getChannels(context).forLevel(settings.filterLevel, settings.userGender)
                channels = chans
                Diagnostics.log("HOME: ${chans.size} ערוצים מאושרים אחרי סינון (רמה ${settings.filterLevel})")
                if (chans.isEmpty()) {
                    if (state !is HomeState.Success) {
                        state = HomeState.Error("רשימת הערוצים המאושרים ריקה — בדוק חיבור לאינטרנט")
                    }
                    return@launch
                }

                val videos = YouTubeRepository.fetchAllChannelsFeed(chans)
                Diagnostics.log("HOME: ${videos.size} סרטונים מה-RSS")
                if (videos.isEmpty()) {
                    if (state !is HomeState.Success) {
                        state = HomeState.Error("לא התקבלו סרטונים מהערוצים המאושרים")
                    }
                    return@launch
                }

                // מיון ~2,500 סרטונים + קריאת ההיסטוריה מהדיסק. scope כאן הוא
                // rememberCoroutineScope, כלומר Dispatchers.Main — בלי המעבר
                // הזה כל רענון של מסך הבית עושה את העבודה על תהליכון ה-UI.
                val ordered = withContext(Dispatchers.Default) {
                    sanitizeFeed(personalizeFeed(videos, store.localHistory()))
                }

                // הפיד מוצג *מיד*. העשרת המטא-דאטה היא שיפור, לא תנאי:
                // כשהיא הייתה חוסמת את ההצגה, מסך הבית חיכה לעד 6 קריאות רשת
                // רצופות (8+10 שניות timeout כל אחת) לפני שהראה משהו, וכל תקלה
                // בדרך הופיעה כ"שגיאה בטעינה".
                state = HomeState.Success(ordered)
                FeedCache.saveFeed(context, ordered)

                // חימום מראש של ראש הפיד: פתרון הזרם לוקח ~1.5 שניות, וכל הזמן
                // הזה נגבה מהמשתמש אחרי הלחיצה. מחממים ברקע בזמן שהוא עוד גולל,
                // כך שהלחיצה עצמה פוגעת במטמון ומתחילה לנגן מיד.
                StreamRepository.prefetch(ordered.map { it.id })

                // ההעשרה רצה אחרי ההצגה: קודם מה שנראה על המסך, אחר כך השאר.
                var latest = ordered
                for (limit in listOf(60, 300)) {
                    val enriched = runCatching { VideoMetadata.enrich(context, ordered, limit) }
                        // יציאה מהמסך היא ביטול תקין, לא תקלה — אין טעם לרשום אותה.
                        .onFailure {
                            if (it !is kotlinx.coroutines.CancellationException) {
                                Diagnostics.log("HOME: העשרה נכשלה — ${it.message}")
                            }
                        }
                        .getOrNull() ?: break
                    if (enriched != latest) {
                        latest = enriched
                        state = HomeState.Success(enriched)
                    }
                }
                // כתיבה אחת בסוף — הפיד המלא הוא ~2,500 רשומות, אין טעם
                // לסרייל אותו שלוש פעמים באותו רענון.
                if (latest !== ordered) FeedCache.saveFeed(context, latest)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Diagnostics.log("HOME: נכשל — ${e::class.simpleName}: ${e.message}")
                if (state !is HomeState.Success) state = HomeState.Error(e.message ?: "שגיאה")
            } finally {
                refreshing = false
            }
        }
    }

    // טעינה מיידית מהקאש (אם יש), ואז רענון ברקע
    LaunchedEffect(Unit) {
        runCatching { LibraryBadges.refresh(context) }
        runCatching { channels = ChannelsRepository.getChannels(context).forLevel(settings.filterLevel, settings.userGender) }
        val cached = FeedCache.loadFeed(context)
        if (!cached.isNullOrEmpty()) {
            state = HomeState.Success(
                withContext(Dispatchers.Default) {
                    sanitizeFeed(personalizeFeed(cached, store.localHistory()))
                },
            )
        }
        refresh(showSpinner = cached.isNullOrEmpty())
    }

    Box(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        // התוכן הראשי — מטושטש כשהתפריט הצף פתוח (אפקט זכוכית)
        Column(modifier = Modifier.fillMaxSize()) {
            // טופ-בר: אווטאר לתפריט, ושם האפליקציה. זהו.
            //
            // קודם ישבו כאן גם "סרטונים חדשים" ו"שידורים חיים" כשני עיגולים
            // קטנים. שניהם *יעדי תוכן*, לא פעולות על המסך הנוכחי, והם נדחסו
            // לפינה שהעין לא סורקת. הם ירדו לשורת הצ'יפים — בדיוק המקום שאליו
            // המשתמש מסתכל כשהוא מחפש "מה יש כאן".
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.weight(1f))
                Text("Filter Tube", color = ThemeState.text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            }
            // שורה אחת לכל מה ש"יש כאן": קודם שני יעדי התוכן (חי, חדש) ואז
            // סינון הפיד לפי קטגוריה. היעדים מסומנים באייקון וצבע כדי שיהיה
            // ברור שהם מעבירים מסך ולא מסננים במקום.
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DestinationChip("שידורים חיים", Icons.Default.LiveTv, Color(0xFFFF3B30), onLive)
                DestinationChip(
                    label = if (newCount > 0) "חדשים ($newCount)" else "חדשים",
                    icon = Icons.Default.Notifications,
                    tint = if (newCount > 0) ThemeState.accent else ThemeState.subtext2,
                    onClick = onInbox,
                )
                if (channels.isNotEmpty()) {
                    Box(
                        modifier = Modifier.height(22.dp).width(1.dp).background(ThemeState.divider),
                    )
                    CategoryChip("הכל", selectedCategory == null) { selectedCategory = null }
                    val categories = remember(channels) { sortedCategories(channels.map { it.category }) }
                    categories.forEach { cat ->
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
                    // remember ולא חישוב ישיר: הבנייה הזו רצה בכל רה-קומפוזיציה,
                    // וכל אחת מהן בנתה מחדש מפה של 166 ערוצים וסיננה ~2,500
                    // סרטונים — פריים אחרי פריים.
                    val catByChannel = remember(channels) {
                        channels.associate { it.youtubeChannelId to it.category }
                    }
                    val displayed = remember(s.videos, selectedCategory, catByChannel) {
                        if (selectedCategory == null) s.videos
                        else s.videos.filter { catByChannel[it.channelId] == selectedCategory }
                    }
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

        // ── רדיו אישי ──────────────────────────────────────────────────────
        // כפתור זכוכית קטן: חצי שקוף, עם מסגרת דקה ורקע מטושטש, כדי שיהיה
        // נוכח בלי לכסות את הפיד. הוא לא צריך למשוך את העין יותר מהתוכן.
        //
        // התחנה עצמה נבנית ב-PersonalRadio מהלייקים, מההיסטוריה ומהסגנון,
        // ומנוגנת כמו שהיא — ראה RadioQueueManager.startQueue(preset).
        Row(
            modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 104.dp)
                    .clip(RoundedCornerShape(50))
                    .background(ThemeState.surface.copy(alpha = 0.55f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                    .clickable(enabled = !radioStarting) {
                        radioStarting = true
                        scope.launch {
                            onStartRadio()
                            // הבנייה קצרה; ההשהיה רק מונעת לחיצה כפולה בזמן
                            // שהמסך מתחלף לנגן.
                            kotlinx.coroutines.delay(1200)
                            radioStarting = false
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
        ) {
            if (radioStarting) {
                CircularProgressIndicator(
                    color = ThemeState.accent, strokeWidth = 1.6.dp,
                    modifier = Modifier.size(15.dp),
                )
            } else {
                Icon(Icons.Default.Radio, null, tint = ThemeState.accent, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text(
                if (radioStarting) "מכין…" else "רדיו",
                color = ThemeState.text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** A stale cache or a repeated upstream item must never create duplicate LazyColumn keys. */
private fun sanitizeFeed(videos: List<Video>): List<Video> =
    videos.asSequence()
        .filter { it.id.isNotBlank() }
        .distinctBy { it.id }
        .toList()

/**
 * צ'יפ שמעביר למסך אחר (שידורים חיים, סרטונים חדשים) — להבדיל מ-[CategoryChip]
 * שמסנן את הפיד במקום. האייקון והמסגרת הם ההבדל הוויזואלי שמונע בלבול.
 */
@Composable
private fun DestinationChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(ThemeState.surface)
            .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = ThemeState.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

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
    val liked = video.id in LibraryBadges.liked
    val watched = video.id in LibraryBadges.watched
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
            // משך זמן הסרטון בפינה הימנית התחתונה
            val formattedDur = video.formattedDuration()
            if (formattedDur.isNotBlank()) {
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                        .clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(formattedDur, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            // סימון "נצפה" — פס התקדמות מלא בתחתית התמונה, כמו ביוטיוב
            if (watched) {
                Box(
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp)
                        .background(ThemeState.accent),
                )
            }
            // סימון "אהבתי"
            if (liked) {
                Box(
                    modifier = Modifier.align(Alignment.TopStart).padding(9.dp)
                        .clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f))
                        .padding(5.dp),
                ) {
                    Icon(Icons.Default.Favorite, "אהבתי", tint = ThemeState.accent,
                        modifier = Modifier.size(12.dp))
                }
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
                val subParts = mutableListOf(video.channelName)
                val viewsStr = video.formattedViewCount()
                if (viewsStr.isNotBlank()) subParts.add(viewsStr)
                val timeStr = video.timeAgoHe()
                // "תאריך לא זמין" רק מרעיש — עדיף להשמיט את החלק הזה
                if (timeStr.isNotBlank() && timeStr != "תאריך לא זמין") subParts.add(timeStr)
                Text(subParts.joinToString(" · "), fontSize = 12.sp, color = ThemeState.subtext,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                val watchedStr = video.watchedAgoHe()
                if (watched && watchedStr.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(watchedStr, fontSize = 11.sp, color = ThemeState.accent,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    if (showActions) VideoActionMenu(video, onDismiss = { showActions = false })
}

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
                VideoAction("הבא בתור", Icons.AutoMirrored.Filled.QueueMusic) {
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
                val liked = video.id in LibraryBadges.liked
                VideoAction(if (liked) "הסר מסרטונים שאהבתי" else "הוסף לסרטונים שאהבתי", if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder) {
                    LibraryBadges.setLiked(video.id, store.toggleLike(video)); onDismiss()
                }
                VideoAction("שתף סרטון", Icons.Default.Share) {
                    val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, "https://www.youtube.com/watch?v=${video.id}")
                    }
                    context.startActivity(android.content.Intent.createChooser(share, "שתף סרטון")); onDismiss()
                }
                VideoAction("הוסף לפלייליסט", Icons.AutoMirrored.Filled.PlaylistAdd) { playlistOpen = true }
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
