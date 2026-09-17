package com.filtertube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.alpha
import com.filtertube.app.ThemeState
import com.filtertube.app.data.ApprovedChannels
import com.filtertube.app.data.ChannelsRepository
import com.filtertube.app.data.LibraryBadges
import com.filtertube.app.data.LibraryStore
import com.filtertube.app.data.SettingsStore
import com.filtertube.app.data.Video
import com.filtertube.app.data.forLevel

/**
 * מסך "סרטונים חדשים" — מה שבדיקת הרקע מצאה בערוצים שאתה עוקב אחריהם.
 *
 * ## למה נבדקת כאן הרשימה הלבנה שוב
 * הרשימה נבנית בבדיקת הרקע, שמסננת לפי רמת הסינון **שהייתה באותו רגע**
 * (ובלי סינון מגדר בכלל), והיא נשמרת עד שמנקים אותה. הורה שהוריד אחר כך
 * את רמת הסינון היה מוצא כאן סרטונים שכבר אינם מותרים — ופתוחים לניגון.
 * הבדיקה כאן היא לפי המצב הנוכחי, ולכן שינוי רמה תופס מיד.
 */
@Composable
fun NewVideosScreen(onVideoClick: (Video) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { LibraryStore(context) }
    val settings = remember { SettingsStore(context) }
    var refreshKey by remember { mutableStateOf(0) }
    var approved by remember { mutableStateOf(ApprovedChannels(emptyList())) }
    var requestFor by remember { mutableStateOf<Video?>(null) }
    LaunchedEffect(Unit) {
        approved = ApprovedChannels(
            runCatching {
                ChannelsRepository.getChannels(context)
                    .forLevel(settings.filterLevel, settings.userGender)
            }.getOrDefault(emptyList()),
        )
    }
    val stored = remember(refreshKey) { store.newVideos() }
    val videos = remember(stored, LibraryBadges.blocked) {
        stored.filter { it.id !in LibraryBadges.blocked }
    }

    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, start = 4.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "חזור", tint = ThemeState.text)
            }
            Text("סרטונים חדשים (${videos.size})", color = ThemeState.text, fontSize = 18.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (videos.isNotEmpty()) {
                TextButton(onClick = { store.clearNewVideos(); refreshKey++ }) {
                    Text("נקה", color = ThemeState.accent2, fontSize = 13.sp)
                }
            }
        }
        HorizontalDivider(color = ThemeState.divider)

        if (videos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Text("אין סרטונים חדשים כרגע", color = ThemeState.subtext2, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text("פתח את התפריט (כפתור הפרופיל למעלה) → ״ערוצים״, ועקוב אחרי ערוצים — " +
                        "הסרטונים החדשים שלהם יופיעו כאן ותקבל עליהם התראה.",
                        color = ThemeState.subtext, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
                items(videos, key = { it.id }) { v ->
                    if (approved.isEmpty() || approved.approves(v)) {
                        VideoRow(v, onClick = { onVideoClick(v) })
                    } else {
                        Box(Modifier.alpha(0.45f)) {
                            VideoRow(v, onClick = { requestFor = v })
                        }
                    }
                }
            }
        }
    }

    requestFor?.let { target ->
        ChannelRequestDialog(
            onDismiss = { requestFor = null },
            prefillName = target.channelName.ifBlank { target.title },
            prefillUrl = "https://www.youtube.com/channel/${target.channelId}",
        )
    }
}
