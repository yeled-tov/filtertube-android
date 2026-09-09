package com.filtertube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import coil.compose.AsyncImage
import com.filtertube.app.ThemeState
import com.filtertube.app.data.Channel
import com.filtertube.app.data.ChannelAvatars
import com.filtertube.app.data.ChannelAdmin
import com.filtertube.app.data.ChannelRequests
import com.filtertube.app.data.ChannelsRepository
import com.filtertube.app.data.LibraryStore
import com.filtertube.app.data.SettingsStore
import com.filtertube.app.data.categoryLabelHe
import com.filtertube.app.data.categoryLabels
import com.filtertube.app.data.forLevel
import com.filtertube.app.data.sortedCategories
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/** רשימת כל הערוצים המאושרים (מקובצת לפי קטגוריה) עם כפתור "עקוב" לכל ערוץ. */
@Composable
fun ChannelsBrowseScreen(onBack: () -> Unit, onOpenChannel: (String, String) -> Unit = { _, _ -> }) {
    val context = LocalContext.current
    val store = remember { LibraryStore(context) }
    val settings = remember { SettingsStore(context) }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var version by remember { mutableStateOf(0) }
    var showRequest by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        channels = runCatching {
            ChannelsRepository.getChannels(context).forLevel(settings.filterLevel, settings.userGender)
        }.getOrNull().orEmpty()
            // מזהה ערוץ כפול ברשימה מפיל את LazyColumn ("Key was already used").
            .distinctBy { it.youtubeChannelId }
            .sortedBy { it.name }
        runCatching { ChannelAvatars.warm(context, channels.map { it.youtubeChannelId }) }
    }

    if (showRequest) ChannelRequestDialog(onDismiss = { showRequest = false })

    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        DetailTopBar("ערוצים — עקוב לקבלת התראות", onBack)
        if (channels.isEmpty()) {
            CenteredLoading("טוען ערוצים...")
        } else {
            val byCat = channels.groupBy { it.category }
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)) {
                item(key = "request_banner") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 4.dp)
                            .clip(RoundedCornerShape(16.dp)).background(ThemeState.card)
                            .clickable { showRequest = true }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.AddCircleOutline, null, tint = ThemeState.accent, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("בקש הוספת ערוץ", color = ThemeState.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("חסר לך ערוץ? שלח בקשה לאישור", color = ThemeState.subtext, fontSize = 12.sp)
                        }
                    }
                }
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

/**
 * טופס בקשת הוספת ערוץ — שם, קישור, קטגוריה ופירוט תוכן. נשלח לאישור המנהל.
 *
 * [prefillName] ו-[prefillUrl] ממלאים מראש את זהות הערוץ. זה מה שקורה כשמגיעים
 * לכאן מקישור יוטיוב שנחסם: הערוץ כבר ידוע מהסרטון עצמו, ואין שום סיבה לבקש
 * מהמשתמש להקליד את שמו מחדש או לחפש את הקישור. נשאר לו רק להסביר מה הערוץ
 * מכיל ולבחור קטגוריה.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelRequestDialog(
    onDismiss: () -> Unit,
    prefillName: String = "",
    prefillUrl: String = "",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefilled = prefillName.isNotBlank() && prefillUrl.isNotBlank()
    var name by remember { mutableStateOf(prefillName) }
    var url by remember { mutableStateOf(prefillUrl) }
    var category by remember { mutableStateOf("music") }
    var gender by remember { mutableStateOf("all") }
    var desc by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var resolving by remember { mutableStateOf(false) }
    var resolved by remember { mutableStateOf<ChannelAdmin.Resolved?>(null) }
    // כשהערוץ הגיע מקישור, הקישור כבר נכון — אין מה לאתר ואין מה לדרוס.
    var manualUrl by remember { mutableStateOf(prefilled) }
    var notFound by remember { mutableStateOf(false) }

    /**
     * מילוי אוטומטי של קישור הערוץ תוך כדי הקלדת השם.
     *
     * שלוש תקלות תוקנו כאן ביחס לגרסה הקודמת:
     * 1. **קריסה.** כל אות פתחה חילוץ NewPipe נוסף במקביל (הביטול לא עוצר בקשת
     *    רשת שכבר רצה), עד לחניקת מאגר ה-IO ונפילה בזיכרון. עכשיו יש debounce
     *    ארוך יותר, מנעול חילוץ יחיד ב-ChannelAdmin, ותפיסת Throwable.
     * 2. **תוצאה ישנה שדורסת חדשה.** נבדק שהשם לא השתנה לפני כתיבת התוצאה.
     * 3. **"מאתר ערוץ…" שנתקע לנצח.** finally מאפס תמיד את מצב הטעינה.
     */
    LaunchedEffect(name, manualUrl) {
        if (prefilled || manualUrl) return@LaunchedEffect
        val query = name.trim()
        if (query.length < 3) {
            resolved = null; notFound = false; resolving = false; url = ""
            return@LaunchedEffect
        }
        if (resolved?.name?.trim() == query) return@LaunchedEffect
        delay(700)
        resolving = true
        notFound = false
        try {
            val found = ChannelAdmin.resolveChannel(query)
            if (name.trim() != query) return@LaunchedEffect // הקלדה חדשה — התוצאה כבר לא רלוונטית
            if (found != null) {
                resolved = found
                url = found.url
            } else {
                resolved = null
                notFound = true
                // חשוב: מנקים את הקישור הישן, אחרת אפשר לשלוח בקשה עם ערוץ
                // שזוהה קודם ולא קשור לשם שמופיע עכשיו בטופס.
                url = ""
            }
        } finally {
            resolving = false
        }
    }

    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = ThemeState.accent, unfocusedBorderColor = ThemeState.divider,
        focusedLabelColor = ThemeState.accent, unfocusedLabelColor = ThemeState.subtext,
        focusedTextColor = ThemeState.text, unfocusedTextColor = ThemeState.text,
        cursorColor = ThemeState.accent,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ThemeState.surface,
        titleContentColor = ThemeState.text,
        textContentColor = ThemeState.text,
        title = { Text(if (sent) "הבקשה נשלחה ✓" else "בקשת הוספת ערוץ") },
        text = {
            if (sent) {
                Text("הבקשה נשלחה לאישור. תודה! נבדוק ונוסיף אם מתאים.",
                    color = ThemeState.subtext2, fontSize = 14.sp, lineHeight = 20.sp)
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("מלא/י את הפרטים כדי שיהיה קל לאשר:", color = ThemeState.subtext, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        name, { name = it; manualUrl = false },
                        label = { Text("שם הערוץ או הזמר") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = colors,
                    )
                    Spacer(Modifier.height(8.dp))

                    // כרטיס הערוץ שזוהה — המשתמש רואה בדיוק איזה ערוץ נבחר,
                    // במקום להתבקש להדביק קישור או מזהה שהוא לא מכיר.
                    val found = resolved
                    when {
                        resolving -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = ThemeState.accent, strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("מאתר את הערוץ ביוטיוב…", color = ThemeState.subtext, fontSize = 12.sp)
                        }

                        found != null && !manualUrl -> Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(ThemeState.card).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = found.avatarUrl, contentDescription = null,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(50))
                                    .background(Brush.linearGradient(ThemeState.accentColors)),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("זוהה: ${found.name}", color = ThemeState.text, fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(found.channelId, color = ThemeState.subtext, fontSize = 11.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            TextButton(onClick = { manualUrl = true }) {
                                Text("לא נכון?", color = ThemeState.accent, fontSize = 12.sp)
                            }
                        }

                        notFound -> Column {
                            Text("לא מצאנו ערוץ בשם הזה. אפשר להדביק קישור ידנית:",
                                color = ThemeState.subtext, fontSize = 12.sp)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                url, { url = it; manualUrl = true },
                                label = { Text("קישור לערוץ ביוטיוב") }, singleLine = true,
                                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
                                colors = colors,
                            )
                        }

                        // הגענו מקישור יוטיוב: הערוץ כבר מזוהה ודאית מהסרטון
                        // עצמו, אז מציגים אותו כעובדה במקום שדה קישור ריק.
                        prefilled -> Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(ThemeState.card).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.AddCircleOutline, null,
                                tint = ThemeState.accent, modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "מהקישור שפתחת", color = ThemeState.subtext, fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        manualUrl -> OutlinedTextField(
                            url, { url = it }, label = { Text("קישור לערוץ ביוטיוב") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = colors,
                        )

                        else -> Text("הקלד/י שם והקישור יימצא אוטומטית",
                            color = ThemeState.subtext, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("קטגוריה", color = ThemeState.subtext, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        categoryLabels.forEach { (key, label) ->
                            val sel = category == key
                            Box(
                                modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(50))
                                    .background(if (sel) ThemeState.accent else ThemeState.card)
                                    .clickable { category = key }.padding(horizontal = 13.dp, vertical = 7.dp),
                            ) { Text(label, color = if (sel) Color.White else ThemeState.text, fontSize = 12.sp) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("מגדר", color = ThemeState.subtext, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        listOf("all" to "הכל", "male" to "זכר", "female" to "נקבה").forEach { (key, label) ->
                            val sel = gender == key
                            Box(
                                modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(50))
                                    .background(if (sel) ThemeState.accent else ThemeState.card)
                                    .clickable { gender = key }.padding(horizontal = 13.dp, vertical = 7.dp),
                            ) { Text(label, color = if (sel) Color.White else ThemeState.text, fontSize = 12.sp) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(desc, { desc = it }, label = { Text("מה הערוץ מכיל? (תוכן, קהל יעד)") },
                        modifier = Modifier.fillMaxWidth().height(96.dp), shape = RoundedCornerShape(14.dp), colors = colors)
                }
            }
        },
        confirmButton = {
            if (sent) {
                TextButton(onClick = onDismiss) { Text("סגור", color = ThemeState.accent) }
            } else {
                TextButton(
                    enabled = !sending && name.isNotBlank() && url.isNotBlank(),
                    onClick = {
                        sending = true
                        scope.launch {
                            val result = ChannelRequests.submitDetailed(name, url, category, gender, desc)
                            sending = false
                            if (result.ok) sent = true
                            else android.widget.Toast.makeText(context, result.message, android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                ) { Text(if (sending) "שולח…" else "שלח בקשה", color = ThemeState.accent, fontWeight = FontWeight.Bold) }
            }
        },
        dismissButton = { if (!sent) TextButton(onClick = onDismiss) { Text("ביטול", color = ThemeState.subtext2) } },
    )
}

@Composable
private fun ChannelFollowRow(ch: Channel, store: LibraryStore, version: Int, onOpenChannel: () -> Unit, onChange: () -> Unit) {
    var subscribed by remember(ch.youtubeChannelId, version) { mutableStateOf(store.isSubscribed(ch.youtubeChannelId)) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onOpenChannel() }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val avatar = ChannelAvatars.cache[ch.youtubeChannelId]
        if (avatar != null) {
            AsyncImage(
                model = avatar, contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(50))
                    .background(ThemeState.card),
            )
        } else {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(50))
                    .background(Brush.linearGradient(ThemeState.accentColors)),
                contentAlignment = Alignment.Center,
            ) {
                Text(ch.name.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
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
