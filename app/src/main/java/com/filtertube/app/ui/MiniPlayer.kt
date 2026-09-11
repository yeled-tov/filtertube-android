package com.filtertube.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.filtertube.app.ThemeState
import com.filtertube.app.data.SettingsStore
import com.filtertube.app.playback.RadioQueueManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** מעבר לזה — ההחלקה נחשבת כוונה ולא רעד יד. */
private const val SWIPE_THRESHOLD_DP = 72f

/** כמה פעמים מציגים את רמז ההחלקה לפני שמניחים שהמשתמש הבין. */
private const val HINT_LIMIT = 3

/**
 * המיני-נגן.
 *
 * ## מחוות
 * החלקה שמאלה = השיר הבא, ימינה = הקודם (או תחילת השיר, לפי ההגדרה),
 * גרירה למטה = עצירה מלאה. בפעמים הראשונות הנגן מרטט קלות ימינה-שמאלה
 * כרמז שאפשר להזיז אותו; אחרי שלוש פעמים או אחרי החלקה ראשונה הרמז נעלם,
 * כי רמז שממשיך להופיע אחרי שהובן הופך להסחה.
 *
 * ## כיווניות
 * האפליקציה בעברית, אבל פקדי מדיה וזמן הם תמיד משמאל לימין: 0:00 בשמאל,
 * ההתקדמות זוחלת ימינה. פס ההתקדמות היה מחוץ לבלוק ה-LTR ולכן התמלא הפוך.
 */
@Composable
fun MiniPlayer(
    controller: MediaController?,
    ui: PlayerUiState,
    onOpen: () -> Unit,
) {
    if (!ui.hasMedia || controller == null) return

    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    val density = LocalDensity.current
    val thresholdPx = remember(density) { with(density) { SWIPE_THRESHOLD_DP.dp.toPx() } }

    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var hintsLeft by remember { mutableStateOf(HINT_LIMIT - settings.miniSwipeHintsShown) }
    val gesturesOn = remember { settings.miniPlayerSwipe }

    /** עצירה מלאה — ולא רק של הנגן. */
    fun stopEverything() {
        // ביטול בניית התור חייב לקדום לניקוי: אחרת הבנייה שרצה ברקע מוסיפה
        // פריט מיד אחרי הניקוי, והנגן "קם לתחייה" עם השיר הבא.
        RadioQueueManager.cancel()
        controller.stop()
        controller.clearMediaItems()
    }

    // ── רמז ההחלקה ────────────────────────────────────────────────────────
    LaunchedEffect(ui.mediaId, gesturesOn) {
        if (!gesturesOn || hintsLeft <= 0 || ui.mediaId == null) return@LaunchedEffect
        delay(1200)
        repeat(2) {
            offsetX.animateTo(26f, tween(260))
            offsetX.animateTo(-26f, tween(360))
            offsetX.animateTo(0f, tween(260))
        }
        settings.miniSwipeHintsShown = settings.miniSwipeHintsShown + 1
        hintsLeft -= 1
    }

    // ── מחווה אחת, שני צירים ──────────────────────────────────────────
    // detectDragGestures יחיד ולא שני גלאים נפרדים: שני pointerInput על אותו
    // רכיב מתחרים על אותה אצבע, והראשון שצורך את האירוע מבטל את השני.
    // הציר נקבע בסיום לפי מי מהם זז יותר.
    val gestureModifier = if (!gesturesOn) Modifier else Modifier.pointerInput(ui.mediaId) {
        detectDragGestures(
            onDragEnd = {
                val dx = offsetX.value
                val dy = offsetY.value
                if (dy >= thresholdPx && dy > abs(dx)) {
                    stopEverything()
                } else if (abs(dx) >= thresholdPx) {
                    if (dx < 0) controller.seekToNextMediaItem()
                    else if (settings.miniSwipeRightRestarts) controller.seekTo(0L)
                    else controller.seekToPreviousMediaItem()
                    // ברגע שהמשתמש החליק, הרמז מיותר.
                    settings.miniSwipeHintsShown = HINT_LIMIT
                    hintsLeft = 0
                }
                scope.launch {
                    offsetX.animateTo(0f, tween(180))
                    offsetY.animateTo(0f, tween(180))
                }
            },
            onDragCancel = {
                scope.launch {
                    offsetX.animateTo(0f, tween(180))
                    offsetY.animateTo(0f, tween(180))
                }
            },
        ) { change, drag ->
            change.consume()
            scope.launch {
                // ההזזה חסומה: המשתמש מקבל משוב, לא גרירה חופשית של הרכיב
                // אל מחוץ למסך. כלפי מעלה אין תנועה — זה שמור לפתיחת הנגן.
                offsetX.snapTo((offsetX.value + drag.x).coerceIn(-thresholdPx * 1.6f, thresholdPx * 1.6f))
                offsetY.snapTo((offsetY.value + drag.y).coerceIn(0f, thresholdPx * 1.5f))
            }
        }
    }

    Column(
        modifier = Modifier
            .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ThemeState.card)
            .border(1.dp, ThemeState.divider, RoundedCornerShape(18.dp))
            .then(gestureModifier)
            .clickable(onClick = onOpen),
    ) {
        // הכל כאן משמאל לימין — כולל פס ההתקדמות, שהיה קודם מחוץ לבלוק.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            val progress = if (ui.duration > 0) (ui.position.toFloat() / ui.duration).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = ThemeState.accent,
                trackColor = Color(0xFF333333),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(50.dp).clip(RoundedCornerShape(13.dp)).background(ThemeState.divider),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!ui.isAudio) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = false
                                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                                    setBackgroundColor(android.graphics.Color.BLACK)
                                }
                            },
                            update = { it.player = controller },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (ui.artworkUri != null) {
                        AsyncImage(
                            model = ui.artworkUri, contentDescription = null,
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(Icons.Default.MusicNote, null, tint = ThemeState.subtext, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        ui.title, color = ThemeState.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        ui.artist, color = ThemeState.subtext2, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { controller.seekToPreviousMediaItem() }, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.SkipPrevious, "שיר קודם", tint = ThemeState.text)
                }
                IconButton(
                    onClick = { if (ui.isPlaying) controller.pause() else controller.play() },
                    modifier = Modifier.size(42.dp),
                ) {
                    Icon(
                        if (ui.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (ui.isPlaying) "השהה" else "נגן", tint = ThemeState.text,
                    )
                }
                IconButton(onClick = { controller.seekToNextMediaItem() }, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.SkipNext, "שיר הבא", tint = ThemeState.text)
                }
                IconButton(onClick = { stopEverything() }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Close, "עצור וסגור", tint = ThemeState.subtext2, modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

/** כותרת קצרה של פריט בתור (לרשימת "הבא בתור"). */
fun MediaItem.shortTitle(): String = mediaMetadata.title?.toString() ?: "סרטון"
