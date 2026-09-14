package com.filtertube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.filtertube.app.ThemeState
import com.filtertube.app.data.Channel
import com.filtertube.app.data.ChannelsRepository
import com.filtertube.app.data.SettingsStore
import com.filtertube.app.data.categoryLabels
import com.filtertube.app.data.forLevel

/** כמה בחירות נדרשות כדי שהתחנה תהיה באמת מגוונת ולא לולאה של זמר אחד. */
private const val MIN_PICKS = 3

/**
 * בחירת זמרים אהובים — נקודת ההתחלה של הרדיו כשאין עדיין שום היסטוריה.
 *
 * ## למה זה קיים
 * "רדיו אישי" בהתקנה טרייה הוא סתירה: אין לייקים, אין היסטוריה, ואין ממה
 * להסיק טעם. עד עכשיו התוצאה הייתה הפיד הכללי בתחפושת. שאלה אחת פותרת את
 * זה — ובחירה מפורשת היא ממילא אות הטעם החזק ביותר שיש, חזק יותר מלייק.
 *
 * ## למה רק ערוצים מאושרים
 * הבחירה מוגבלת לרשימה הלבנה שכבר סוננה לרמת הסינון ולמגדר של המשתמש.
 * מסך שמציע לבחור זמר שאסור לשמוע היה שובר את כל הרעיון של האפליקציה.
 */
@Composable
fun ArtistPickerDialog(
    onDismiss: () -> Unit,
    onSaved: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }

    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    val picked = remember { mutableStateListOf<String>() }

    LaunchedEffect(Unit) {
        picked.addAll(settings.favoriteArtists)
        channels = runCatching {
            ChannelsRepository.getChannels(context).forLevel(settings.filterLevel, settings.userGender)
        }.getOrNull().orEmpty()
        loading = false
    }

    // מוזיקה ואירועים ראשונים: זה מה ש"זמר אהוב" אומר. השאר נשאר זמין,
    // כי גם שיעור או תוכנית הם טעם.
    val ordered = remember(channels, query) {
        val q = query.trim()
        val musical = setOf("music", "dati_light", "events")
        channels.asSequence()
            .filter { q.isBlank() || it.name.contains(q, ignoreCase = true) }
            .sortedWith(
                compareByDescending<Channel> { it.category in musical }
                    .thenBy { it.name },
            )
            .toList()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ThemeState.bg)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)) {
                Text("איזה זמרים אתה אוהב?", color = ThemeState.text,
                    fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "בחר לפחות $MIN_PICKS, ולפי זה הרדיו יבנה את הסגנון שלך. " +
                        "ככל שתשמע יותר הוא יכיר אותך יותר טוב — אפשר לשנות בכל רגע.",
                    color = ThemeState.subtext2, fontSize = 13.sp, lineHeight = 19.sp,
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("חיפוש", color = ThemeState.subtext, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = ThemeState.subtext) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ThemeState.text,
                    unfocusedTextColor = ThemeState.text,
                    focusedBorderColor = ThemeState.accent,
                    unfocusedBorderColor = ThemeState.divider,
                    focusedContainerColor = ThemeState.card,
                    unfocusedContainerColor = ThemeState.card,
                ),
            )

            if (loading) {
                Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                    CircularProgressIndicator(color = ThemeState.accent)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    items(ordered, key = { it.youtubeChannelId }) { channel ->
                        val selected = channel.youtubeChannelId in picked
                        ArtistRow(channel, selected) {
                            if (selected) picked.remove(channel.youtubeChannelId)
                            else picked.add(channel.youtubeChannelId)
                        }
                    }
                }
            }

            Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Button(
                    onClick = {
                        val chosen = picked.toSet()
                        settings.favoriteArtists = chosen
                        settings.artistPickerSeen = true
                        onSaved(chosen)
                    },
                    enabled = picked.size >= MIN_PICKS,
                    modifier = Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ThemeState.accent,
                        disabledContainerColor = ThemeState.card,
                        disabledContentColor = ThemeState.subtext,
                    ),
                ) {
                    Text(
                        if (picked.size >= MIN_PICKS) "הפעל רדיו (${picked.size})"
                        else "בחר עוד ${MIN_PICKS - picked.size}",
                        fontWeight = FontWeight.Bold,
                    )
                }
                TextButton(
                    onClick = {
                        // "דילוג" נזכר. אחרת המסך היה קופץ בכל לחיצה על רדיו,
                        // וזה כבר לא שאלה אלא מכשול.
                        settings.artistPickerSeen = true
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("לא עכשיו", color = ThemeState.subtext) }
            }
        }
    }
}

@Composable
private fun ArtistRow(channel: Channel, selected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) ThemeState.accent.copy(alpha = 0.16f) else ThemeState.card)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape)
                .background(if (selected) ThemeState.accent else ThemeState.bg2),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Text(
                    channel.name.trim().take(1).ifBlank { "?" },
                    color = ThemeState.subtext2, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                channel.name, color = ThemeState.text, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                categoryLabels[channel.category] ?: channel.category,
                color = ThemeState.subtext, fontSize = 11.5.sp,
            )
        }
    }
}
