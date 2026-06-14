package com.filtertube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import coil.compose.AsyncImage

@Composable
fun MiniPlayer(
    controller: MediaController?,
    ui: PlayerUiState,
    onOpen: () -> Unit,
) {
    if (!ui.hasMedia || controller == null) return

    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF1A1A1A)).clickable(onClick = onOpen)) {
        val progress = if (ui.duration > 0) (ui.position.toFloat() / ui.duration).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = Color(0xFFFF0000),
            trackColor = Color(0xFF333333),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF272727)),
                contentAlignment = Alignment.Center,
            ) {
                if (ui.artworkUri != null) {
                    AsyncImage(model = ui.artworkUri, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.MusicNote, null, tint = Color(0xFF888888), modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(ui.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(ui.artist, color = Color(0xFFAAAAAA), fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { if (ui.isPlaying) controller.pause() else controller.play() }) {
                Icon(
                    if (ui.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (ui.isPlaying) "השהה" else "נגן", tint = Color.White,
                )
            }
            IconButton(onClick = { controller.stop(); controller.clearMediaItems() }) {
                Icon(Icons.Default.Close, "סגור", tint = Color(0xFFAAAAAA))
            }
        }
    }
}

/** כותרת קצרה של פריט בתור (לרשימת "הבא בתור"). */
fun MediaItem.shortTitle(): String = mediaMetadata.title?.toString() ?: "סרטון"
