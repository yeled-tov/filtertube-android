package com.filtertube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filtertube.app.data.ChannelsRepository
import com.filtertube.app.data.SettingsStore
import com.filtertube.app.data.Video
import com.filtertube.app.data.YouTubeRepository
import com.filtertube.app.data.forLevel
import kotlinx.coroutines.launch

sealed class SearchState {
    data object Idle : SearchState()
    data object Loading : SearchState()
    data class Results(val videos: List<Video>) : SearchState()
    data class Error(val message: String) : SearchState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onVideoClick: (Video) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context) }
    val keyboard = LocalSoftwareKeyboardController.current

    var query by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<SearchState>(SearchState.Idle) }
    var history by remember { mutableStateOf(settings.getSearchHistory()) }

    fun runSearch(q: String) {
        val trimmed = q.trim()
        if (trimmed.isEmpty()) return
        keyboard?.hide()
        settings.addSearchQuery(trimmed)
        history = settings.getSearchHistory()
        state = SearchState.Loading
        scope.launch {
            state = try {
                val channels = ChannelsRepository.getChannels(context).forLevel(settings.filterLevel)
                val results = YouTubeRepository.search(trimmed, channels)
                if (results.isEmpty()) SearchState.Error("לא נמצאו תוצאות בערוצים המאושרים")
                else SearchState.Results(results)
            } catch (e: Exception) {
                SearchState.Error(e.message ?: "שגיאה בחיפוש")
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F))) {
        // Search bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 28.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("חפש בערוצים המאושרים...", color = Color(0xFF888888)) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFF888888)) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = ""; state = SearchState.Idle }) {
                            Icon(Icons.Default.Close, "נקה", tint = Color(0xFF888888))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch(query) }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF272727),
                    unfocusedContainerColor = Color(0xFF272727),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = Color(0xFFFF0000),
                ),
            )
        }

        when (val s = state) {
            is SearchState.Idle -> SearchHistory(
                history = history,
                onPick = { query = it; runSearch(it) },
                onRemove = { settings.removeSearchQuery(it); history = settings.getSearchHistory() },
                onClear = { settings.clearSearchHistory(); history = emptyList() },
            )
            is SearchState.Loading -> CenteredLoading("מחפש...")
            is SearchState.Error -> CenteredError(s.message) { runSearch(query) }
            is SearchState.Results -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                items(s.videos, key = { it.id }) { video ->
                    VideoRow(video, onClick = { onVideoClick(video) })
                }
            }
        }
    }
}

@Composable
private fun SearchHistory(
    history: List<String>,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    if (history.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Search, null, tint = Color(0xFF444444), modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("חפש סרטונים בערוצים המאושרים", color = Color(0xFF888888), fontSize = 14.sp)
            }
        }
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("היסטוריית חיפוש", color = Color(0xFFAAAAAA), fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("נקה הכל", color = Color(0xFFFF0000), fontSize = 12.sp) }
        }
        LazyColumn {
            items(history) { q ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPick(q) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.History, null, tint = Color(0xFF888888), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(q, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRemove(q) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "הסר", tint = Color(0xFF666666), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
