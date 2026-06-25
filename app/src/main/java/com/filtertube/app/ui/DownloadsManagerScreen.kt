package com.filtertube.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.filtertube.app.data.DownloadEngine
import com.filtertube.app.data.DownloadTask
import com.filtertube.app.data.LibraryStore
import com.filtertube.app.data.SettingsStore
import kotlinx.coroutines.launch

/** מנהל הורדות — הורדת כל ה"אהבתי", הורדה אוטומטית, מהירות (חיבורים/מקביליות), ומעקב. */
@Composable
fun DownloadsManagerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context) }
    val store = remember { LibraryStore(context) }

    var concurrent by remember { mutableStateOf(settings.concurrentDownloads.toFloat()) }
    var connections by remember { mutableStateOf(settings.connectionsPerDownload.toFloat()) }
    var autoLikes by remember { mutableStateOf(settings.autoDownloadLikes) }
    var queuing by remember { mutableStateOf(false) }
    val likesCount = remember { store.likes().size }
    val active = DownloadEngine.active

    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        DetailTopBar("מנהל הורדות", onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp)) {
            item {
                Button(
                    onClick = onClick@{
                        if (!settings.premiumActive) {
                            Toast.makeText(context, "הורדות — פיצ'ר פרימיום. ראה הגדרות → Premium", Toast.LENGTH_LONG).show()
                            return@onClick
                        }
                        queuing = true
                        scope.launch {
                            val likes = store.likes()
                            if (likes.isEmpty()) {
                                Toast.makeText(context, "אין סרטונים ב״אהבתי״", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "מוסיף ${likes.size} לתור ההורדות…", Toast.LENGTH_SHORT).show()
                                likes.forEach { DownloadEngine.enqueueByVideo(context, it, isAudio = false) }
                            }
                            queuing = false
                        }
                    },
                    enabled = !queuing,
                    modifier = Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeState.accent),
                ) { Text(if (queuing) "מוסיף לתור…" else "הורד את כל מה שאהבתי ($likesCount)", fontWeight = FontWeight.Bold) }
            }

            item {
                ToggleCard(
                    title = "הורדה אוטומטית של לייקים",
                    subtitle = "כל סרטון שתסמן ״אהבתי״ יירד אוטומטית",
                    checked = autoLikes,
                ) { autoLikes = it; settings.autoDownloadLikes = it }
            }

            item {
                SliderCard("הורדות במקביל", "${concurrent.toInt()} קבצים בו-זמנית",
                    concurrent, 1f..4f, 2) { concurrent = it; settings.concurrentDownloads = it.toInt() }
            }
            item {
                SliderCard("מהירות הורדה (חיבורים לכל קובץ)", "${connections.toInt()} חיבורים · יותר = מהיר יותר",
                    connections, 1f..8f, 6) { connections = it; settings.connectionsPerDownload = it.toInt() }
            }

            item {
                Text("הורדות", color = ThemeState.subtext, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
            }
            if (active.isEmpty()) {
                item { Text("אין הורדות פעילות. הורד סרטון מהנגן או לחץ ״הורד את כל מה שאהבתי״.",
                    color = ThemeState.subtext, fontSize = 12.sp, lineHeight = 17.sp) }
            } else {
                items(active) { task -> DownloadTaskRow(task) }
            }
        }
    }
}

@Composable
private fun DownloadTaskRow(task: DownloadTask) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(task.video.title.ifBlank { "סרטון" }, color = ThemeState.text, fontSize = 13.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            val statusColor = when (task.status) {
                "הושלם" -> Color(0xFF7CF2C0); "נכשל" -> ThemeState.accent2; else -> ThemeState.subtext2
            }
            Text(if (task.status == "מוריד") "${task.progress}%" else task.status,
                color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { task.progress / 100f },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = ThemeState.accent, trackColor = ThemeState.divider,
        )
    }
}

@Composable
private fun ToggleCard(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(16.dp))
            .background(ThemeState.card).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = ThemeState.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = ThemeState.subtext, fontSize = 11.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = ThemeState.accent,
                uncheckedThumbColor = ThemeState.subtext, uncheckedTrackColor = ThemeState.divider,
            ),
        )
    }
}

@Composable
private fun SliderCard(
    title: String, subtitle: String, value: Float,
    range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(16.dp))
            .background(ThemeState.card).padding(16.dp),
    ) {
        Text(title, color = ThemeState.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Text(subtitle, color = ThemeState.subtext, fontSize = 11.sp)
        Slider(
            value = value, onValueChange = onChange, valueRange = range, steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = ThemeState.accent, activeTrackColor = ThemeState.accent,
                inactiveTrackColor = ThemeState.divider,
            ),
        )
    }
}
