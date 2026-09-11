package com.filtertube.app.ui.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.filtertube.app.ThemeState
import com.filtertube.app.data.LibraryStore
import com.filtertube.app.data.Video
import com.filtertube.app.ui.PlayerUiState
import kotlinx.coroutines.delay

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
    var showQueue by remember { mutableStateOf(false) }
    var liked by remember(ui.mediaId) {
        mutableStateOf(ui.mediaId?.let { store.isLiked(it) } == true)
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

    Column(
        modifier = Modifier.fillMaxSize().background(ThemeState.bg)
            .statusBarsPadding().navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Default.KeyboardArrowDown, "סגור", tint = ThemeState.text, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Tune, "הגדרות", tint = ThemeState.subtext2)
            }
        }

        Spacer(Modifier.weight(1f))

        // ── הכריכה ────────────────────────────────────────────────────────
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MusicDim.playerPadding),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints {
                SongArt(current, maxWidth, corner = 12.dp)
            }
        }

        Spacer(Modifier.height(28.dp))

        // ── כותרת ואמן ────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MusicDim.playerPadding),
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
            IconButton(onClick = {
                val id = ui.mediaId ?: return@IconButton
                liked = store.toggleLike(current)
                com.filtertube.app.data.LibraryBadges.setLiked(id, liked)
            }) {
                Icon(
                    if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    "אהבתי", tint = if (liked) ThemeState.accent else ThemeState.subtext2,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── סרגל ההתקדמות ─────────────────────────────────────────────────
        Column(modifier = Modifier.padding(horizontal = MusicDim.playerPadding)) {
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
                Text(formatTime(position), color = ThemeState.subtext, fontSize = 11.5.sp)
                Spacer(Modifier.weight(1f))
                Text(formatTime(ui.duration), color = ThemeState.subtext, fontSize = 11.5.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── הבקרים ────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = MusicDim.playerPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = {
                shuffle = !shuffle
                controller?.shuffleModeEnabled = shuffle
            }) {
                Icon(
                    Icons.Default.Shuffle, "ערבוב",
                    tint = if (shuffle) ThemeState.accent else ThemeState.subtext2,
                )
            }
            IconButton(onClick = { controller?.seekToPreviousMediaItem() }, enabled = ui.hasPrev) {
                Icon(
                    Icons.Default.SkipPrevious, "הקודם", modifier = Modifier.size(36.dp),
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
                        if (ui.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (ui.isPlaying) "השהה" else "נגן",
                        tint = Color.White, modifier = Modifier.size(34.dp),
                    )
                }
            }
            IconButton(onClick = { controller?.seekToNextMediaItem() }, enabled = ui.hasNext) {
                Icon(
                    Icons.Default.SkipNext, "הבא", modifier = Modifier.size(36.dp),
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
                    if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    "חזרה",
                    tint = if (repeatMode == Player.REPEAT_MODE_OFF) ThemeState.subtext2 else ThemeState.accent,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // ── רצועת התור ────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable { showQueue = true }
                .background(ThemeState.surface)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = ThemeState.text, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("הבא בתור", color = ThemeState.text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
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
