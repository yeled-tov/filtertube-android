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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Person
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
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "חזור", tint = ThemeState.text)
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
    // ── שני מקורות, ושניהם של יוטיוב ──────────────────────────────────
    // קודם המתג היה בין "אהבתי ב-FilterTube" ל"אהבתי ביוטיוב", ואלה נראו
    // כמו אותו דבר פעמיים — ומאז שלייק באפליקציה מסומן גם ביוטיוב עצמה,
    // הם באמת אותו דבר. המתג עבר להבחנה שכן קיימת: יוטיוב מול יוטיוב
    // מיוזיק, שתי רשימות נפרדות אצל גוגל עצמה.
    var musicSource by remember { mutableStateOf(false) }
    // הרשימה המאושרת. ריקה = עוד לא נטענה, ואז לא מאפירים כלום: להראות את
    // כל הספרייה אפורה לרגע בכל כניסה גרוע מלא להאפיר בכלל.
    var approvedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var requestFor by remember { mutableStateOf<Video?>(null) }
    LaunchedEffect(Unit) {
        approvedIds = runCatching {
            com.filtertube.app.data.ChannelsRepository.getChannels(context)
                .mapTo(HashSet()) { it.youtubeChannelId }
        }.getOrDefault(emptySet())
    }
    val (title, videos) = remember(type, refreshKey, musicSource) {
        when (type) {
            "likes" -> "אהבתי" to (if (musicSource) store.musicLikes() else store.youtubeLikes())
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
            val ytCount = remember(refreshKey) { store.youtubeLikes().size }
            val musicCount = remember(refreshKey) { store.musicLikes().size }
            val greyed = videos.count {
                approvedIds.isNotEmpty() && it.channelId.isNotBlank() && it.channelId !in approvedIds
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SourceTab("יוטיוב ($ytCount)", !musicSource) { musicSource = false }
                SourceTab("יוטיוב מיוזיק ($musicCount)", musicSource) { musicSource = true }
            }
            if (greyed > 0) {
                Text(
                    "$greyed באפור — מערוצים שלא אושרו. לחיצה עליהם שולחת בקשה להוסיף.",
                    color = ThemeState.subtext2, fontSize = 12.sp, lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
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
            items(videos, key = { it.id }) { v ->
                // ── אפור = הערוץ לא ברשימה המאושרת ────────────────────────
                // הרשימה שלמה בכוונה: "אהבתי" חתוך בלי שום רמז שחסר בו משהו
                // הוא בלבול, בדיוק כמו שהיה במנויים. אבל אפור אינו דלת —
                // הלחיצה מגיעה לטופס הבקשה ולא לנגן, ולכן אין מכאן שום דרך
                // לסרטון מערוץ שלא אושר.
                val ok = approvedIds.isEmpty() || v.channelId.isBlank() || v.channelId in approvedIds
                if (ok) {
                    VideoRow(v, onClick = { onVideoClick(v) })
                } else {
                    Box(Modifier.alpha(0.45f)) {
                        VideoRow(v, onClick = { requestFor = v })
                    }
                }
            }
        }
    }

    requestFor?.let { target ->
        ChannelRequestDialog(
            onDismiss = { requestFor = null },
            prefillName = target.channelName.ifBlank { target.title },
            prefillUrl = "https://www.youtube.com/channel/${target.channelId}",
        )
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

    // ── הטופס המלא, לא גרסה מקוצרת ────────────────────────────────────
    // ניסיתי כאן דיאלוג של שתי אפשרויות ("רגיל" / "דתי לייט"), וזה היה
    // ויתור: מי שמאשר צריך לדעת מה הערוץ מכיל ולמי הוא מיועד, ובלי זה כל
    // בקשה חוזרת אליו כשאלה. זה אותו טופס שנפתח מ"בקשת ערוץ", עם השם
    // והקישור כבר ממולאים מהמנוי עצמו.
    pending?.let { target ->
        ChannelRequestDialog(
            onDismiss = {
                requested = requested + target.channelId
                pending = null
            },
            prefillName = target.title,
            prefillUrl = "https://www.youtube.com/channel/${target.channelId}",
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
                    Icon(Icons.Rounded.Person, null, tint = ThemeState.text)
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
