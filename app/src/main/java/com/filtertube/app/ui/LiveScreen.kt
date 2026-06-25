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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filtertube.app.ThemeState
import com.filtertube.app.data.ChannelsRepository
import com.filtertube.app.data.SettingsStore
import com.filtertube.app.data.Video
import com.filtertube.app.data.YouTubeDataApi
import com.filtertube.app.data.forLevel
import kotlinx.coroutines.launch

/** שידורים חיים — פתיחה עם קישור, או חיפוש שידורים חיים פעילים בערוצים המאושרים. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScreen(onVideoClick: (Video) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context) }

    var url by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Video>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    fun extractId(u: String): String? {
        for (p in listOf(
            "[?&]v=([A-Za-z0-9_-]{11})", "youtu\\.be/([A-Za-z0-9_-]{11})",
            "/live/([A-Za-z0-9_-]{11})", "/shorts/([A-Za-z0-9_-]{11})", "/embed/([A-Za-z0-9_-]{11})",
        )) Regex(p).find(u)?.let { return it.groupValues[1] }
        return u.trim().takeIf { Regex("^[A-Za-z0-9_-]{11}$").matches(it) }
    }

    fun openUrl() {
        val id = extractId(url) ?: run { error = "קישור לא תקין"; return }
        onVideoClick(Video(id, "שידור חי", "", "", "https://i.ytimg.com/vi/$id/hqdefault.jpg", System.currentTimeMillis()))
    }

    fun runSearch() {
        val q = query.trim(); if (q.isEmpty()) return
        loading = true; searched = true; error = ""; results = emptyList()
        scope.launch {
            try {
                val channels = ChannelsRepository.getChannels(context).forLevel(settings.filterLevel)
                results = YouTubeDataApi.search(q, channels, live = true)
                if (results.isEmpty()) error = "לא נמצאו שידורים חיים פעילים בערוצים המאושרים"
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

        Column(modifier = Modifier.padding(16.dp)) {
            Text("פתח שידור עם קישור", color = ThemeState.subtext2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = url, onValueChange = { url = it; error = "" },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("הדבק קישור יוטיוב…", color = ThemeState.subtext) },
                    singleLine = true, shape = RoundedCornerShape(18.dp), colors = fieldColors,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { openUrl() }),
                )
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = { openUrl() },
                    enabled = url.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = ThemeState.accent),
                ) { Text("פתח") }
            }

            Spacer(Modifier.height(18.dp))
            Text("חיפוש שידורים חיים", color = ThemeState.subtext2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            TextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("חפש שידור חי בערוצים המאושרים…", color = ThemeState.subtext) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = ThemeState.subtext) },
                singleLine = true, shape = RoundedCornerShape(18.dp), colors = fieldColors,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
            )
        }

        when {
            loading -> CenteredLoading("מחפש שידורים חיים…")
            error.isNotEmpty() && results.isEmpty() -> CenteredError(error) { runSearch() }
            results.isNotEmpty() -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
            ) {
                items(results, key = { it.id }) { v -> VideoRow(v, onClick = { onVideoClick(v) }) }
            }
            !searched -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Icon(Icons.Default.LiveTv, null, tint = ThemeState.subtext, modifier = Modifier.size(46.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("הדבק קישור לשידור חי, או חפש שידור חי מהערוצים המאושרים.",
                        color = ThemeState.subtext, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}
