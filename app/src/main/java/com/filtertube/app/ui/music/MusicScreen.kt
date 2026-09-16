package com.filtertube.app.ui.music

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
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
import com.filtertube.app.ui.theme.MosaicTile
import com.filtertube.app.ui.theme.Tint
import com.filtertube.app.data.Channel
import com.filtertube.app.data.ChannelsRepository
import com.filtertube.app.data.FeedCache
import com.filtertube.app.data.LibraryStore
import com.filtertube.app.data.SettingsStore
import com.filtertube.app.data.Video
import com.filtertube.app.data.forLevel
import kotlinx.coroutines.launch

/**
 * הקטגוריות שנחשבות "מוזיקה" ב-FilterMusic.
 *
 * FilterMusic הוא מצב מוזיקה, לא אפליקציה שנייה: הוא מציג את אותם ערוצים
 * מאושרים, מצומצמים למה שבאמת מנגנים. שיעור תורה או מתכון לא שייכים לכאן,
 * גם אם הם מאושרים לחלוטין.
 */
private val MUSIC_CATEGORIES = setOf("music", "dati_light", "events")

/** "בחירה מהירה" — תשעה שירים בעמוד, שלוש שורות על שלוש עמודות. */
private const val SPEED_DIAL_ROWS = 3
private const val SPEED_DIAL_COLUMNS = 3

private enum class MusicChip(val label: String) {
    ALL("הכל"),
    LIKED("אהבתי"),
    RECENT("לאחרונה"),
    NEW("חדש"),
}

private enum class MusicTab(val label: String, val icon: ImageVector) {
    HOME("בית", Icons.Rounded.Home),
    SEARCH("חיפוש", Icons.Rounded.Search),
    // ── למה אין כאן "הורדות" ──────────────────────────────────────────
    // הורדות הן חלק מהספרייה ולא מקום אחר: זה עדיין "מה ששלי", רק שהוא
    // כבר על המכשיר. לשונית נפרדת אילצה לזכור בשתי רשימות שונות איפה שיר
    // נמצא, וגזלה רבע מסרגל הניווט בשביל הבחנה שאינה מעניינת את המאזין.
    LIBRARY("ספריה", Icons.Rounded.LibraryMusic),
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
    var downloads by remember { mutableStateOf<List<Video>>(emptyList()) }
    /** null = טרם נבדק. false = אין חיבור. */
    var online by remember { mutableStateOf<Boolean?>(null) }
    // התפריט הוא בדיוק זה של FilterTube, במצב מוזיקה: אותן פעולות, אותה
    // צורה, ורק ההורדה שונה — אודיו, ומסומנת כהורדת מוזיקה.
    var menuFor by remember { mutableStateOf<Video?>(null) }
    // הערוצים המוזיקליים המאושרים — החיפוש ברשת מוגבל אליהם.
    var searchChannels by remember { mutableStateOf<List<Channel>>(emptyList()) }

    LaunchedEffect(Unit) {
        val channels = runCatching {
            ChannelsRepository.getChannels(context).forLevel(settings.filterLevel, settings.userGender)
        }.getOrNull().orEmpty()
        // ApprovedChannels ולא סט מזהים: שיר שהועלה ע"י ערוץ ה-Topic של אמן
        // מאושר נשא מזהה שאינו ברשימה, ולכן נפל כאן בשקט — הוא לא הוצג אפור
        // אלא פשוט לא הופיע, ו"אהבתי" ב-FilterMusic נראה חסר בלי שום הסבר.
        val musicOnlyChannels = channels.filter { it.category in MUSIC_CATEGORIES }
        searchChannels = musicOnlyChannels
        val musicChannels = com.filtertube.app.data.ApprovedChannels(musicOnlyChannels)

        // distinctBy חובה ולא נוי: מפתח כפול ב-LazyColumn מפיל את המסך,
        // ואותו סרטון יכול להופיע פעמיים בפיד אחרי רענון.
        fun List<Video>.musicOnly() =
            filter { musicChannels.approves(it) && !it.isShort && it.id.isNotBlank() }
                .distinctBy { it.id }

        feed = runCatching { FeedCache.loadFeed(context) }.getOrNull().orEmpty().musicOnly()
        // ── מה נחשב "אהבתי" ב-FilterMusic ──────────────────────────────
        // שלושה מקורות, ובמכוון לא כולל את הלייקים של יוטיוב הרגיל:
        // מוזיקה שאהבת ביוטיוב מיוזיק, ולייקים מקומיים שניתנו בתוך
        // FilterMusic עצמה. לייק על שיעור תורה נשאר ב-FilterTube.
        // ── שלושה מקורות, ובכוונה ─────────────────────────────────────
        // חסר כאן youtubeLikes, ולכן "אהבתי" ב-FilterMusic היה כמעט תמיד
        // ריק: רוב הלייקים של המשתמש יושבים שם, לא ב"מוזיקה שאהבתי"
        // הנפרדת של מיוזיק (שממנה לרוב אף שיר לא עובר את הרשימה הלבנה).
        //
        // musicOnly הוא מה שמפריד: שיעור תורה שסומן בלב לא ייכנס לכאן גם
        // אם הוא ברשימת הלייקים, כי הערוץ שלו אינו ערוץ מוזיקה.
        likes = (
            runCatching { store.musicLikes() }.getOrNull().orEmpty() +
                runCatching { store.youtubeLikes() }.getOrNull().orEmpty() +
                runCatching { store.likes() }.getOrNull().orEmpty()
            ).musicOnly()
        history = runCatching { store.localHistory() }.getOrNull().orEmpty().musicOnly()
        favorites = runCatching { settings.favoriteArtists }.getOrNull().orEmpty()
        // הסמלים של הערוצים — נמשכים בהדרגה ונשמרים לתמיד. בלעדיהם עיגול
        // האמן ריק, וזו בדיוק התלונה על "אות באנגלית במקום התמונה".
        runCatching {
            com.filtertube.app.data.ChannelAvatars.warm(
                context, channels.map { it.youtubeChannelId },
            )
        }
        // רשימת האמנים נבנית מהערוצים המאושרים עצמם, ולכן היא לא עוברת דרך
        // גשר ה-Topic: ערוץ Topic אינו מוצג כאמן נפרד אלא נספר לאמן שלו.
        artists = channels.filter { it.category in MUSIC_CATEGORIES }
            .sortedByDescending { it.youtubeChannelId in favorites }
        downloads = runCatching { store.downloads() }.getOrNull().orEmpty()
            .filter { it.localUri.isNotBlank() }
        loading = false
    }

    // ── רענון ההורדות ────────────────────────────────────────────────────
    // הרשימה נטענה פעם אחת בכניסה למסך, ולכן הורדה שהסתיימה *אחרי* הכניסה
    // לא הופיעה עד יציאה וחזרה — וזה נראה בדיוק כמו הורדה שלא עבדה.
    // active הוא רשימת-מצב, ולכן ספירת המושלמים בה מספיקה כטריגר.
    val finishedCount = com.filtertube.app.data.DownloadEngine.active.count { it.progress >= 100 }
    LaunchedEffect(finishedCount) {
        if (finishedCount > 0) {
            downloads = runCatching { store.downloads() }.getOrNull().orEmpty()
                .filter { it.localUri.isNotBlank() }
        }
    }

    // ── זיהוי אופליין ─────────────────────────────────────────────────────
    // בלי חיבור, מסך בית שמנסה לנגן מיוטיוב הוא רק תסכול. הטאב מוחלף
    // אוטומטית להורדות — פעם אחת, כדי לא לחטוף למשתמש את הניווט בכל חזרה
    // למסך אם הוא בכל זאת בחר ללכת למקום אחר.
    var offlineRedirected by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        online = com.filtertube.app.data.Connectivity.isOnline(context)
        if (online == false && !offlineRedirected) {
            offlineRedirected = true
            tab = MusicTab.LIBRARY
        }
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
                    onMenu = { menuFor = it },
                )
                tab == MusicTab.SEARCH -> MusicSearch(
                    feed + likes + history, searchChannels, activeId, onPlay,
                    onMenu = { menuFor = it },
                )
                else -> MusicLibrary(
                    likes, history, downloads, online, activeId, onPlay,
                    onMenu = { menuFor = it },
                )
            }
        }

        menuFor?.let { song ->
            com.filtertube.app.ui.VideoActionMenu(
                video = song,
                onDismiss = { menuFor = null },
                musicMode = true,
            )
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
        // אותו מחליף מצבים שבמסך הבית, רק שכאן החצי השני מסומן. חץ "אחורה"
        // היה מסתיר את העובדה שאלה שני מצבים של אותה אפליקציה, ובעיקר לא
        // לימד את מי שהגיע לכאן איך חוזרים — מחליף מראה את שני הצדדים תמיד.
        Spacer(Modifier.width(6.dp))
        com.filtertube.app.ui.theme.ModeSwitch(musicMode = true) { music ->
            if (!music) onExit()
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Rounded.Settings, "הגדרות FilterMusic", tint = ThemeState.subtext2)
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
    onMenu: (Video) -> Unit,
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
                // ── שלוש על שלוש, עמוד אחרי עמוד ──────────────────────────
                // קודם זו הייתה רשת של שורות רשימה: תמונה קטנה בצד וטקסט
                // לידה. הכריכה היא מה שמזהה שיר במבט, ולכן כאן היא הפריט
                // עצמו — ריבוע עם הכותרת מתחתיו — תשעה שירים בעמוד, וגלילה
                // שנעצרת על עמוד שלם.
                val perPage = SPEED_DIAL_ROWS * SPEED_DIAL_COLUMNS
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val gap = 10.dp
                    val side = MusicDim.screenPadding
                    val tile = (maxWidth - side * 2 - gap * (SPEED_DIAL_COLUMNS - 1)) / SPEED_DIAL_COLUMNS
                    // גובה התא: הכריכה הריבועית ועוד שתי שורות טקסט.
                    val cellHeight = tile + 44.dp
                    Column {
                        LazyHorizontalGrid(
                            state = gridState,
                            rows = GridCells.Fixed(SPEED_DIAL_ROWS),
                            flingBehavior = rememberSnapFlingBehavior(gridState),
                            contentPadding = PaddingValues(horizontal = side),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                            verticalArrangement = Arrangement.spacedBy(gap),
                            modifier = Modifier.fillMaxWidth()
                                .height(cellHeight * SPEED_DIAL_ROWS + gap * (SPEED_DIAL_ROWS - 1)),
                        ) {
                            items(quickPicks, key = { "qp_${it.id}" }) { song ->
                                Box(Modifier.width(tile)) {
                                    MusicCell(
                                        song,
                                        active = song.id == activeId,
                                        onMenu = { onMenu(song) },
                                    ) { onPlay(quickPicks, quickPicks.indexOf(song)) }
                                }
                            }
                        }
                        // נקודות העמודים — בלי הן אין שום רמז שיש עוד עמוד.
                        val pages = (quickPicks.size + perPage - 1) / perPage
                        if (pages > 1) {
                            val current = gridState.firstVisibleItemIndex / perPage
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                repeat(pages) { page ->
                                    Box(
                                        modifier = Modifier.padding(horizontal = 3.dp).size(6.dp)
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                if (page == current) ThemeState.text
                                                else ThemeState.subtext2.copy(alpha = 0.35f),
                                            ),
                                    )
                                }
                            }
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
                    onMenu = { onMenu(song) },
                    onClick = { onPlay(feed, feed.indexOf(song)) },
                )
            }
        }
    }
}

/** כפתור פעולה רחב — "נגן", "ערבוב", "הורד". */
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
private fun MusicSearch(
    pool: List<Video>,
    musicChannels: List<Channel>,
    activeId: String?,
    onPlay: (List<Video>, Int) -> Unit,
    onMenu: (Video) -> Unit,
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val songs = remember(pool) { pool.distinctBy { it.id } }

    // ── שני מקורות, בזה אחר זה ────────────────────────────────────────────
    // הסינון המקומי מיידי ועובד בלי רשת, אבל הוא מוגבל למה שכבר נטען — ולכן
    // שיר מערוץ מאושר שלא הופיע בפיד פשוט "לא נמצא". החיפוש ברשת משלים את
    // החסר, ומגיע שנייה אחריו כדי שהתוצאות המקומיות כבר יהיו על המסך.
    val local = remember(songs, query) {
        val q = query.trim()
        if (q.isBlank()) emptyList()
        else songs.filter { it.title.contains(q, true) || it.channelName.contains(q, true) }.take(40)
    }
    var remote by remember { mutableStateOf<List<Video>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(query, musicChannels) {
        val q = query.trim()
        remote = emptyList()
        if (q.length < 2 || musicChannels.isEmpty()) { searching = false; return@LaunchedEffect }
        // השהיה קצרה: בלעדיה כל הקשה שולחת בקשה נפרדת ליוטיוב.
        kotlinx.coroutines.delay(450)
        searching = true
        val found = runCatching {
            com.filtertube.app.data.SearchEngine.search(context, q, musicChannels).videos
        }.getOrDefault(emptyList())
        searching = false
        remote = found
    }

    val results = remember(local, remote) {
        (local + remote).distinctBy { it.id }.filter { !it.isShort }.take(80)
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text("שיר או אמן", color = ThemeState.subtext, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Rounded.Search, null, tint = ThemeState.subtext) },
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
        if (searching) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = ThemeState.accent, trackColor = ThemeState.divider,
            )
        }
        when {
            query.isBlank() -> EmptyState("חפש שיר או אמן מתוך המוזיקה המאושרת.")
            results.isEmpty() && searching -> EmptyState("מחפש…")
            results.isEmpty() -> EmptyState("לא נמצאו תוצאות ל״$query״.")
            else -> LazyColumn {
                items(results, key = { it.id }) { song ->
                    SongListItem(
                        song, active = song.id == activeId, playing = song.id == activeId,
                        onMenu = { onMenu(song) },
                    ) { onPlay(results, results.indexOf(song)) }
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
    downloads: List<Video>,
    online: Boolean?,
    activeId: String?,
    onPlay: (List<Video>, Int) -> Unit,
    onMenu: (Video) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val active = com.filtertube.app.data.DownloadEngine.active

    // ── רק ההורדות של FilterMusic ─────────────────────────────────────────
    // מה שהורד מפילטר טיוב נשאר בפילטר טיוב, גם אם הוא שיר מערוץ מוזיקה.
    // הסימון נעשה ברגע ההורדה ולא נגזר מ"אודיו מול וידאו", כי גם בפילטר
    // טיוב יש קטגוריות שיורדות כאודיו בכפייה — ואז ההבחנה הזו שקרית.
    val mine = remember(downloads) { downloads.filter { it.fromMusic } }
    val recent = remember(history) { history.take(60) }

    // ── קובייה נפתחת, לא הכל פרוש ─────────────────────────────────────────
    // קודם שלושת האוספים נשפכו למסך אחד ארוך, ומי שחיפש שיר מסוים היה
    // צריך לגלול דרך אוסף שלם כדי להגיע לבא אחריו. קובייה לכל אוסף נותנת
    // את התמונה המלאה במסך אחד, והכניסה היא החלטה של המשתמש.
    var open by remember { mutableStateOf<String?>(null) }

    if (likes.isEmpty() && recent.isEmpty() && mine.isEmpty() && active.isEmpty()) {
        EmptyState("הספרייה תתמלא ממה שתשמע ותסמן בלב.")
        return
    }

    val collections = listOf(
        Triple("שירים שאהבתי", likes, Icons.Rounded.Favorite to Tint.red),
        Triple("ההורדות שלי", mine, Icons.Rounded.Download to Tint.green),
        Triple("הושמע לאחרונה", recent, Icons.Rounded.History to Tint.orange),
    )

    open?.let { title ->
        val songs = collections.firstOrNull { it.first == title }?.second.orEmpty()
        MusicCollection(
            title = title,
            songs = songs,
            activeId = activeId,
            onPlay = onPlay,
            onMenu = onMenu,
            onBack = { open = null },
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = MusicDim.screenPadding, end = MusicDim.screenPadding,
            top = 6.dp, bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (online == false) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp)).background(ThemeState.card)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.CloudOff, null, tint = Tint.amber, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("אין חיבור לאינטרנט", color = ThemeState.text,
                            style = MaterialTheme.typography.titleSmall)
                        Text("מה שהורדת ממשיך לעבוד", color = ThemeState.subtext,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // ── מה שמוריד עכשיו ───────────────────────────────────────────────
        if (active.isNotEmpty()) {
            item {
                Column {
                    NavigationTitle("מוריד עכשיו", label = "${active.size} פריטים")
                    active.take(4).forEach { task ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    task.video.title, color = ThemeState.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                                LinearProgressIndicator(
                                    progress = { task.progress / 100f },
                                    modifier = Modifier.fillMaxWidth().height(3.dp).padding(top = 4.dp),
                                    color = ThemeState.accent, trackColor = ThemeState.divider,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(task.status, color = ThemeState.subtext,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // ── שלוש הקוביות ──────────────────────────────────────────────────
        items(collections.chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (title, songs, look) ->
                    val (icon, tint) = look
                    MosaicTile(
                        title = title,
                        subtitle = "${songs.size} שירים",
                        images = songs.map { it.thumbnailUrl },
                        icon = icon,
                        tint = tint,
                        modifier = Modifier.weight(1f),
                        // גם כשריק: קובייה שנראית לחיצה ולא נלחצת היא תקלה
                        // בעיני המשתמש. המסך שנפתח יסביר שאין בו כלום.
                        onClick = { open = title },
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        // הורדת כל האהובים — הפעולה היחידה שמתחילה הורדה מרוכזת, ולכן היא
        // נשארת גלויה גם כשלא נכנסים לאף אוסף.
        if (likes.isNotEmpty()) {
            item {
                BigAction("הורד את מה שאהבת (אודיו)", Icons.Rounded.Download, Modifier.fillMaxWidth()) {
                    scope.launch {
                        android.widget.Toast.makeText(
                            context, "מוסיף ${likes.size} לתור ההורדות…",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                        likes.forEach {
                            com.filtertube.app.data.DownloadEngine.enqueueByVideo(
                                context, it, isAudio = true, fromMusic = true,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** אוסף אחד פרוש ככוורת, עם דרך חזרה. */
@Composable
private fun MusicCollection(
    title: String,
    songs: List<Video>,
    activeId: String?,
    onPlay: (List<Video>, Int) -> Unit,
    onMenu: (Video) -> Unit,
    onBack: () -> Unit,
) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = MusicDim.screenPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "חזרה לספרייה", tint = ThemeState.text)
            }
            Text(
                title, color = ThemeState.text,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            Text("${songs.size}", color = ThemeState.subtext,
                style = MaterialTheme.typography.bodyMedium)
        }
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = MusicDim.screenPadding, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BigAction("נגן הכל", Icons.Rounded.PlayArrow, Modifier.weight(1f)) { onPlay(songs, 0) }
            BigAction("ערבוב", Icons.Rounded.Shuffle, Modifier.weight(1f)) {
                onPlay(songs.shuffled(), 0)
            }
        }
        if (songs.isEmpty()) {
            EmptyState("עוד אין כאן שירים.")
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(MusicDim.cellMinWidth),
            contentPadding = PaddingValues(
                start = MusicDim.screenPadding, end = MusicDim.screenPadding,
                top = 8.dp, bottom = 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(songs, key = { it.id }) { song ->
                MusicCell(song, active = song.id == activeId, onMenu = { onMenu(song) }) {
                    onPlay(songs, songs.indexOf(song))
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
