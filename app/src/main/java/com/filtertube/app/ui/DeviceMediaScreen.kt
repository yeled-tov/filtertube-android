package com.filtertube.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filtertube.app.ThemeState
import com.filtertube.app.data.DeviceMedia
import com.filtertube.app.data.toVideo
import com.filtertube.app.data.DownloadEngine
import com.filtertube.app.data.Video
import kotlinx.coroutines.launch

/** מה מוצג ברשימה. */
private enum class MediaFilter(val label: String) {
    ALL("הכול"),
    MUSIC("מוזיקה"),
    VIDEO("סרטונים"),
    FILTERTUBE("ההורדות שלי"),
}

/**
 * נגן המדיה של המכשיר — כל האודיו והווידאו ששמורים על הטלפון.
 *
 * זה לא מסך יוטיוב: הפריטים כאן הם קבצים שכבר נמצאים אצל המשתמש, ולכן אין
 * עליהם סינון רשימה לבנה ואין להם ערוץ. לחיצה מנגנת את *כל הרשימה הנראית*
 * כתור, כדי שהמעבר לשיר הבא, פקדי המסך הנעול והמיני-פלייר יעבדו כרגיל.
 */
@Composable
fun DeviceMediaScreen(
    onBack: () -> Unit,
    onPlayList: (List<Video>, Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var granted by remember { mutableStateOf(DeviceMedia.hasPermission(context)) }
    var loading by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(false) }
    var all by remember { mutableStateOf<List<DeviceMedia.Item>>(emptyList()) }
    var filter by remember { mutableStateOf(MediaFilter.ALL) }

    fun rescan() {
        loading = true
        scope.launch {
            all = DeviceMedia.scan(context)
            loading = false
            scanned = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        // די בהרשאה אחת שאושרה: משתמש שנתן גישה למוזיקה בלבד עדיין יקבל
        // רשימת מוזיקה, ולא מסך ריק.
        granted = result.values.any { it } || DeviceMedia.hasPermission(context)
        if (granted) rescan()
    }

    LaunchedEffect(granted) { if (granted && !scanned) rescan() }

    // הסינון עצמו זול, אבל אין סיבה להריץ אותו בכל recomposition של גלילה.
    val shown = remember(all, filter) {
        when (filter) {
            MediaFilter.ALL -> all
            MediaFilter.MUSIC -> all.filter { !it.isVideo }
            MediaFilter.VIDEO -> all.filter { it.isVideo }
            MediaFilter.FILTERTUBE -> all.filter { it.folder.equals(DownloadEngine.FOLDER, ignoreCase = true) }
        }
    }
    val queue = remember(shown) { shown.map { it.toVideo() } }

    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        DetailTopBar("במכשיר שלי", onBack, action = {
            if (granted) {
                IconButton(onClick = { rescan() }, enabled = !loading) {
                    Icon(Icons.Default.Refresh, contentDescription = "רענון", tint = ThemeState.subtext2)
                }
            }
        })

        if (!granted) {
            PermissionPrompt { permissionLauncher.launch(DeviceMedia.requiredPermissions()) }
            return@Column
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(MediaFilter.entries.toList()) { option ->
                val selected = option == filter
                FilterChip(
                    selected = selected,
                    onClick = { filter = option },
                    label = { Text(option.label, fontSize = 13.sp) },
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

        when {
            loading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = ThemeState.accent)
            }

            shown.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                Text(
                    if (all.isEmpty()) {
                        "לא נמצאו קבצי מדיה על המכשיר.\nכל שיר או סרטון שיישמר בטלפון יופיע כאן."
                    } else {
                        "אין פריטים בקטגוריה הזו."
                    },
                    color = ThemeState.subtext, fontSize = 13.5.sp, lineHeight = 20.sp,
                )
            }

            else -> LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 120.dp)) {
                item {
                    Text(
                        "${shown.count { !it.isVideo }} שירים · ${shown.count { it.isVideo }} סרטונים",
                        color = ThemeState.subtext, fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                itemsIndexed(shown) { index, media ->
                    DeviceMediaRow(media) { onPlayList(queue, index) }
                }
            }
        }
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("נגן המכשיר", color = ThemeState.text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            "כדי לנגן מוזיקה וסרטונים ששמורים על הטלפון, FilterTube צריכה הרשאת " +
                "קריאה למדיה. ההרשאה משמשת רק לקריאה מקומית — שום קובץ לא נשלח לשום מקום.",
            color = ThemeState.subtext2, fontSize = 13.5.sp, lineHeight = 20.sp,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onRequest,
            modifier = Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)),
            colors = ButtonDefaults.buttonColors(containerColor = ThemeState.accent),
        ) { Text("אפשר גישה למדיה", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun DeviceMediaRow(media: DeviceMedia.Item, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ThemeState.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                .background(if (media.isVideo) Color(0xFF3B82F6) else Color(0xFF10B981)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (media.isVideo) Icons.Default.Videocam else Icons.Default.MusicNote,
                contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                media.title, color = ThemeState.text, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null, tint = ThemeState.subtext, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    listOf(media.artist, media.folder).filter { it.isNotBlank() }.joinToString(" · "),
                    color = ThemeState.subtext, fontSize = 11.5.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(formatDuration(media.durationSec), color = ThemeState.subtext, fontSize = 11.5.sp)
    }
}

private fun formatDuration(sec: Long): String {
    if (sec <= 0) return ""
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
