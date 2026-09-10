package com.filtertube.app.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filtertube.app.ThemeState
import com.filtertube.app.data.Channel
import com.filtertube.app.data.ChannelsRepository
import com.filtertube.app.data.FeedCache
import com.filtertube.app.data.LibraryStore
import com.filtertube.app.data.SettingsStore
import com.filtertube.app.data.Video
import com.filtertube.app.data.forLevel

/**
 * הקטגוריות שנחשבות "מוזיקה" ב-FilterMusic.
 *
 * FilterMusic הוא מצב מוזיקה, לא אפליקציה שנייה: הוא מציג את אותם ערוצים
 * מאושרים, מצומצמים למה שבאמת מנגנים. שיעור תורה או מתכון לא שייכים לכאן,
 * גם אם הם מאושרים לחלוטין.
 */
private val MUSIC_CATEGORIES = setOf("music", "dati_light", "events")

private enum class MusicTab(val label: String, val icon: ImageVector) {
    HOME("בית", Icons.Default.Home),
    SEARCH("חיפוש", Icons.Default.Search),
    LIBRARY("ספריה", Icons.Default.LibraryMusic),
}

/**
 * FilterMusic — מצב המוזיקה של האפליקציה.
 *
 * ## מה זה
 * אותו מאגר, אותה רשימה לבנה, אותו נגן — ממשק אחר. הכל כאן ריבועי, קצר
 * ורשימתי, כי ככה נראית אפליקציית מוזיקה; FilterTube נשארת מלבנית ווידאואית.
 * מעבר בין השניים הוא לחיצה אחת בשני הכיוונים.
 *
 * ## למה זה מסך אחד עם טאבים פנימיים
 * ולא ענף חדש בגרף הניווט: המצב הזה הוא עולם סגור עם ניווט משלו. שילובו
 * בגרף הראשי היה מערבב שתי שורות ניווט תחתונות ושתי היררכיות חזרה.
 *
 * @param onPlay מנגן רשימה החל מאינדקס — הנגן והתור הם אותם אלה של FilterTube.
 * @param miniPlayer המיני-פלייר המשותף, מוזרק כדי לא לשכפל אותו כאן.
 */
@Composable
fun FilterMusicScreen(
    onExit: () -> Unit,
    onPlay: (List<Video>, Int) -> Unit,
    miniPlayer: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    val store = remember { LibraryStore(context) }

    var tab by remember { mutableStateOf(MusicTab.HOME) }
    var loading by remember { mutableStateOf(true) }
    var feed by remember { mutableStateOf<List<Video>>(emptyList()) }
    var likes by remember { mutableStateOf<List<Video>>(emptyList()) }
    var history by remember { mutableStateOf<List<Video>>(emptyList()) }
    var artists by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var favorites by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        val channels = runCatching {
            ChannelsRepository.getChannels(context).forLevel(settings.filterLevel, settings.userGender)
        }.getOrNull().orEmpty()
        val musicIds = channels.asSequence()
            .filter { it.category in MUSIC_CATEGORIES }
            .mapTo(HashSet()) { it.youtubeChannelId }

        // distinctBy חובה ולא נוי: מפתח כפול ב-LazyColumn מפיל את המסך,
        // ואותו סרטון יכול להופיע פעמיים בפיד אחרי רענון.
        fun List<Video>.musicOnly() =
            filter { it.channelId in musicIds && !it.isShort && it.id.isNotBlank() }
                .distinctBy { it.id }

        feed = runCatching { FeedCache.loadFeed(context) }.getOrNull().orEmpty().musicOnly()
        likes = runCatching { store.likes() }.getOrNull().orEmpty().musicOnly()
        history = runCatching { store.localHistory() }.getOrNull().orEmpty().musicOnly()
        favorites = runCatching { settings.favoriteArtists }.getOrNull().orEmpty()
        artists = channels.filter { it.youtubeChannelId in musicIds }
            .sortedByDescending { it.youtubeChannelId in favorites }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        MusicTopBar(tab, onExit)

        Box(Modifier.weight(1f)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = ThemeState.accent)
                }
                tab == MusicTab.HOME -> MusicHome(feed, likes, history, artists, favorites, onPlay)
                tab == MusicTab.SEARCH -> MusicSearch(feed + likes + history, onPlay)
                else -> MusicLibrary(likes, history, onPlay)
            }
        }

        // המיני-פלייר של FilterTube, כאן מעל שורת הניווט של FilterMusic:
        // הסרגל התחתון הרגיל מוסתר במצב הזה, ואפליקציית מוזיקה בלי גישה
        // למה שמתנגן היא לא אפליקציית מוזיקה.
        miniPlayer()
        MusicNavBar(tab) { tab = it }
    }
}

@Composable
private fun MusicTopBar(tab: MusicTab, onExit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding()
            .padding(start = 6.dp, end = MusicDim.screenPadding, top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // חזרה ל-FilterTube — במקום הקבוע של "אחורה", כדי שהמעבר יהיה
        // רפלקס ולא חיפוש. אותו כפתור בדיוק קיים בכיוון ההפוך במסך הבית.
        IconButton(onClick = onExit) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה ל-FilterTube", tint = ThemeState.text)
        }
        Text(
            "FilterMusic", color = ThemeState.text,
            fontSize = 20.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f),
        )
        Text(tab.label, color = ThemeState.subtext, fontSize = 12.sp)
    }
}

@Composable
private fun MusicNavBar(current: MusicTab, onSelect: (MusicTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(ThemeState.surface)
            .navigationBarsPadding()
            .height(MusicDim.navBarHeight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MusicTab.entries.forEach { entry ->
            val selected = entry == current
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight()
                    .clickable { onSelect(entry) },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) ThemeState.accent.copy(alpha = 0.20f) else Color.Transparent)
                        .padding(horizontal = 18.dp, vertical = 4.dp),
                ) {
                    Icon(
                        entry.icon, entry.label,
                        tint = if (selected) ThemeState.accent else ThemeState.subtext,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    entry.label, fontSize = 11.sp,
                    color = if (selected) ThemeState.accent else ThemeState.subtext,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
    }
}

// ── בית ──────────────────────────────────────────────────────────────────
@Composable
private fun MusicHome(
    feed: List<Video>,
    likes: List<Video>,
    history: List<Video>,
    artists: List<Channel>,
    favorites: Set<String>,
    onPlay: (List<Video>, Int) -> Unit,
) {
    if (feed.isEmpty() && likes.isEmpty() && history.isEmpty()) {
        EmptyState("עוד אין מוזיקה להציג.\nהפיד מתעדכן מהערוצים המאושרים — נסה שוב בעוד רגע.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        // ── שתי פעולות שמתחילות ניגון בלי לבחור כלום ──────────────────
        // אפליקציית מוזיקה נמדדת בכמה מהר יוצא צליל. שתי הכפתורים האלה הם
        // המסלול הקצר ביותר: אחד לסדר הרגיל, אחד לערבוב.
        item {
            val all = remember(likes, history, feed) {
                (likes + history + feed).distinctBy { it.id }
            }
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = MusicDim.screenPadding, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BigAction("נגן", Icons.Default.PlayArrow, Modifier.weight(1f)) {
                    if (all.isNotEmpty()) onPlay(all, 0)
                }
                BigAction("ערבוב", Icons.Default.Shuffle, Modifier.weight(1f)) {
                    if (all.isNotEmpty()) onPlay(all.shuffled(), 0)
                }
            }
        }

        if (history.isNotEmpty()) {
            item { SectionTitle("המשך להאזין") }
            item { HorizontalSongs(history.take(20), onPlay) }
        }
        if (likes.isNotEmpty()) {
            item { SectionTitle("השירים שאהבת") }
            item { HorizontalSongs(likes.take(20), onPlay) }
        }
        if (artists.isNotEmpty()) {
            item { SectionTitle("אמנים") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = MusicDim.screenPadding),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(artists.take(24), key = { it.youtubeChannelId }) { artist ->
                        ArtistCircle(artist.name, artist.youtubeChannelId in favorites) {
                            // ניגון כל מה שיש מהאמן הזה, בסדר הפיד.
                            val songs = feed.filter { it.channelId == artist.youtubeChannelId }
                            if (songs.isNotEmpty()) onPlay(songs, 0)
                        }
                    }
                }
            }
        }
        if (feed.isNotEmpty()) {
            item { SectionTitle("חדש") }
            items(feed.take(60), key = { it.id }) { song ->
                MusicListItem(song) { onPlay(feed, feed.indexOf(song)) }
            }
        }
    }
}

@Composable
private fun HorizontalSongs(songs: List<Video>, onPlay: (List<Video>, Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = MusicDim.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(songs, key = { it.id }) { song ->
            MusicGridItem(song) { onPlay(songs, songs.indexOf(song)) }
        }
    }
}

@Composable
private fun BigAction(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier.height(48.dp).clip(RoundedCornerShape(24.dp))
            .background(ThemeState.card).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = ThemeState.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = ThemeState.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

// ── חיפוש ────────────────────────────────────────────────────────────────
@Composable
private fun MusicSearch(pool: List<Video>, onPlay: (List<Video>, Int) -> Unit) {
    var query by remember { mutableStateOf("") }
    val songs = remember(pool) { pool.distinctBy { it.id } }
    // הסינון מתבצע על הרשימה שכבר בזיכרון: זה חיפוש בתוך המוזיקה המאושרת,
    // לא בקשה חדשה ליוטיוב — ולכן הוא מיידי ועובד גם בלי רשת.
    val results = remember(songs, query) {
        val q = query.trim()
        if (q.isBlank()) emptyList()
        else songs.filter { it.title.contains(q, true) || it.channelName.contains(q, true) }.take(80)
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("שיר או אמן", color = ThemeState.subtext, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = ThemeState.subtext) },
            modifier = Modifier.fillMaxWidth().padding(MusicDim.screenPadding),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = ThemeState.text,
                unfocusedTextColor = ThemeState.text,
                focusedBorderColor = ThemeState.accent,
                unfocusedBorderColor = ThemeState.divider,
                focusedContainerColor = ThemeState.card,
                unfocusedContainerColor = ThemeState.card,
            ),
        )
        when {
            query.isBlank() -> EmptyState("חפש שיר או אמן מתוך המוזיקה המאושרת.")
            results.isEmpty() -> EmptyState("לא נמצאו תוצאות ל״$query״.")
            else -> LazyColumn {
                items(results, key = { it.id }) { song ->
                    MusicListItem(song) { onPlay(results, results.indexOf(song)) }
                }
            }
        }
    }
}

// ── ספריה ────────────────────────────────────────────────────────────────
@Composable
private fun MusicLibrary(
    likes: List<Video>,
    history: List<Video>,
    onPlay: (List<Video>, Int) -> Unit,
) {
    if (likes.isEmpty() && history.isEmpty()) {
        EmptyState("הספרייה תתמלא ממה שתשמע ותסמן בלב.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        if (likes.isNotEmpty()) {
            item { SectionTitle("אהבתי (${likes.size})") }
            items(likes, key = { "like_${it.id}" }) { song ->
                MusicListItem(song) { onPlay(likes, likes.indexOf(song)) }
            }
        }
        if (history.isNotEmpty()) {
            item { SectionTitle("הושמע לאחרונה") }
            items(history.take(60), key = { "hist_${it.id}" }) { song ->
                MusicListItem(song) { onPlay(history, history.indexOf(song)) }
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
        Text(
            text, color = ThemeState.subtext, fontSize = 13.5.sp, lineHeight = 20.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
