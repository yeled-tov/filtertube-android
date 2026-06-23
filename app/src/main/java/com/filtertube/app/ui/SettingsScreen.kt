package com.filtertube.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filtertube.app.BuildConfig
import com.filtertube.app.ThemeState
import com.filtertube.app.data.GoogleAuth
import com.filtertube.app.data.SettingsStore
import com.filtertube.app.data.UpdateChecker
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException

@Composable
fun SettingsScreen(
    shortsEnabled: Boolean,
    onShortsToggle: (Boolean) -> Unit,
    filterLevel: Int,
    onFilterLevelChange: (Int) -> Unit,
    onOpenAdmin: () -> Unit,
) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }

    var showAccount by remember { mutableStateOf(false) }
    var showGate by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var showChangePw by remember { mutableStateOf(false) }
    var showDisplay by remember { mutableStateOf(false) }
    var showPlayerAudio by remember { mutableStateOf(false) }
    var showUpdate by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var adminUnlocked by remember { mutableStateOf(settings.adminUnlocked) }

    Column(
        modifier = Modifier.fillMaxSize().background(ThemeState.bg)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            "הגדרות", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = ThemeState.text,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp),
        )
        HorizontalDivider(color = ThemeState.divider)

        Spacer(Modifier.height(8.dp))
        SettingsRow(Icons.Default.AccountCircle, Color(0xFFFF0000), "חשבון Google",
            "התחברות וסנכרון לייקים ומנויים") { showAccount = true }
        SettingsRow(Icons.Default.FilterAlt, Color(0xFFFFAA00), "הגדרות סינון 🔒",
            "רמת סינון והצגת Shorts — מוגן בסיסמה") { showGate = true }
        SettingsRow(Icons.Default.MusicNote, Color(0xFF10B981), "נגן ושמע",
            "עיצוב הנגן ואיכות") { showPlayerAudio = true }
        SettingsRow(Icons.Default.Tune, Color(0xFF3B82F6), "הגדרות תצוגה",
            "צבע ראשי · מצב כהה/בהיר · 120 הרץ") { showDisplay = true }
        SettingsRow(Icons.Default.SystemUpdate, Color(0xFFA855F7), "עדכונים",
            "בדוק והורד גרסה חדשה") { showUpdate = true }
        SettingsRow(Icons.Default.Info, ThemeState.subtext2, "אודות",
            "FilterTube — רק ערוצים מאושרים") { showAbout = true }

        // מופיע רק אחרי 7 לחיצות על "אודות" → גרסה (חשיפת מצב ניהול)
        if (adminUnlocked) {
            SettingsRow(Icons.Default.AdminPanelSettings, Color(0xFFFFAA00), "ניהול ערוצים",
                "הוספה/הסרה של ערוצים מהרשימה הלבנה") { onOpenAdmin() }
        }
        // מרווח תחתון כדי שהפריט האחרון יהיה מעל סרגל הניווט הצף
        Spacer(Modifier.height(110.dp))
    }

    if (showAccount) AccountDialog(onDismiss = { showAccount = false })

    if (showGate) FilterGateDialog(
        settings = settings,
        onUnlock = { showGate = false; showFilter = true },
        onDismiss = { showGate = false },
    )

    if (showFilter) FilterSettingsDialog(
        filterLevel = filterLevel,
        onFilterLevelChange = onFilterLevelChange,
        shortsEnabled = shortsEnabled,
        onShortsToggle = onShortsToggle,
        onChangePassword = { showFilter = false; showChangePw = true },
        onDismiss = { showFilter = false },
    )

    if (showChangePw) ChangePasswordDialog(
        settings = settings,
        onDone = { showChangePw = false },
        onDismiss = { showChangePw = false },
    )

    if (showDisplay) DisplayDialog(settings = settings, onDismiss = { showDisplay = false })

    if (showPlayerAudio) PlayerAudioDialog(settings = settings, onDismiss = { showPlayerAudio = false })

    if (showUpdate) UpdateDialog(onDismiss = { showUpdate = false })

    if (showAbout) AboutDialog(
        onUnlock = { settings.adminUnlocked = true; adminUnlocked = true },
        onDismiss = { showAbout = false },
    )
}

// ── שורת הגדרה ───────────────────────────────────────────────────────────
@Composable
private fun SettingsRow(icon: ImageVector, accent: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(ThemeState.surface),
            contentAlignment = Alignment.Center) { Icon(icon, null, tint = accent) }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = ThemeState.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = ThemeState.subtext, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color(0xFF666666))
    }
}

// ── חשבון Google ─────────────────────────────────────────────────────────
@Composable
private fun AccountDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var account by remember { mutableStateOf(GoogleAuth.lastAccount(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        try {
            account = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
        } catch (_: ApiException) {}
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("חשבון Google") },
        text = {
            Column {
                Text(if (account != null) "מחובר: ${account?.email}" else "לא מחובר",
                    color = ThemeState.text, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text("סנכרון הלייקים והמנויים מתבצע בטאב ״ספריה״.", color = ThemeState.subtext, fontSize = 12.sp)
            }
        },
        confirmButton = {
            if (account != null) {
                TextButton(onClick = { GoogleAuth.client(context).signOut(); account = null }) {
                    Text("התנתק", color = Color(0xFFFF5555))
                }
            } else {
                TextButton(onClick = { launcher.launch(GoogleAuth.client(context).signInIntent) }) { Text("התחבר") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("סגור") } },
        containerColor = ThemeState.surface,
        titleContentColor = ThemeState.text, textContentColor = ThemeState.text,
    )
}

// ── שער סיסמה לסינון ─────────────────────────────────────────────────────
@Composable
private fun FilterGateDialog(settings: SettingsStore, onUnlock: () -> Unit, onDismiss: () -> Unit) {
    val isSetup = !settings.hasFilterPassword
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Lock, null, tint = Color(0xFFFFAA00)) },
        title = { Text(if (isSetup) "קביעת סיסמת הורים" else "הזן סיסמה") },
        text = {
            Column {
                Text(
                    if (isSetup) "קבע סיסמה שתידרש כל פעם שמשנים את רמת הסינון או את הצגת ה-Shorts."
                    else "נדרשת סיסמה כדי לשנות את הגדרות הסינון.",
                    color = ThemeState.subtext2, fontSize = 12.sp,
                )
                Spacer(Modifier.height(12.dp))
                PwField(pw, { pw = it; error = "" }, "סיסמה")
                if (isSetup) {
                    Spacer(Modifier.height(8.dp))
                    PwField(pw2, { pw2 = it; error = "" }, "אימות סיסמה")
                }
                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = Color(0xFFFF5555), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (isSetup) {
                    when {
                        pw.trim().length < 3 -> error = "סיסמה קצרה מדי (לפחות 3 תווים)"
                        pw != pw2 -> error = "הסיסמאות אינן תואמות"
                        else -> { settings.filterPassword = pw.trim(); onUnlock() }
                    }
                } else {
                    if (settings.checkFilterPassword(pw)) onUnlock() else error = "סיסמה שגויה"
                }
            }) { Text(if (isSetup) "שמור והמשך" else "אישור") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("בטל") } },
        containerColor = ThemeState.surface,
        titleContentColor = ThemeState.text, textContentColor = ThemeState.text,
    )
}

@Composable
private fun ChangePasswordDialog(settings: SettingsStore, onDone: () -> Unit, onDismiss: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("שינוי סיסמה") },
        text = {
            Column {
                PwField(pw, { pw = it; error = "" }, "סיסמה חדשה")
                Spacer(Modifier.height(8.dp))
                PwField(pw2, { pw2 = it; error = "" }, "אימות סיסמה")
                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = Color(0xFFFF5555), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    pw.trim().length < 3 -> error = "סיסמה קצרה מדי (לפחות 3 תווים)"
                    pw != pw2 -> error = "הסיסמאות אינן תואמות"
                    else -> { settings.filterPassword = pw.trim(); onDone() }
                }
            }) { Text("שמור") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("בטל") } },
        containerColor = ThemeState.surface,
        titleContentColor = ThemeState.text, textContentColor = ThemeState.text,
    )
}

@Composable
private fun PwField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label, color = ThemeState.subtext) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = ThemeState.text, unfocusedTextColor = ThemeState.text,
            focusedBorderColor = Color(0xFFFFAA00), unfocusedBorderColor = Color(0xFF333333),
        ),
    )
}

// ── הגדרות סינון (אחרי סיסמה) ────────────────────────────────────────────
@Composable
private fun FilterSettingsDialog(
    filterLevel: Int,
    onFilterLevelChange: (Int) -> Unit,
    shortsEnabled: Boolean,
    onShortsToggle: (Boolean) -> Unit,
    onChangePassword: () -> Unit,
    onDismiss: () -> Unit,
) {
    var level by remember { mutableStateOf(filterLevel) }
    var shorts by remember { mutableStateOf(shortsEnabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("הגדרות סינון") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                LevelRow(1, "מחמיר", "מוזיקה כאודיו בלבד · ״דתי לייט״ מוסתר", level) { level = 1; onFilterLevelChange(1) }
                LevelRow(2, "רגיל", "הכל כווידאו · ״דתי לייט״ מוסתר", level) { level = 2; onFilterLevelChange(2) }
                LevelRow(3, "דתי לייט", "כולל ״דתי לייט״ (אודיו בלבד)", level) { level = 3; onFilterLevelChange(3) }

                HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("הצג טאב Shorts", color = ThemeState.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("סרטונים קצרים מהערוצים המאושרים", color = ThemeState.subtext, fontSize = 11.sp)
                    }
                    Switch(
                        checked = shorts,
                        onCheckedChange = { shorts = it; onShortsToggle(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFFF0000),
                            uncheckedThumbColor = ThemeState.subtext, uncheckedTrackColor = Color(0xFF333333),
                        ),
                    )
                }

                HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 8.dp))
                TextButton(onClick = onChangePassword) { Text("שנה סיסמה", color = ThemeState.subtext) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("סגור") } },
        containerColor = ThemeState.surface,
        titleContentColor = ThemeState.text, textContentColor = ThemeState.text,
    )
}

@Composable
private fun LevelRow(value: Int, title: String, desc: String, selected: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected == value, onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFF0000), unselectedColor = Color(0xFF666666)),
        )
        Spacer(Modifier.width(4.dp))
        Column {
            Text("רמה $value — $title", color = ThemeState.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(desc, color = ThemeState.subtext, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

// ── תצוגה ────────────────────────────────────────────────────────────────
private val accentOptions = listOf(
    Color(0xFFFF0000), Color(0xFFFF6D00), Color(0xFFFFC400), Color(0xFF00C853),
    Color(0xFF2962FF), Color(0xFFAA00FF), Color(0xFF00BFA5), Color(0xFFEC407A),
)

private val qualityOptions = listOf(
    0 to "אוטומטי", 2160 to "4K", 1440 to "1440p", 1080 to "1080p",
    720 to "720p", 480 to "480p", 360 to "360p", 240 to "240p", 144 to "144p",
)

@Composable
private fun DisplayDialog(settings: SettingsStore, onDismiss: () -> Unit) {
    var high by remember { mutableStateOf(settings.highRefreshRate) }
    val sysDark = androidx.compose.foundation.isSystemInDarkTheme()
    var mode by remember { mutableStateOf(settings.themeMode) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("הגדרות תצוגה") },
        text = {
            Column {
                Text("ערכת נושא", color = ThemeState.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChip("מערכת", mode == 0) { mode = 0; settings.themeMode = 0; ThemeState.dark = sysDark }
                    ThemeChip("כהה", mode == 1) { mode = 1; settings.themeMode = 1; ThemeState.dark = true }
                    ThemeChip("בהיר", mode == 2) { mode = 2; settings.themeMode = 2; ThemeState.dark = false }
                }
                HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("קצב רענון גבוה (120 הרץ)", color = ThemeState.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("תצוגה חלקה במכשירים שתומכים · החלפה דורשת הפעלה מחדש",
                            color = ThemeState.subtext, fontSize = 11.sp)
                    }
                    Switch(
                        checked = high,
                        onCheckedChange = { high = it; settings.highRefreshRate = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF3B82F6),
                            uncheckedThumbColor = ThemeState.subtext, uncheckedTrackColor = Color(0xFF333333),
                        ),
                    )
                }

                HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 12.dp))

                Text("צבע ראשי", color = ThemeState.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text("נכנס לתוקף מיד", color = ThemeState.subtext, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    accentOptions.forEach { c ->
                        val selected = ThemeState.accent.toArgb() == c.toArgb()
                        Box(
                            modifier = Modifier.size(34.dp).clip(CircleShape).background(c)
                                .border(if (selected) 3.dp else 0.dp, Color.White, CircleShape)
                                .clickable { ThemeState.accent = c; settings.accentColor = c.toArgb() },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("סגור") } },
        containerColor = ThemeState.surface,
        titleContentColor = ThemeState.text, textContentColor = ThemeState.text,
    )
}

// ── אודות ────────────────────────────────────────────────────────────────
@Composable
private fun AboutDialog(onUnlock: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var taps by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, null, tint = Color(0xFFFF0000)) },
        title = { Text("FilterTube") },
        text = {
            Column {
                Text("פלטפורמת וידאו מסוננת — מציגה אך ורק ערוצים מאושרים. כל התוכן מסונן לפי רמת הסינון שנבחרה.",
                    color = ThemeState.subtext2, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
                // לחיצה 7 פעמים על שורת הגרסה חושפת את ניהול הערוצים (כמו "אפשרויות מפתח")
                Text("גרסה ${BuildConfig.VERSION_NAME}",
                    color = ThemeState.subtext, fontSize = 13.sp,
                    modifier = Modifier.clickable {
                        taps++
                        if (taps >= 7) {
                            onUnlock()
                            android.widget.Toast.makeText(context, "מצב ניהול הופעל ✓", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    })
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("סגור") } },
        containerColor = ThemeState.surface,
        titleContentColor = ThemeState.text, textContentColor = ThemeState.text,
    )
}

@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(16.dp))
            .background(if (selected) ThemeState.accent else Color(0xFF2A2A2A))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(label, color = ThemeState.text, fontSize = 13.sp) }
}

// ── נגן ושמע ─────────────────────────────────────────────────────────────
@Composable
private fun PlayerAudioDialog(settings: SettingsStore, onDismiss: () -> Unit) {
    var style by remember { mutableStateOf(settings.playerStyle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("נגן ושמע") },
        text = {
            Column {
                Text("עיצוב הנגן", color = ThemeState.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                StyleRow(1, "מתנגן עכשיו", "וידאו למעלה ובקרים גדולים מתחת", style) { style = 1; settings.playerStyle = 1 }
                StyleRow(2, "בקרים על הוידאו", "בקרים על הסרטון, ״הבא בתור״ מתחת", style) { style = 2; settings.playerStyle = 2 }

                HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 10.dp))

                var quality by remember { mutableStateOf(settings.preferredQuality) }
                Text("איכות צפייה", color = ThemeState.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    qualityOptions.forEach { (h, label) ->
                        val selected = quality == h
                        Box(
                            modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (selected) ThemeState.accent else Color(0xFF2A2A2A))
                                .clickable { quality = h; settings.preferredQuality = h }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) { Text(label, color = Color.White, fontSize = 12.sp) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("חל על הסרטון הבא שתפעיל. מעל 720p נטען מעט יותר לאט.",
                    color = ThemeState.subtext, fontSize = 11.sp, lineHeight = 15.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("סגור") } },
        containerColor = ThemeState.surface,
        titleContentColor = ThemeState.text, textContentColor = ThemeState.text,
    )
}

@Composable
private fun StyleRow(value: Int, title: String, desc: String, selected: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected == value, onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = ThemeState.accent, unselectedColor = Color(0xFF666666)))
        Spacer(Modifier.width(4.dp))
        Column {
            Text(title, color = ThemeState.text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(desc, color = ThemeState.subtext, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

// ── עדכונים ──────────────────────────────────────────────────────────────
@Composable
private fun UpdateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("בודק עדכונים...") }
    var update by remember { mutableStateOf<UpdateChecker.Update?>(null) }
    LaunchedEffect(Unit) {
        val u = UpdateChecker.check()
        when {
            u == null -> status = "לא ניתן לבדוק כעת — נסה שוב מאוחר יותר"
            u.isNewer -> { update = u; status = "" }
            else -> status = "אתה מעודכן (גרסה ${BuildConfig.VERSION_NAME})"
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("עדכונים") },
        text = {
            Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                val u = update
                if (u != null) {
                    Text("יש גרסה חדשה: ${u.name}", color = ThemeState.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("מה השתנה:", color = ThemeState.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(u.changelog.ifEmpty { "—" }, color = ThemeState.subtext2, fontSize = 12.sp, lineHeight = 16.sp)
                } else {
                    Text(status, color = ThemeState.text, fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            val u = update
            val apk = u?.apkUrl
            if (apk != null) {
                TextButton(onClick = { UpdateChecker.downloadApk(context, apk); onDismiss() }) { Text("הורד והתקן") }
            } else {
                TextButton(onClick = onDismiss) { Text("סגור") }
            }
        },
        dismissButton = { if (update != null) TextButton(onClick = onDismiss) { Text("אחר כך") } },
        containerColor = ThemeState.surface,
        titleContentColor = ThemeState.text, textContentColor = ThemeState.text,
    )
}
