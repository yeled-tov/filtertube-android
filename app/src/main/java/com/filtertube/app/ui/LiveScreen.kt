package com.filtertube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filtertube.app.ThemeState
import com.filtertube.app.data.ChannelsRepository
import com.filtertube.app.data.LibraryStore
import com.filtertube.app.data.SettingsStore
import com.filtertube.app.data.Video
import com.filtertube.app.data.YouTubeDataApi
import com.filtertube.app.data.forLevel
import kotlinx.coroutines.launch

/**
 * שידורים חיים — מציג אוטומטית שידורים חיים פעילים מהערוצים שאתה עוקב אחריהם,
 * וגם חיפוש שידורים חיים בערוצים המאושרים. (פתיחה דרך קישור חיצוני מטופלת ב-Deep Link.)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(onVideoClick: (Video) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context) }
    val store = remember { LibraryStore(context) }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Video>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    var autoLive by remember { mutableStateOf<List<Video>>(emptyList()) }
    var autoLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        autoLoading = true
        runCatching {
            val approved = ChannelsRepository.getChannels(context).forLevel(settings.filterLevel)
            val subs = approved.filter { store.isSubscribed(it.youtubeChannelId) }
            // עדיפות לערוצים שאתה עוקב אחריהם; אם אין — בודקים תת-קבוצה מהמאושרים (חיסכון במכסה)
            val toCheck = (if (subs.isNotEmpty()) subs else approved).take(20)
            autoLive = YouTubeDataApi.liveFromChannels(toCheck)
        }
        autoLoading = false
    }

    fun runSearch() {
        val q = query.trim()
        if (q.isEmpty()) { searched = false; results = emptyList(); return }
        loading = true; searched = true; error = ""; results = emptyList()
        scope.launch {
            try {
                val channels = ChannelsRepository.getChannels(context).forLevel(settings.filterLevel)
                results = YouTubeDataApi.search(q, channels, live = true)
                if (results.isEmpty()) error = "לא נמצאו שידורים חיים פעילים לחיפוש זה"
            } catch (e: Exception) {
                error = e.message ?: "שגיאה בחיפוש"
            } finally { loading = false }
        }
    }

    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = ThemeState.divider, unfocusedContainerColor = ThemeState.divider,
        focusedTextColor = ThemeState.text, unfocusedTextColor = ThemeState.text,
        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
        cursorColor = ThemeState.accent,
    )

    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        DetailTopBar("שידורים חיים", onBack)

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            TextField(
                value = query,
                onValueChange = { query = it; if (it.isBlank()) { searched = false; results = emptyList() } },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("חפש שידור חי בערוצים המאושרים…", color = ThemeState.subtext) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = ThemeState.subtext) },
                singleLine = true, shape = RoundedCornerShape(18.dp), colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
            )
        }

        when {
            // תוצאות חיפוש (כשמחפשים)
            loading -> CenteredLoading("מחפש שידורים חיים…")
            searched && results.isNotEmpty() -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
            ) { items(results, key = { it.id }) { v -> VideoRow(v, onClick = { onVideoClick(v) }) } }
            searched && error.isNotEmpty() -> CenteredError(error) { runSearch() }

            // אחרת — שידורים חיים אוטומטיים מהערוצים שעוקבים אחריהם
            autoLoading -> CenteredLoading("מחפש שידורים חיים פעילים…")
            autoLive.isNotEmpty() -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(Color(0xFFFF3B30)))
                        Spacer(Modifier.width(8.dp))
                        Text("עכשיו בשידור חי", color = ThemeState.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
                items(autoLive, key = { it.id }) { v -> VideoRow(v, onClick = { onVideoClick(v) }) }
            }
            else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Default.LiveTv, null, tint = ThemeState.subtext, modifier = Modifier.size(46.dp))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "אין כרגע שידורים חיים מהערוצים שאתה עוקב אחריהם.\nאפשר לחפש שידור חי למעלה.",
                        color = ThemeState.subtext, fontSize = 13.sp, lineHeight = 19.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}
