package com.filtertube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filtertube.app.ThemeState
import com.filtertube.app.data.LibraryStore
import com.filtertube.app.data.Video

/** מסך "סרטונים חדשים" — מה שבדיקת הרקע מצאה בערוצים שאתה עוקב אחריהם. */
@Composable
fun NewVideosScreen(onVideoClick: (Video) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { LibraryStore(context) }
    var refreshKey by remember { mutableStateOf(0) }
    val videos = remember(refreshKey) { store.newVideos() }

    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, start = 4.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזור", tint = ThemeState.text)
            }
            Text("סרטונים חדשים (${videos.size})", color = ThemeState.text, fontSize = 18.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (videos.isNotEmpty()) {
                TextButton(onClick = { store.clearNewVideos(); refreshKey++ }) {
                    Text("נקה", color = Color(0xFFFF6A5C), fontSize = 13.sp)
                }
            }
        }
        HorizontalDivider(color = ThemeState.divider)

        if (videos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Text("אין סרטונים חדשים כרגע", color = ThemeState.subtext2, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text("עקוב אחרי ערוצים (כפתור ״עקוב״ במסך הערוץ) ותקבל כאן את הסרטונים החדשים שלהם.",
                        color = ThemeState.subtext, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
                items(videos, key = { it.id }) { v -> VideoRow(v, onClick = { onVideoClick(v) }) }
            }
        }
    }
}
