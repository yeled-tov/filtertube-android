package com.filtertube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filtertube.app.data.LibraryStore
import com.filtertube.app.data.Video

@Composable
fun PlaylistScreen(name: String, onVideoClick: (Video) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { LibraryStore(context) }
    val videos = remember { store.playlists().firstOrNull { it.name == name }?.videos ?: emptyList() }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F))) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp, start = 4.dp, end = 16.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזור", tint = Color.White) }
            Text(name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { store.deletePlaylist(name); onBack() }) {
                Icon(Icons.Default.Delete, "מחק אלבום", tint = Color(0xFFFF0000))
            }
        }
        HorizontalDivider(color = Color(0xFF272727))

        if (videos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("האלבום ריק — הוסף שירים מתוך הנגן", color = Color(0xFF888888), fontSize = 13.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(videos, key = { it.id }) { v -> VideoRow(v, onClick = { onVideoClick(v) }) }
            }
        }
    }
}
