package com.filtertube.app.ui.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.filtertube.app.ThemeState
import com.filtertube.app.data.LibraryStore
import com.filtertube.app.data.SettingsStore
import com.filtertube.app.ui.theme.playerSwipeGestures
import com.filtertube.app.data.Video
import com.filtertube.app.ui.PlayerUiState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * נגן FilterMusic — פריסת YouTube Music / Metrolist.
 *
 * ## מה מגדיר את המראה
 * הסדר האנכי, לא הצבעים: חץ סגירה בראש, כריכה **ריבועית גדולה** שתופסת
 * כמעט את כל הרוחב, כותרת ואמן מיושרים לצד אחד (לא במרכז), סרגל התקדמות
 * דק עם זמנים בקצוות, שורת בקרים שבה כפתור הניגון הוא עיגול מלא וגדול
 * משמעותית מהשאר, ובתחתית רצועה שמושכת את התור.
 *
 * זה מסך *מוזיקה*: אין כאן וידאו. גם כשמתנגן סרטון, מה שמוצג הוא הכריכה.
 */
@Composable
fun MusicPlayerScreen(
    controller: MediaController?,
    ui: PlayerUiState,
    onCollapse: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = remember { LibraryStore(context) }
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context) }
    val swipeTracks = remember { settings.musicSwipeTrack }
    val swipeDismiss = remember { settings.musicSwipeDismiss }

    // ── משוב למחווה ───────────────────────────────────────────────────────
    // התוכן זז עם האצבע, וחוזר למקומו בקפיץ כשמשחררים. בלי זה המחווה
    // מרגישה כמו הימור: או שמשהו קרה או שלא, ואין שום סימן באמצע.
    //
    // Animatable ולא state רגיל: הערך נקרא **רק בתוך offset{}**, כלומר
    // בשלב הפריסה ולא בקומפוזיציה. גרירה מזיזה את המסך בלי לבנות מחדש את
    // הכריכה, הכותרת והבקרים שישים פעם בשנייה.
    val shift = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var showQueue by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var liked by remember(ui.mediaId) {
        mutableStateOf(ui.mediaId?.let { store.isLiked(it) } == true)
    }
    // מתאפס לכל שיר: "כבר הורד" הוא מצב של השיר הנוכחי ולא של המסך.
    var downloading by remember(ui.mediaId) { mutableStateOf(false) }
    var downloaded by remember(ui.mediaId) {
        mutableStateOf(ui.mediaId?.let { store.downloadedVideo(it) != null } == true)
    }
    var repeatMode by remember { mutableStateOf(controller?.repeatMode ?: Player.REPEAT_MODE_OFF) }
    var shuffle by remember { mutableStateOf(controller?.shuffleModeEnabled == true) }

    // גרירה של הסרגל צריכה להיות חלקה, ולכן היא לא נכתבת ישירות לנגן: בזמן
    // הגרירה מוצג הערך המקומי, והחיפוש בפועל קורה רק בשחרור.
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableStateOf(0f) }
    val position = if (scrubbing) scrubValue.toLong() else ui.position
    val duration = ui.duration.coerceAtLeast(1L)

    val current = remember(ui.mediaId, ui.title, ui.artist) {
        Video(
            id = ui.mediaId.orEmpty(), title = ui.title, channelName = ui.artist,
            channelId = "", thumbnailUrl = ui.artworkUri?.toString().orEmpty(),
            publishedAt = 0L,
        )
    }

    // ── למה BoxWithConstraints ────────────────────────────────────────────
    // הכריכה נמדדה לפי *רוחב* המסך בלבד, והריבוע שיצא מזה היה גדול ממה
    // שנשאר אחרי הכותרת, פס ההתקדמות והבקרים. במסך קצר ה-Column פשוט גלש:
    // כפתור הנגינה ורצועת "הבא בתור" נדחפו אל מתחת לקצה ונחתכו — בדיוק
    // התלונה "לא רואים כפתור עצירה, לא רואים את התור, התמונה תופסת הכול".
    //
    // עכשיו הגובה הפנוי ידוע, והכריכה מקבלת את מה שנשאר ולא יותר.
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(ThemeState.bg)
            .playerSwipeGestures(
                tracksEnabled = swipeTracks,
                dismissEnabled = swipeDismiss,
                onNext = { controller?.seekToNextMediaItem() },
                onPrevious = { controller?.seekToPreviousMediaItem() },
                onDismiss = onCollapse,
                onDrag = { x, y -> scope.launch { shift.snapTo(Offset(x, y)) } },
                onDragFinished = { scope.launch { shift.animateTo(Offset.Zero) } },
            ),
    ) {
    val compact = maxHeight < 680.dp
    val sidePad = if (compact) 18.dp else MusicDim.playerPadding
    val gapL = if (compact) 14.dp else 28.dp
    val gapM = if (compact) 6.dp else 12.dp
    val gapS = if (compact) 4.dp else 10.dp

    Column(
        modifier = Modifier.fillMaxSize()
            .offset { IntOffset(shift.value.x.roundToInt(), shift.value.y.roundToInt()) }
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Rounded.KeyboardArrowDown, "סגור", tint = ThemeState.text, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Tune, "הגדרות", tint = ThemeState.subtext2)
            }
        }

        // ── הכריכה ────────────────────────────────────────────────────────
        // weight(1f) ולא גובה קבוע: היא לוקחת את *השארית* אחרי שכל השאר
        // קיבל את גובהו, ו-min בין הרוחב לגובה שומר אותה ריבועית בלי לגלוש.
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = sidePad),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints {
                SongArt(current, minOf(maxWidth, maxHeight), corner = 12.dp)
            }
        }

        Spacer(Modifier.height(gapL))

        // ── כותרת ואמן ────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = sidePad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    ui.title.ifBlank { "—" }, color = ThemeState.text,
                    fontSize = 21.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    ui.artist, color = ThemeState.subtext,
                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            // ── הורדה ─────────────────────────────────────────────────
            // עד עכשיו הדרך היחידה להוריד שיר מ-FilterMusic הייתה כפתור
            // "הורד את כל מה שאהבת" בלשונית ההורדות — הכל או כלום. כאן
            // מורידים בדיוק את מה שמתנגן, ברגע שמחליטים שרוצים אותו.
            IconButton(onClick = {
                if (downloaded || downloading) return@IconButton
                if (!com.filtertube.app.data.DownloadEngine.canDownload(context)) {
                    android.widget.Toast.makeText(
                        context, "הורדות הן פיצ'ר פרימיום. ראה הגדרות → FilterTube Premium", android.widget.Toast.LENGTH_LONG,
                    ).show()
                    return@IconButton
                }
                downloading = true
                scope.launch {
                    val ok = com.filtertube.app.data.DownloadEngine.enqueueByVideo(
                        context, current, isAudio = true, fromMusic = true,
                    )
                    downloading = false
                    downloaded = ok
                    android.widget.Toast.makeText(
                        context,
                        if (ok) "ההורדה התחילה — יופיע בספרייה" else "לא ניתן להתחיל הורדה",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }) {
                when {
                    downloading -> CircularProgressIndicator(
                        color = ThemeState.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                    downloaded -> Icon(
                        Icons.Rounded.DownloadDone, "כבר הורד", tint = ThemeState.accent,
                    )
                    else -> Icon(
                        Icons.Rounded.Download, "הורד שיר", tint = ThemeState.subtext2,
                    )
                }
            }
            IconButton(onClick = {
                val id = ui.mediaId ?: return@IconButton
                liked = store.toggleLike(current)
                com.filtertube.app.data.LibraryBadges.setLiked(id, liked)
            }) {
                Icon(
                    if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    "אהבתי", tint = if (liked) ThemeState.accent else ThemeState.subtext2,
                )
            }
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, "פעולות לשיר", tint = ThemeState.subtext2)
            }
        }

        if (menuOpen) {
            com.filtertube.app.ui.VideoActionMenu(
                video = current,
                onDismiss = { menuOpen = false },
                musicMode = true,
            )
        }

        Spacer(Modifier.height(gapM))

        // ── סרגל ההתקדמות ─────────────────────────────────────────────────
        // זמן תמיד זורם משמאל לימין, גם בממשק עברי: 0:00 בשמאל, ההתקדמות
        // זוחלת ימינה, הזמן הכולל בימין.
        CompositionLocalProvider(
            androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr,
        ) {
        Column(modifier = Modifier.padding(horizontal = sidePad)) {
            Slider(
                value = position.coerceIn(0L, duration).toFloat(),
                onValueChange = { scrubbing = true; scrubValue = it },
                onValueChangeFinished = {
                    controller?.seekTo(scrubValue.toLong())
                    scrubbing = false
                },
                valueRange = 0f..duration.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = ThemeState.accent,
                    activeTrackColor = ThemeState.accent,
                    inactiveTrackColor = ThemeState.divider,
                ),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(com.filtertube.app.ui.timeLtr(formatTime(position)), color = ThemeState.subtext, fontSize = 11.5.sp)
                Spacer(Modifier.weight(1f))
                Text(com.filtertube.app.ui.timeLtr(formatTime(ui.duration)), color = ThemeState.subtext, fontSize = 11.5.sp)
            }
        }
        }

        Spacer(Modifier.height(gapS))

        // ── הבקרים ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = sidePad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = {
                shuffle = !shuffle
                controller?.shuffleModeEnabled = shuffle
            }) {
                Icon(
                    Icons.Rounded.Shuffle, "ערבוב",
                    tint = if (shuffle) ThemeState.accent else ThemeState.subtext2,
                )
            }
            IconButton(onClick = { controller?.seekToPreviousMediaItem() }, enabled = ui.hasPrev) {
                Icon(
                    Icons.Rounded.SkipPrevious, "הקודם", modifier = Modifier.size(36.dp),
                    tint = if (ui.hasPrev) ThemeState.text else ThemeState.divider,
                )
            }
            // כפתור הניגון גדול משמעותית מהשאר — זו נקודת המשקל של המסך.
            Box(
                modifier = Modifier.size(68.dp).clip(CircleShape)
                    .background(ThemeState.accent)
                    .clickable {
                        val c = controller ?: return@clickable
                        if (c.isPlaying) c.pause() else c.play()
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (ui.buffering) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
                } else {
                    Icon(
                        if (ui.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (ui.isPlaying) "השהה" else "נגן",
                        tint = Color.White, modifier = Modifier.size(34.dp),
                    )
                }
            }
            IconButton(onClick = { controller?.seekToNextMediaItem() }, enabled = ui.hasNext) {
                Icon(
                    Icons.Rounded.SkipNext, "הבא", modifier = Modifier.size(36.dp),
                    tint = if (ui.hasNext) ThemeState.text else ThemeState.divider,
                )
            }
            IconButton(onClick = {
                repeatMode = when (repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                controller?.repeatMode = repeatMode
            }) {
                Icon(
                    if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    "חזרה",
                    tint = if (repeatMode == Player.REPEAT_MODE_OFF) ThemeState.subtext2 else ThemeState.accent,
                )
            }
        }

        Spacer(Modifier.height(gapS))

        // ── רצועת התור ────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable { showQueue = true }
                .background(ThemeState.surface)
                .padding(horizontal = 20.dp, vertical = if (compact) 10.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, tint = ThemeState.text, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("הבא בתור", color = ThemeState.text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
        }
    }

    }

    if (showQueue && controller != null) {
        MusicQueueSheet(controller, ui, onDismiss = { showQueue = false })
    }
}

/** "3:07" / "1:02:11" */
internal fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** קורא את התור מהנגן — נקרא מחדש בכל שינוי ב-queueVersion. */
internal fun readQueue(controller: MediaController): List<Video> {
    val out = ArrayList<Video>(controller.mediaItemCount)
    for (i in 0 until controller.mediaItemCount) {
        val item: MediaItem = controller.getMediaItemAt(i)
        val md = item.mediaMetadata
        out += Video(
            id = item.mediaId,
            title = md.title?.toString().orEmpty(),
            channelName = md.artist?.toString().orEmpty(),
            channelId = "",
            thumbnailUrl = md.artworkUri?.toString().orEmpty(),
            publishedAt = 0L,
        )
    }
    return out
}
