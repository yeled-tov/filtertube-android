package com.filtertube.app.ui
import com.filtertube.app.ThemeState

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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
import kotlinx.coroutines.launch

/**
 * סרגל עליון אחיד עם כפתור חזרה לכל מסכי הפירוט.
 *
 * [action] — פעולה קבועה בקצה הימני. היא נשארת על המסך גם כשגוללים, וזה
 * בדיוק ההבדל: "בקשת ערוץ" ישבה קודם כבאנר בתוך רשימה נגללת של 167 ערוצים,
 * ולכן אף אחד לא ידע שהיא קיימת.
 */
@Composable
fun DetailTopBar(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, start = 4.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזור", tint = ThemeState.text)
            }
            Text(title, color = ThemeState.text, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            action?.invoke()
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
    var refreshKey by remember { mutableStateOf(0) }
    // מקור הלייקים: FilterTube או YouTube. קודם אלה היו שתי קוביות נפרדות
    // בספרייה שנראו כמו אותו דבר פעמיים; עכשיו זו רשימה אחת עם מתג.
    var ytSource by remember { mutableStateOf(false) }
    val (title, videos) = remember(type, refreshKey, ytSource) {
        when (type) {
            "likes" -> "אהבתי" to (if (ytSource) store.youtubeLikes() else store.likes())
            "ytlikes" -> "אהבתי ביוטיוב" to store.youtubeLikes()
            "downloads" -> "הורדות" to store.downloads()
            "history" -> "היסטוריה" to store.localHistory()   // היסטוריה מקומית — תמיד עובדת
            "recs" -> "מומלצים" to store.recommendations()
            else -> "אוסף" to emptyList()
        }
    }
    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        DetailTopBar("$title (${videos.size})", onBack)
        if (type == "likes") {
            val ftCount = remember(refreshKey) { store.likes().size }
            val ytCount = remember(refreshKey) { store.youtubeLikes().size }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SourceTab("ב-FilterTube ($ftCount)", !ytSource) { ytSource = false }
                SourceTab("ביוטיוב ($ytCount)", ytSource) { ytSource = true }
            }
        }
        if (type == "history" && videos.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { store.clearLocalHistory(); refreshKey++ }) {
                    Text("נקה היסטוריה", color = ThemeState.accent2, fontSize = 13.sp)
                }
            }
        }
        if (videos.isEmpty()) EmptyHint(if (type == "history") "עדיין לא צפית בכלום" else "האוסף ריק")
        else LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
            items(videos, key = { it.id }) { v -> VideoRow(v, onClick = { onVideoClick(v) }) }
        }
    }
}

/** מתג מקור בתוך מסך אוסף — למשל לייקים של FilterTube מול לייקים של יוטיוב. */
@Composable
private fun RowScope.SourceTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.weight(1f)
            .clip(RoundedCornerShape(50))
            .background(if (selected) ThemeState.accent else ThemeState.card)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Color.White else ThemeState.subtext2,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

/**
 * רשימת כל המנויים של המשתמש מיוטיוב.
 *
 * ## מאושרים ולא מאושרים באותה רשימה
 * קודם הלא-מאושרים נזרקו כבר בסנכרון, והמשתמש ראה רשימה קטועה בלי שום רמז
 * שחסר בה משהו — "למה חצי מהמנויים שלי נעלמו". עכשיו הם כאן, באפור, ולחיצה
 * עליהם מציעה לבקש שיתווספו לרשימה המאושרת.
 *
 * הרשימה הלבנה לא נחלשת בכלום: ערוץ לא מאושר אינו נפתח, ולכן גם אין דרך
 * להגיע ממנו לסרטון. האפור הוא הזמנה לבקש, לא דלת.
 */
@Composable
fun SubscriptionsScreen(onOpenChannel: (String, String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { LibraryStore(context) }
    val subs = remember { store.subscriptions() }
    var approved by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pending by remember { mutableStateOf<SubChannel?>(null) }
    // ערוצים שכבר נשלחה עליהם בקשה במסך הזה — כדי לא לשלוח פעמיים ברצף.
    var requested by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        approved = runCatching {
            com.filtertube.app.data.ChannelsRepository.getChannels(context)
                .mapTo(HashSet()) { it.youtubeChannelId }
        }.getOrDefault(emptySet())
    }

    val approvedCount = subs.count { it.channelId in approved }
    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        DetailTopBar("המנויים שלי (${subs.size})", onBack)
        if (subs.isEmpty()) {
            EmptyHint("התחבר לחשבון גוגל בספריה כדי למשוך את המנויים שלך")
        } else {
            if (approvedCount < subs.size) {
                Text(
                    "$approvedCount מאושרים · ${subs.size - approvedCount} באפור — " +
                        "לחיצה עליהם שולחת בקשה להוסיף אותם",
                    color = ThemeState.subtext2, fontSize = 12.5.sp, lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)) {
                items(subs, key = { it.channelId }) { sub ->
                    SubRow(
                        sub = sub,
                        approved = sub.channelId in approved,
                        requested = sub.channelId in requested,
                    ) {
                        if (sub.channelId in approved) onOpenChannel(sub.channelId, sub.title)
                        else pending = sub
                    }
                }
            }
        }
    }

    pending?.let { target ->
        RequestChannelDialog(
            channel = target,
            onDismiss = { pending = null },
            onSent = { requested = requested + target.channelId; pending = null },
        )
    }
}

/**
 * בקשה להוסיף ערוץ לרשימה המאושרת, עם בחירת השיוך.
 *
 * שתי אפשרויות ולא רשימת קטגוריות מלאה: ההבחנה היחידה שבאמת משנה בצד
 * הלקוח היא "דתי לייט" — כי היא מגבילה לאודיו ומוצגת רק ברמה 3. את
 * הקטגוריה המדויקת קובע מי שמאשר, ולא מי שמבקש.
 */
@Composable
private fun RequestChannelDialog(
    channel: SubChannel,
    onDismiss: () -> Unit,
    onSent: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var datiLight by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("בקשה להוסיף ערוץ") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(channel.title, color = ThemeState.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text("לאיזה סוג תוכן הערוץ שייך?", color = ThemeState.subtext, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                RequestChoice("תוכן רגיל", !datiLight) { datiLight = false }
                Spacer(Modifier.height(6.dp))
                RequestChoice("דתי לייט (אודיו בלבד)", datiLight) { datiLight = true }
                Spacer(Modifier.height(10.dp))
                Text(
                    "הבקשה נשלחת לאישור. הערוץ לא ייפתח עד שיאושר.",
                    color = ThemeState.subtext2, fontSize = 11.5.sp, lineHeight = 16.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !sending,
                onClick = {
                    sending = true
                    scope.launch {
                        val result = com.filtertube.app.data.ChannelRequests.submitDetailed(
                            name = channel.title,
                            url = "https://www.youtube.com/channel/${channel.channelId}",
                            category = if (datiLight) "dati_light" else "general",
                            gender = "",
                            description = "נשלח ממסך המנויים",
                        )
                        sending = false
                        android.widget.Toast.makeText(
                            context, result.message, android.widget.Toast.LENGTH_LONG,
                        ).show()
                        if (result.ok) onSent() else onDismiss()
                    }
                },
            ) { Text(if (sending) "שולח…" else "שלח בקשה") }
        },
        dismissButton = { TextButton(enabled = !sending, onClick = onDismiss) { Text("ביטול") } },
        containerColor = ThemeState.card,
    )
}

@Composable
private fun RequestChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(if (selected) ThemeState.accent.copy(alpha = 0.18f) else ThemeState.bg2)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            color = if (selected) ThemeState.text else ThemeState.subtext,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun SubRow(
    sub: SubChannel,
    approved: Boolean,
    requested: Boolean,
    onClick: () -> Unit,
) {
    // האפור הוא המסר: הערוץ קיים אצלך ביוטיוב, אבל הוא לא חלק מהאפליקציה.
    val alpha = if (approved) 1f else 0.45f
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.alpha(alpha)) {
            if (sub.thumbnailUrl.isNotEmpty()) {
                AsyncImage(
                    model = sub.thumbnailUrl,
                    contentDescription = sub.title,
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(ThemeState.divider),
                    contentScale = ContentScale.Crop,
                    colorFilter = if (approved) null else ColorFilter.colorMatrix(
                        ColorMatrix().apply { setToSaturation(0f) },
                    ),
                )
            } else {
                Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(channelColor(sub.title)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = ThemeState.text)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                sub.title,
                color = if (approved) ThemeState.text else ThemeState.subtext,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (!approved) {
                Text(
                    if (requested) "הבקשה נשלחה — ממתין לאישור" else "לא מאושר · לחץ כדי לבקש להוסיף",
                    color = ThemeState.subtext2, fontSize = 11.5.sp, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
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
    val context = LocalContext.current
    val store = remember { LibraryStore(context) }
    var subscribed by remember { mutableStateOf(store.isSubscribed(channelId)) }

    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        DetailTopBar(channelName, onBack)
        // עקוב — קובע אם תקבל התראות על סרטונים חדשים מהערוץ הזה
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = { subscribed = store.toggleSubscription(channelId) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (subscribed) ThemeState.surface else ThemeState.accent,
                ),
            ) { Text(if (subscribed) "עוקב ✓" else "עקוב", color = if (subscribed) ThemeState.text else Color.White) }
            Spacer(Modifier.width(12.dp))
            Text(
                if (subscribed) "תקבל התראות על סרטונים חדשים" else "עקוב כדי לקבל התראות וסרטונים חדשים",
                color = ThemeState.subtext, fontSize = 12.sp,
            )
        }
        HorizontalDivider(color = ThemeState.divider)
        when (val s = state) {
            is HomeState.Loading -> CenteredLoading("טוען סרטונים...")
            is HomeState.Error -> CenteredError(s.message) { retry++ }
            is HomeState.Success -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
                items(s.videos, key = { it.id }) { v -> VideoRow(v, onClick = { onVideoClick(v) }) }
            }
        }
    }
}
