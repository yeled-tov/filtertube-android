package com.filtertube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filtertube.app.ThemeState
import com.filtertube.app.data.Channel
import com.filtertube.app.data.ChannelsRepository
import com.filtertube.app.data.LibraryStore
import com.filtertube.app.data.SettingsStore
import com.filtertube.app.data.categoryLabelHe
import com.filtertube.app.data.forLevel
import com.filtertube.app.data.sortedCategories

/** רשימת כל הערוצים המאושרים (מקובצת לפי קטגוריה) עם כפתור "עקוב" לכל ערוץ. */
@Composable
fun ChannelsBrowseScreen(onBack: () -> Unit, onOpenChannel: (String, String) -> Unit = { _, _ -> }) {
    val context = LocalContext.current
    val store = remember { LibraryStore(context) }
    val settings = remember { SettingsStore(context) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var version by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        channels = runCatching {
            ChannelsRepository.getChannels(context).forLevel(settings.filterLevel)
        }.getOrNull().orEmpty().sortedBy { it.name }
    }

    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        DetailTopBar("ערוצים — עקוב לקבלת התראות", onBack)
        if (channels.isEmpty()) {
            CenteredLoading("טוען ערוצים...")
        } else {
            val byCat = channels.groupBy { it.category }
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)) {
                sortedCategories(channels.map { it.category }).forEach { cat ->
                    val list = byCat[cat].orEmpty()
                    item(key = "h_$cat") {
                        Text(
                            "${categoryLabelHe(cat)} (${list.size})",
                            color = ThemeState.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(list, key = { it.youtubeChannelId }) { ch ->
                        ChannelFollowRow(ch, store, version,
                            onOpenChannel = { onOpenChannel(ch.youtubeChannelId, ch.name) }) { version++ }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelFollowRow(ch: Channel, store: LibraryStore, version: Int, onOpenChannel: () -> Unit, onChange: () -> Unit) {
    var subscribed by remember(ch.youtubeChannelId, version) { mutableStateOf(store.isSubscribed(ch.youtubeChannelId)) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onOpenChannel() }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(50))
                .background(Brush.linearGradient(ThemeState.accentColors)),
            contentAlignment = Alignment.Center,
        ) {
            Text(ch.name.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Text(ch.name, color = ThemeState.text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        if (subscribed) {
            OutlinedButton(onClick = { subscribed = store.toggleSubscription(ch.youtubeChannelId); onChange() }) {
                Text("עוקב ✓", color = ThemeState.text, fontSize = 13.sp)
            }
        } else {
            Button(
                onClick = { subscribed = store.toggleSubscription(ch.youtubeChannelId); onChange() },
                colors = ButtonDefaults.buttonColors(containerColor = ThemeState.accent),
            ) { Text("עקוב", color = Color.White, fontSize = 13.sp) }
        }
    }
}
