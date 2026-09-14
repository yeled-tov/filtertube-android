package com.filtertube.app.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.session.MediaController
import com.filtertube.app.ThemeState
import com.filtertube.app.ui.PlayerUiState
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * התור — ניתן לגרירה, למחיקה ולערבוב.
 *
 * ## למה הרשימה מוחזקת במצב מקומי
 * אפשר היה לקרוא את התור מהנגן בכל פריים, אבל אז הפריט הנגרר היה קופץ
 * חזרה בכל עדכון שמגיע מהשירות. לכן המצב המקומי הוא מקור האמת בזמן
 * הגרירה, והנגן מסונכרן אליו מיד אחרי — `moveMediaItem` שומר על הניגון
 * הנוכחי גם כשהאינדקס שלו זז.
 */
@Composable
fun MusicQueueSheet(
    controller: MediaController,
    ui: PlayerUiState,
    onDismiss: () -> Unit,
) {
    // ── מפתחות יציבים ─────────────────────────────────────────────────
    // מזהה הסרטון לבדו לא מספיק: אותו שיר יכול להופיע פעמיים בתור, ומפתח
    // כפול ב-LazyColumn מפיל את המסך. מצרפים מונה הופעות — ולא את
    // האינדקס, כי אינדקס משתנה בדיוק כשגוררים, וזה היה הורס את האנימציה
    // ואת הזהות של הפריט הנגרר באמצע הגרירה.
    var queue by remember(ui.queueVersion) {
        mutableStateOf(withStableKeys(readQueue(controller)))
    }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        // מזיזים קודם מקומית כדי שהאנימציה תהיה חלקה, ואז בנגן.
        queue = queue.toMutableList().apply { add(to.index, removeAt(from.index)) }
        controller.moveMediaItem(from.index, to.index)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier.fillMaxSize().background(ThemeState.bg)
                .statusBarsPadding().navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("הבא בתור", color = ThemeState.text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${queue.size} שירים · גרור כדי לסדר מחדש", color = ThemeState.subtext, fontSize = 11.5.sp)
                }
                IconButton(onClick = {
                    controller.shuffleModeEnabled = !controller.shuffleModeEnabled
                }) {
                    Icon(
                        Icons.Default.Shuffle, "ערבוב",
                        tint = if (controller.shuffleModeEnabled) ThemeState.accent else ThemeState.subtext2,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "סגור", tint = ThemeState.text)
                }
            }

            androidx.compose.foundation.lazy.LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(count = queue.size, key = { index -> queue[index].first }) { index ->
                    val song = queue[index].second
                    ReorderableItem(reorderState, key = queue[index].first) { dragging ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .background(if (dragging) ThemeState.card else Color.Transparent),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SongListItem(
                                video = song,
                                active = song.id == ui.mediaId,
                                playing = song.id == ui.mediaId && ui.isPlaying,
                                modifier = Modifier.weight(1f),
                                onClick = { controller.seekTo(index, 0L) },
                                trailing = {
                                    IconButton(onClick = {
                                        // הסרה מהתור. הנגן מטפל לבד במקרה
                                        // שמסירים את הפריט שמתנגן כרגע.
                                        controller.removeMediaItem(index)
                                        queue = queue.toMutableList().apply { removeAt(index) }
                                    }) {
                                        Icon(
                                            Icons.Default.Close, "הסר מהתור",
                                            tint = ThemeState.subtext, modifier = Modifier.size(18.dp),
                                        )
                                    }
                                },
                            )
                            Box(
                                modifier = Modifier.draggableHandle()
                                    .padding(horizontal = 10.dp),
                            ) {
                                Icon(
                                    Icons.Default.DragHandle, "גרור",
                                    tint = ThemeState.subtext, modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * נותן לכל פריט בתור מפתח ייחודי ויציב.
 *
 * שיר שמופיע פעמיים מקבל "id#0" ו-"id#1". המפתח נוסע עם הפריט כשגוררים
 * אותו, ולכן הגרירה חלקה ולא מאבדת את הזהות באמצע.
 */
private fun withStableKeys(videos: List<com.filtertube.app.data.Video>): List<Pair<String, com.filtertube.app.data.Video>> {
    val seen = HashMap<String, Int>()
    return videos.map { video ->
        val n = seen.getOrDefault(video.id, 0)
        seen[video.id] = n + 1
        "${video.id}#$n" to video
    }
}
