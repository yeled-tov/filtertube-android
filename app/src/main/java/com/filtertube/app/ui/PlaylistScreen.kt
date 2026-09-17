package com.filtertube.app.ui
import com.filtertube.app.ThemeState

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
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
import com.filtertube.app.data.ApprovedChannels
import com.filtertube.app.data.ChannelsRepository
import com.filtertube.app.data.LibraryBadges
import com.filtertube.app.data.LibraryStore
import com.filtertube.app.data.SettingsStore
import com.filtertube.app.data.Video
import com.filtertube.app.data.forLevel

/**
 * אלבום שהמשתמש בנה בעצמו.
 *
 * ## למה גם כאן יש בדיקת רשימה לבנה
 * שיר נכנס לאלבום ברמת הסינון ששררה באותו רגע, והאלבום נשמר לנצח. אם
 * ההורה הוריד אחר כך את רמת הסינון, השיר נשאר באלבום — ועד עכשיו גם נשאר
 * ניתן לניגון. הרשימה הלבנה נבדקת בכל מסך בנפרד, וזה המסך שבו היא לא
 * נבדקה. אפור אינו דלת: הלחיצה מגיעה לטופס הבקשה ולא לנגן.
 */
@Composable
fun PlaylistScreen(name: String, onVideoClick: (Video) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { LibraryStore(context) }
    val settings = remember { SettingsStore(context) }
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
    val saved = remember { store.playlists().firstOrNull { it.name == name }?.videos ?: emptyList() }
    // סרטון שהמשתמש חסם לעצמו לא מוצג באף אוסף, וגם לא כאן.
    val videos = remember(saved, LibraryBadges.blocked) {
        saved.filter { it.id !in LibraryBadges.blocked }
    }

    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, start = 4.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "חזור", tint = ThemeState.text) }
            Text(name, color = ThemeState.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { store.deletePlaylist(name); onBack() }) {
                Icon(Icons.Rounded.Delete, "מחק אלבום", tint = Color(0xFFFF0000))
            }
        }
        HorizontalDivider(color = ThemeState.divider)

        if (videos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("האלבום ריק — הוסף שירים מתוך הנגן", color = ThemeState.subtext, fontSize = 13.sp)
            }
        } else {
            val greyed = videos.count { !approved.isEmpty() && !approved.approves(it) }
            if (greyed > 0) {
                Text(
                    "$greyed באפור — מערוצים שאינם מותרים ברמת הסינון הנוכחית. " +
                        "לחיצה עליהם שולחת בקשה להוסיף.",
                    color = ThemeState.subtext2, fontSize = 12.sp, lineHeight = 17.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
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
