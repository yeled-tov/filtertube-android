package com.filtertube.app.ui.music

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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

private enum class MusicChip(val label: String) {
    ALL("הכל"),
    LIKED("אהבתי"),
    RECENT("לאחרונה"),
    NEW("חדש"),
}

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
    onOpenSettings: () -> Unit = {},
    activeId: String? = null,
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
        // הסמלים של הערוצים — נמשכים בהדרגה ונשמרים לתמיד. בלעדיהם עיגול
        // האמן ריק, וזו בדיוק התלונה על "אות באנגלית במקום התמונה".
        runCatching {
            com.filtertube.app.data.ChannelAvatars.warm(
                context, channels.map { it.youtubeChannelId },
            )
        }
        artists = channels.filter { it.youtubeChannelId in musicIds }
            .sortedByDescending { it.youtubeChannelId in favorites }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        MusicTopBar(tab, onExit, onOpenSettings)

        Box(Modifier.weight(1f)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = ThemeState.accent)
                }
                tab == MusicTab.HOME -> MusicHome(
                    feed, likes, history, artists, favorites, activeId, onPlay,
                    onOpenArtist = { artist ->
                        val songs = feed.filter { it.channelId == artist.youtubeChannelId }
                        if (songs.isNotEmpty()) onPlay(songs, 0)
                    },
                )
                tab == MusicTab.SEARCH -> MusicSearch(feed + likes + history, activeId, onPlay)
                else -> MusicLibrary(likes, history, activeId, onPlay)
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
private fun MusicTopBar(tab: MusicTab, onExit: () -> Unit, onOpenSettings: () -> Unit) {
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
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Default.Settings, "הגדרות FilterMusic", tint = ThemeState.subtext2)
        }
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
/**
 * מסך הבית, בנוי כמו של Metrolist / YouTube Music.
 *
 * הקצב של הדף הוא מה שמייצר את הזהות שלו, ולא הצבעים:
 *  1. שורת צ'יפים לסינון
 *  2. "בחירה מהירה" — רשת אופקית של **4 שורות**, שנגללת בעמודים. זה
 *     האלמנט החתימתי של יוטיוב מיוזיק, וזה מה שחסר קודם.
 *  3. קטעים, כל אחד עם כותרת גדולה בצבע ההדגשה ורצועה אופקית מתחת.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MusicHome(
    feed: List<Video>,
    likes: List<Video>,
    history: List<Video>,
    artists: List<Channel>,
    favorites: Set<String>,
    activeId: String?,
    onPlay: (List<Video>, Int) -> Unit,
    onOpenArtist: (Channel) -> Unit,
) {
    if (feed.isEmpty() && likes.isEmpty() && history.isEmpty()) {
        EmptyState("עוד אין מוזיקה להציג.\nהפיד מתעדכן מהערוצים המאושרים — נסה שוב בעוד רגע.")
        return
    }

    var chip by remember { mutableStateOf(MusicChip.ALL) }
    val gridState = rememberLazyGridState()

    // "בחירה מהירה" — הכי אישי שיש: קודם אהובים, אחר כך מה שנשמע, ואז חדש.
    val quickPicks = remember(likes, history, feed, chip) {
        val base = when (chip) {
            MusicChip.ALL -> likes + history + feed
            MusicChip.LIKED -> likes
            MusicChip.RECENT -> history
            MusicChip.NEW -> feed
        }
        base.distinctBy { it.id }.take(40)
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = MusicDim.screenPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(MusicChip.entries.toList()) { entry ->
                    val selected = entry == chip
                    FilterChip(
                        selected = selected,
                        onClick = { chip = entry },
                        label = { Text(entry.label, fontSize = 13.sp) },
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = ThemeState.card,
                            labelColor = ThemeState.subtext2,
                            selectedContainerColor = ThemeState.accent,
                            selectedLabelColor = Color.White,
                        ),
                        border = null,
                    )
                }
            }
        }

        if (quickPicks.isNotEmpty()) {
            item { NavigationTitle("בחירה מהירה") }
            item {
                // ארבע שורות בדיוק, גובה קבוע של 4 שורות רשימה, וגלילה
                // אופקית שנעצרת על עמוד שלם — בדיוק כמו במקור.
                LazyHorizontalGrid(
                    state = gridState,
                    rows = GridCells.Fixed(4),
                    flingBehavior = rememberSnapFlingBehavior(gridState),
                    modifier = Modifier.fillMaxWidth().height(MusicDim.listItemHeight * 4),
                ) {
                    items(quickPicks, key = { "qp_${it.id}" }) { song ->
                        Box(Modifier.fillParentMaxWidth(0.92f)) {
                            SongListItem(
                                video = song,
                                active = song.id == activeId,
                                playing = song.id == activeId,
                                onClick = { onPlay(quickPicks, quickPicks.indexOf(song)) },
                            )
                        }
                    }
                }
            }
        }

        if (history.isNotEmpty()) {
            item { NavigationTitle("המשך להאזין", label = "בשבילך") }
            item { SongRow(history.take(20), onPlay) }
        }
        if (likes.isNotEmpty()) {
            item { NavigationTitle("השירים שאהבת") }
            item { SongRow(likes.take(20), onPlay) }
        }
        if (artists.isNotEmpty()) {
            item { NavigationTitle("האמנים שלך") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = MusicDim.screenPadding),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(artists.take(24), key = { it.youtubeChannelId }) { artist ->
                        ArtistCircle(
                            artist.youtubeChannelId, artist.name,
                            artist.youtubeChannelId in favorites,
                        ) { onOpenArtist(artist) }
                    }
                }
            }
        }
        if (feed.isNotEmpty()) {
            item { NavigationTitle("חדש בערוצים שלך") }
            items(feed.take(60), key = { "new_${it.id}" }) { song ->
                SongListItem(
                    video = song,
                    active = song.id == activeId,
                    playing = song.id == activeId,
                    onClick = { onPlay(feed, feed.indexOf(song)) },
                )
            }
        }
    }
}

/** רצועה אופקית של כרטיסים ריבועיים. */
@Composable
private fun SongRow(songs: List<Video>, onPlay: (List<Video>, Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = MusicDim.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(songs, key = { it.id }) { song ->
            MusicGridItem(song) { onPlay(songs, songs.indexOf(song)) }
        }
    }
}

// ── חיפוש ────────────────────────────────────────────────────────────────
@Composable
private fun MusicSearch(pool: List<Video>, activeId: String?, onPlay: (List<Video>, Int) -> Unit) {
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
                    SongListItem(song, active = song.id == activeId, playing = song.id == activeId) {
                        onPlay(results, results.indexOf(song))
                    }
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
    activeId: String?,
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
                SongListItem(song, active = song.id == activeId, playing = song.id == activeId) {
                    onPlay(likes, likes.indexOf(song))
                }
            }
        }
        if (history.isNotEmpty()) {
            item { SectionTitle("הושמע לאחרונה") }
            items(history.take(60), key = { "hist_${it.id}" }) { song ->
                SongListItem(song, active = song.id == activeId, playing = song.id == activeId) {
                    onPlay(history, history.indexOf(song))
                }
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
