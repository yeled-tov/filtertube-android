package com.filtertube.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filtertube.app.data.GoogleAuth
import com.filtertube.app.data.SettingsStore
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
    var showAbout by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F))) {
        Text(
            "הגדרות", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp),
        )
        HorizontalDivider(color = Color(0xFF272727))

        Spacer(Modifier.height(8.dp))
        SettingsRow(Icons.Default.AccountCircle, Color(0xFFFF0000), "חשבון Google",
            "התחברות וסנכרון לייקים ומנויים") { showAccount = true }
        SettingsRow(Icons.Default.FilterAlt, Color(0xFFFFAA00), "הגדרות סינון 🔒",
            "רמת סינון והצגת Shorts — מוגן בסיסמה") { showGate = true }
        SettingsRow(Icons.Default.Tune, Color(0xFF3B82F6), "הגדרות תצוגה",
            "קצב רענון גבוה (120 הרץ)") { showDisplay = true }
        SettingsRow(Icons.Default.Info, Color(0xFFAAAAAA), "אודות",
            "FilterTube — רק ערוצים מאושרים") { showAbout = true }
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
        onOpenAdmin = { showFilter = false; onOpenAdmin() },
        onDismiss = { showFilter = false },
    )

    if (showChangePw) ChangePasswordDialog(
        settings = settings,
        onDone = { showChangePw = false },
        onDismiss = { showChangePw = false },
    )

    if (showDisplay) DisplayDialog(settings = settings, onDismiss = { showDisplay = false })

    if (showAbout) AboutDialog(onDismiss = { showAbout = false })
}

// ── שורת הגדרה ───────────────────────────────────────────────────────────
@Composable
private fun SettingsRow(icon: ImageVector, accent: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1F1F1F)),
            contentAlignment = Alignment.Center) { Icon(icon, null, tint = accent) }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = Color(0xFF888888), fontSize = 12.sp)
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
                    color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                Text("סנכרון הלייקים והמנויים מתבצע בטאב ״ספריה״.", color = Color(0xFF888888), fontSize = 12.sp)
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
        containerColor = Color(0xFF1F1F1F),
        titleContentColor = Color.White, textContentColor = Color.White,
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
                    color = Color(0xFFAAAAAA), fontSize = 12.sp,
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
        containerColor = Color(0xFF1F1F1F),
        titleContentColor = Color.White, textContentColor = Color.White,
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
        containerColor = Color(0xFF1F1F1F),
        titleContentColor = Color.White, textContentColor = Color.White,
    )
}

@Composable
private fun PwField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        label = { Text(label, color = Color(0xFF888888)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
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
    onOpenAdmin: () -> Unit,
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
                        Text("הצג טאב Shorts", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("סרטונים קצרים מהערוצים המאושרים", color = Color(0xFF888888), fontSize = 11.sp)
                    }
                    Switch(
                        checked = shorts,
                        onCheckedChange = { shorts = it; onShortsToggle(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFFF0000),
                            uncheckedThumbColor = Color(0xFF888888), uncheckedTrackColor = Color(0xFF333333),
                        ),
                    )
                }

                HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 8.dp))
                TextButton(onClick = onOpenAdmin) { Text("פאנל ניהול ערוצים ›", color = Color(0xFFFFAA00)) }
                TextButton(onClick = onChangePassword) { Text("שנה סיסמה", color = Color(0xFF888888)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("סגור") } },
        containerColor = Color(0xFF1F1F1F),
        titleContentColor = Color.White, textContentColor = Color.White,
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
            Text("רמה $value — $title", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(desc, color = Color(0xFF888888), fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

// ── תצוגה ────────────────────────────────────────────────────────────────
@Composable
private fun DisplayDialog(settings: SettingsStore, onDismiss: () -> Unit) {
    var high by remember { mutableStateOf(settings.highRefreshRate) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("הגדרות תצוגה") },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("קצב רענון גבוה (120 הרץ)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text("תצוגה חלקה במכשירים שתומכים · החלפה דורשת הפעלה מחדש",
                            color = Color(0xFF888888), fontSize = 11.sp)
                    }
                    Switch(
                        checked = high,
                        onCheckedChange = { high = it; settings.highRefreshRate = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF3B82F6),
                            uncheckedThumbColor = Color(0xFF888888), uncheckedTrackColor = Color(0xFF333333),
                        ),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("סגור") } },
        containerColor = Color(0xFF1F1F1F),
        titleContentColor = Color.White, textContentColor = Color.White,
    )
}

// ── אודות ────────────────────────────────────────────────────────────────
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, null, tint = Color(0xFFFF0000)) },
        title = { Text("FilterTube") },
        text = {
            Text("פלטפורמת וידאו מסוננת — מציגה אך ורק ערוצים מאושרים. כל התוכן מסונן לפי רמת הסינון שנבחרה.",
                color = Color(0xFFAAAAAA), fontSize = 13.sp, lineHeight = 18.sp)
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("סגור") } },
        containerColor = Color(0xFF1F1F1F),
        titleContentColor = Color.White, textContentColor = Color.White,
    )
}
