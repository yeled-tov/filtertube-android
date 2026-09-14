package com.filtertube.app.ui
import com.filtertube.app.ThemeState

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
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filtertube.app.data.Channel
import com.filtertube.app.data.ChannelsRepository
import com.filtertube.app.data.Diagnostics
import com.filtertube.app.data.SearchEngine
import com.filtertube.app.data.SettingsStore
import com.filtertube.app.data.Video
import com.filtertube.app.data.YouTubeSuggest
import com.filtertube.app.data.forLevel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

sealed class SearchState {
    data object Idle : SearchState()
    data object Loading : SearchState()
    /** חיפוש תקין שפשוט לא מצא כלום — שונה מ-[Error], שמסמן תקלה. */
    data object Empty : SearchState()
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
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    /** השאילתה שרצה כרגע — מוצגת במסך הטעינה כדי שיהיה ברור מה נקלט. */
    var searching by remember { mutableStateOf("") }
    var showRequest by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        channels = runCatching {
            ChannelsRepository.getChannels(context).forLevel(settings.filterLevel, settings.userGender)
        }.getOrNull().orEmpty()
    }

    // השלמה אוטומטית: קודם שמות ערוצים מאושרים (מיידי, מקומי), ואז הצעות יוטיוב.
    LaunchedEffect(query, channels) {
        val q = query.trim()
        if (q.isEmpty()) { suggestions = emptyList(); return@LaunchedEffect }
        suggestions = SearchEngine.channelSuggestions(channels, q)
        kotlinx.coroutines.delay(220)
        val remote = YouTubeSuggest.suggest(q)
        suggestions = (suggestions + remote).distinct().take(8)
    }

    fun runSearch(q: String) {
        val trimmed = q.trim()
        if (trimmed.isEmpty()) return
        // המצב מתחלף *ראשון*, לפני כל עבודה אחרת. קודם לכן נכתבה קודם
        // היסטוריית החיפוש ל-SharedPreferences ונקראה בחזרה — שתי פעולות דיסק
        // סינכרוניות על תהליכון ה-UI — ורק אחר כך המסך התחלף. בלחיצה על הצעה
        // זה נראה בדיוק כאילו הלחיצה לא נקלטה.
        state = SearchState.Loading
        searching = trimmed
        keyboard?.hide()
        suggestions = emptyList()

        searchJob?.cancel()
        searchJob = scope.launch {
            settings.addSearchQuery(trimmed)
            history = settings.getSearchHistory()
            val approved = channels.ifEmpty {
                val cached = ChannelsRepository.getCachedChannelsFast(context)
                    .forLevel(settings.filterLevel, settings.userGender)
                cached.ifEmpty {
                    runCatching {
                        ChannelsRepository.getChannels(context)
                            .forLevel(settings.filterLevel, settings.userGender)
                    }.getOrNull().orEmpty()
                }.also { channels = it }
            }

            state = try {
                val outcome = SearchEngine.search(context, trimmed, approved) { partial ->
                    if (partial.isNotEmpty()) state = SearchState.Results(partial)
                }
                when {
                    outcome.videos.isNotEmpty() -> SearchState.Results(outcome.videos)
                    // מבחינים בין "אין תוצאות" (מצב תקין) לבין תקלה אמיתית.
                    outcome.failed -> SearchState.Error("לא ניתן לחפש כרגע. בדוק את החיבור לאינטרנט ונסה שוב.")
                    else -> SearchState.Empty
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Diagnostics.log("SEARCH_FINAL_FAILURE query=$trimmed error=${e.message}")
                SearchState.Error(e.message ?: "שגיאה בחיפוש")
            }
        }
    }

    if (showRequest) ChannelRequestDialog(onDismiss = { showRequest = false }, prefillName = query.trim())

    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        // Search bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 28.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = query,
                onValueChange = { query = it; state = SearchState.Idle },
                modifier = Modifier.weight(1f),
                placeholder = { Text("חפש בערוצים המאושרים...", color = ThemeState.subtext) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = ThemeState.subtext) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = ""; state = SearchState.Idle }) {
                            Icon(Icons.Default.Close, "נקה", tint = ThemeState.subtext)
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch(query) }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = ThemeState.divider,
                    unfocusedContainerColor = ThemeState.divider,
                    focusedTextColor = ThemeState.text,
                    unfocusedTextColor = ThemeState.text,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = ThemeState.accent,
                ),
            )
        }

        when (val s = state) {
            is SearchState.Idle ->
                if (query.isBlank()) SearchHistory(
                    history = history,
                    onPick = { query = it; runSearch(it) },
                    onRemove = { settings.removeSearchQuery(it); history = settings.getSearchHistory() },
                    onClear = { settings.clearSearchHistory(); history = emptyList() },
                ) else SuggestionsList(suggestions, query) { picked -> query = picked; runSearch(picked) }
            is SearchState.Loading -> CenteredLoading("מחפש \"$searching\"…")
            is SearchState.Empty -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp),
                ) {
                    Icon(Icons.Default.Search, null, tint = ThemeState.subtext, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("לא נמצאו סרטונים ל\"$query\"", color = ThemeState.text, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "החיפוש מוגבל לערוצים המאושרים בלבד.",
                        color = ThemeState.subtext, fontSize = 12.5f.sp, lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    // הרגע שבו המשתמש הכי רוצה לבקש ערוץ הוא בדיוק כאן — הוא
                    // חיפש משהו ולא מצא. עד עכשיו הוא היה צריך לקרוא משפט,
                    // להבין שקיים מסך "ערוצים", למצוא אותו, ולגלול בו לבאנר.
                    Button(
                        onClick = { showRequest = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ThemeState.accent),
                    ) { Text("בקש להוסיף ערוץ", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                }
            }
            is SearchState.Error -> CenteredError(s.message) { runSearch(query) }
            is SearchState.Results -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp),
            ) {
                items(s.videos, key = { it.id }) { video ->
                    VideoRow(video, onClick = { onVideoClick(video) })
                }
            }
        }
    }
}

/**
 * הצעות השלמה — לא תוצאות חיפוש.
 *
 * בלי הכותרת והחץ הרשימה הזו נראתה כמו רשימת התוצאות עצמה, והמשתמש חשב
 * שהחיפוש כבר רץ ושהלחיצה שלו לא עושה כלום.
 */
@Composable
private fun SuggestionsList(suggestions: List<String>, query: String, onPick: (String) -> Unit) {
    if (suggestions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("מקליד…", color = ThemeState.subtext, fontSize = 13.sp)
        }
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "הצעות — הקש כדי לחפש",
            color = ThemeState.subtext2, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            items(suggestions) { s ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPick(s) }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, null, tint = ThemeState.subtext, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(s, color = ThemeState.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Icon(
                        Icons.Default.NorthWest, "חפש את זה",
                        tint = ThemeState.subtext, modifier = Modifier.size(16.dp),
                    )
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
                Text("חפש סרטונים בערוצים המאושרים", color = ThemeState.subtext, fontSize = 14.sp)
            }
        }
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("היסטוריית חיפוש", color = ThemeState.subtext2, fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onClear) { Text("נקה הכל", color = Color(0xFFFF0000), fontSize = 12.sp) }
        }
        LazyColumn {
            items(history) { q ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPick(q) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.History, null, tint = ThemeState.subtext, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(q, color = ThemeState.text, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRemove(q) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "הסר", tint = Color(0xFF666666), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
