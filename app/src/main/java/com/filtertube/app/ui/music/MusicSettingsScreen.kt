package com.filtertube.app.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filtertube.app.ThemeState
import com.filtertube.app.data.SettingsStore

/**
 * הגדרות FilterMusic — במבנה של Metrolist: קבוצות עם כותרת, ובתוכן שורות.
 *
 * ## כלל אחד
 * כל הגדרה כאן מחוברת לנגן בפועל. לא הוספתי הגדרות שנראות טוב ולא עושות
 * כלום: הגדרה דקורטיבית גרועה מהיעדר הגדרה, כי היא מלמדת את המשתמש שהמסך
 * הזה משקר.
 */
@Composable
fun MusicSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }

    var quality by remember { mutableStateOf(settings.audioQuality) }
    var crossfade by remember { mutableStateOf(settings.crossfadeSeconds.toFloat()) }
    var skipSilence by remember { mutableStateOf(settings.skipSilence) }
    var normalize by remember { mutableStateOf(settings.audioNormalization) }
    var speed by remember { mutableStateOf(settings.playbackSpeed.toFloat()) }
    var pitch by remember { mutableStateOf(settings.playbackPitch.toFloat()) }
    var autoRadio by remember { mutableStateOf(settings.autoRadioQueue) }
    var noDup by remember { mutableStateOf(settings.preventQueueDuplicates) }
    var sleep by remember { mutableStateOf(settings.sleepTimerMinutes.toFloat()) }
    var audioOnly by remember { mutableStateOf(settings.audioOnlyMode) }

    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזרה", tint = ThemeState.text)
            }
            Text("הגדרות", color = ThemeState.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            item { Group("שמע") }
            item {
                ChoiceRow(
                    "איכות שמע",
                    listOf("אוטומטי", "גבוהה", "חסכונית בנתונים"),
                    quality,
                ) { quality = it; settings.audioQuality = it }
            }
            item {
                SliderRow(
                    "עמעום מוצלב", 
                    if (crossfade.toInt() == 0) "כבוי" else "${crossfade.toInt()} שניות",
                    crossfade, 0f..12f, 12,
                ) { crossfade = it; settings.crossfadeSeconds = it.toInt() }
            }
            item {
                ToggleRow(
                    "דילוג על שקט",
                    "מקצר שקט ארוך בתוך השיר ובין השירים",
                    skipSilence,
                ) { skipSilence = it; settings.skipSilence = it }
            }
            item {
                ToggleRow(
                    "הגברת עוצמה",
                    "מוסיף 7dB לפלט — עוזר בהקלטות חלשות",
                    normalize,
                ) { normalize = it; settings.audioNormalization = it }
            }
            item {
                SliderRow("מהירות ניגון", "${speed.toInt()}%", speed, 50f..200f, 30) {
                    speed = it; settings.playbackSpeed = it.toInt()
                }
            }
            item {
                SliderRow("גובה הצליל", "${pitch.toInt()}%", pitch, 50f..200f, 30) {
                    pitch = it; settings.playbackPitch = it.toInt()
                }
            }
            item {
                ToggleRow(
                    "אודיו בלבד",
                    "בכל רמות הסינון — גם ההורדות יהיו אודיו",
                    audioOnly,
                ) { audioOnly = it; settings.audioOnlyMode = it }
            }

            item { Group("תור") }
            item {
                ToggleRow(
                    "רדיו אוטומטי בסוף התור",
                    "כשהתור נגמר, ממשיכים באותו סגנון במקום לעצור",
                    autoRadio,
                ) { autoRadio = it; settings.autoRadioQueue = it }
            }
            item {
                ToggleRow(
                    "מניעת כפילויות",
                    "אותו שיר לא ייכנס לתור פעמיים",
                    noDup,
                ) { noDup = it; settings.preventQueueDuplicates = it }
            }

            item { Group("טיימר שינה") }
            item {
                SliderRow(
                    "כיבוי אוטומטי",
                    if (sleep.toInt() == 0) "כבוי" else "אחרי ${sleep.toInt()} דקות",
                    sleep, 0f..120f, 24,
                ) { sleep = it; settings.sleepTimerMinutes = it.toInt() }
            }
        }
    }
}

@Composable
private fun Group(title: String) {
    Text(
        title, color = ThemeState.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = ThemeState.text, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = ThemeState.subtext, fontSize = 11.5.sp, lineHeight = 15.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = ThemeState.accent,
                uncheckedThumbColor = ThemeState.subtext, uncheckedTrackColor = ThemeState.divider,
            ),
        )
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: String,
    current: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = ThemeState.text, fontSize = 14.5.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f))
            Text(value, color = ThemeState.subtext2, fontSize = 12.5.sp)
        }
        Slider(
            value = current, onValueChange = onChange, valueRange = range, steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = ThemeState.accent,
                activeTrackColor = ThemeState.accent,
                inactiveTrackColor = ThemeState.divider,
            ),
        )
    }
}

@Composable
private fun ChoiceRow(title: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(title, color = ThemeState.text, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, label ->
                val active = index == selected
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(50))
                        .background(if (active) ThemeState.accent else ThemeState.card)
                        .clickable { onSelect(index) }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text(label, color = if (active) Color.White else ThemeState.subtext2, fontSize = 12.sp)
                }
            }
        }
    }
}
