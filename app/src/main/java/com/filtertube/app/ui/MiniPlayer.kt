package com.filtertube.app.ui
import com.filtertube.app.ThemeState

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage

@Composable
fun MiniPlayer(
    controller: MediaController?,
    ui: PlayerUiState,
    onOpen: () -> Unit,
) {
    if (!ui.hasMedia || controller == null) return

    Column(
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ThemeState.card)
            .border(1.dp, ThemeState.divider, RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen),
    ) {
        val progress = if (ui.duration > 0) (ui.position.toFloat() / ui.duration).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(3.dp),
            color = ThemeState.accent,
            trackColor = Color(0xFF333333),
        )
        // Controls stay physically LTR even though the rest of the app is Hebrew/RTL.
        androidx.compose.runtime.CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
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
                        factory = { ctx -> PlayerView(ctx).apply {
                            useController = false
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            setBackgroundColor(android.graphics.Color.BLACK)
                        } },
                        update = { it.player = controller },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (ui.artworkUri != null) {
                    AsyncImage(model = ui.artworkUri, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.MusicNote, null, tint = ThemeState.subtext, modifier = Modifier.size(20.dp))
                }
            }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(ui.title, color = ThemeState.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(ui.artist, color = ThemeState.subtext2, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { controller.seekToPreviousMediaItem() }, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.SkipPrevious, "שיר קודם", tint = ThemeState.text)
                }
                IconButton(onClick = { if (ui.isPlaying) controller.pause() else controller.play() }, modifier = Modifier.size(42.dp)) {
                    Icon(
                        if (ui.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (ui.isPlaying) "השהה" else "נגן", tint = ThemeState.text,
                    )
                }
                IconButton(onClick = { controller.seekToNextMediaItem() }, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Default.SkipNext, "שיר הבא", tint = ThemeState.text)
                }
                IconButton(onClick = { controller.stop(); controller.clearMediaItems() }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Close, "סגור", tint = ThemeState.subtext2, modifier = Modifier.size(19.dp))
                }
            }
        }
    }
}

/** כותרת קצרה של פריט בתור (לרשימת "הבא בתור"). */
fun MediaItem.shortTitle(): String = mediaMetadata.title?.toString() ?: "סרטון"
