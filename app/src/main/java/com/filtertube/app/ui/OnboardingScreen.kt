package com.filtertube.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.filtertube.app.ThemeState
import com.filtertube.app.data.Channel
import com.filtertube.app.data.ChannelAvatars
import com.filtertube.app.data.ChannelsRepository
import com.filtertube.app.data.LibraryStore
import com.filtertube.app.data.SettingsStore

/**
 * מסך הרשמה ראשוני (Onboarding) — אשף 5 שלבים:
 *  0) פרטים אישיים + מגדר   1) סיסמת הורים (= סיסמת רמת הסינון)
 *  2) רמת סינון             3) זמרים/ערוצים אהובים (למנויים והתאמה אישית)
 *  4) ברוכים הבאים + 60 ימי ניסיון חינם
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    val store = remember { LibraryStore(context) }

    val total = 5
    var step by remember { mutableStateOf(0) }

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var level by remember { mutableStateOf(2) }
    val selected = remember { mutableStateListOf<String>() }
    var channels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    LaunchedEffect(Unit) {
        channels = runCatching { ChannelsRepository.getChannels(context) }.getOrNull().orEmpty()
    }

    fun canProceed(): Boolean = when (step) {
        0 -> name.isNotBlank() && email.contains("@") && email.contains(".") && gender.isNotEmpty()
        1 -> password.length >= 4 && password == confirm
        else -> true
    }

    fun finish() {
        settings.userName = name.trim()
        settings.userEmail = email.trim()
        settings.userGender = gender
        if (password.isNotBlank()) settings.filterPassword = password
        settings.filterLevel = level
        selected.forEach { id -> if (!store.isSubscribed(id)) store.toggleSubscription(id) }
        settings.trialStartMillis = System.currentTimeMillis()
        settings.onboardingDone = true
        onDone()
    }

    Column(
        modifier = Modifier.fillMaxSize().background(ThemeState.bg)
            .padding(horizontal = 22.dp).padding(top = 36.dp, bottom = 24.dp),
    ) {
        // ── מותג + פס התקדמות ──
        Text(
            "FilterTube",
            fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            color = ThemeState.accent,
        )
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(total) { i ->
                Box(
                    modifier = Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(50))
                        .then(
                            if (i <= step) Modifier.background(Brush.linearGradient(ThemeState.accentColors))
                            else Modifier.background(ThemeState.divider),
                        ),
                )
            }
        }
        Spacer(Modifier.height(26.dp))

        // ── תוכן השלב ──
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (slideInHorizontally { -it } + fadeIn(tween(250))) togetherWith
                    (slideOutHorizontally { it } + fadeOut(tween(200)))
            },
            label = "onboard",
            modifier = Modifier.weight(1f),
        ) { s ->
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (s) {
                    0 -> StepIdentity(name, { name = it }, email, { email = it }, gender, { gender = it })
                    1 -> StepPassword(password, { password = it }, confirm, { confirm = it })
                    2 -> StepLevel(level) { level = it }
                    3 -> StepArtists(channels, selected)
                    else -> StepWelcome(name)
                }
            }
        }

        // ── ניווט ──
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) {
                TextButton(onClick = { step-- }) { Text("חזרה", color = ThemeState.subtext2) }
                Spacer(Modifier.width(8.dp))
            }
            val isLast = step == total - 1
            Button(
                onClick = { if (isLast) finish() else if (canProceed()) step++ },
                enabled = canProceed(),
                modifier = Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(16.dp)),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ThemeState.accent,
                    disabledContainerColor = ThemeState.divider,
                ),
            ) {
                Text(
                    if (isLast) "כניסה לאפליקציה ✨" else "המשך",
                    fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                )
            }
        }
    }
}

// ─────────────────────────────── שלבים ───────────────────────────────

@Composable
private fun StepHeader(title: String, subtitle: String) {
    Text(title, color = ThemeState.text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 30.sp)
    Spacer(Modifier.height(6.dp))
    Text(subtitle, color = ThemeState.subtext2, fontSize = 14.sp, lineHeight = 20.sp)
    Spacer(Modifier.height(22.dp))
}

@Composable
private fun StepIdentity(
    name: String, onName: (String) -> Unit,
    email: String, onEmail: (String) -> Unit,
    gender: String, onGender: (String) -> Unit,
) {
    StepHeader("ברוכים הבאים 👋", "נכיר אותך רגע — כדי להתאים לך את התוכן.")
    OnbField(name, onName, "שם", Icons.Default.Person)
    Spacer(Modifier.height(12.dp))
    OnbField(email, onEmail, "אימייל", Icons.Default.Email, keyboard = KeyboardType.Email)
    Spacer(Modifier.height(22.dp))
    Text("מגדר", color = ThemeState.subtext2, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GenderCard("זכר", "👨", gender == "male", Modifier.weight(1f)) { onGender("male") }
        GenderCard("נקבה", "👩", gender == "female", Modifier.weight(1f)) { onGender("female") }
    }
}

@Composable
private fun StepPassword(
    password: String, onPassword: (String) -> Unit,
    confirm: String, onConfirm: (String) -> Unit,
) {
    StepHeader("סיסמת הורים 🔒", "הסיסמה הזו תגן על שינוי רמת הסינון, Shorts והגדרות רגישות. בחר/י סיסמה שילדים לא ינחשו.")
    OnbField(password, onPassword, "סיסמה (4 ספרות לפחות)", Icons.Default.Lock, password = true, keyboard = KeyboardType.NumberPassword)
    Spacer(Modifier.height(12.dp))
    OnbField(confirm, onConfirm, "אישור סיסמה", Icons.Default.Lock, password = true, keyboard = KeyboardType.NumberPassword)
    if (confirm.isNotEmpty() && confirm != password) {
        Spacer(Modifier.height(8.dp))
        Text("הסיסמאות אינן תואמות", color = ThemeState.accent2, fontSize = 12.sp)
    }
}

@Composable
private fun StepLevel(level: Int, onLevel: (Int) -> Unit) {
    StepHeader("רמת סינון 🛡️", "אפשר לשנות בכל רגע (עם סיסמת ההורים).")
    LevelCard(1, "מחמיר", "מוזיקה נשמעת כאודיו בלבד · ערוצי ״דתי לייט״ מוסתרים", level == 1) { onLevel(1) }
    Spacer(Modifier.height(10.dp))
    LevelCard(2, "רגיל", "הכל וידאו · ערוצי ״דתי לייט״ מוסתרים", level == 2) { onLevel(2) }
    Spacer(Modifier.height(10.dp))
    LevelCard(3, "דתי לייט", "ערוצי ״דתי לייט״ מוצגים ומתנגנים כאודיו", level == 3) { onLevel(3) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepArtists(channels: List<Channel>, selected: MutableList<String>) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    LaunchedEffect(channels) {
        if (channels.isNotEmpty()) runCatching { ChannelAvatars.warm(context, channels.map { it.youtubeChannelId }) }
    }
    StepHeader("מה אוהבים לשמוע 🎵", "בחר/י זמרים/ערוצים — נתאים לך את הבית ונשלח התראות. אפשר לחפש לפי שם.")
    if (channels.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ThemeState.accent)
        }
        return
    }
    OnbField(query, { query = it }, "חיפוש זמר / ערוץ", Icons.Default.Search)
    Spacer(Modifier.height(16.dp))
    // קודם מוזיקה (הכי רלוונטי ל"זמרים"), ומסונן לפי החיפוש
    val ordered = channels
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        .sortedByDescending { it.category == "music" }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        ordered.forEach { ch ->
            val sel = ch.youtubeChannelId in selected
            ArtistChip(ch.name, ChannelAvatars.cache[ch.youtubeChannelId], sel) {
                if (sel) selected.remove(ch.youtubeChannelId) else selected.add(ch.youtubeChannelId)
            }
        }
    }
}

@Composable
private fun StepWelcome(name: String) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(50))
                .background(Brush.linearGradient(ThemeState.accentColors)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(52.dp)) }
        Spacer(Modifier.height(22.dp))
        Text(
            if (name.isNotBlank()) "הכל מוכן, $name!" else "הכל מוכן!",
            color = ThemeState.text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "קיבלת 60 ימי ניסיון חינם לכל הפיצ'רים המתקדמים:",
            color = ThemeState.subtext2, fontSize = 14.sp,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        PerkRow(Icons.Default.Download, "הורדות מהירות לצפייה לא־מקוונת")
        PerkRow(Icons.Default.MusicNote, "ניגון ברקע ומסך כבוי")
        PerkRow(Icons.Default.Shield, "סינון מותאם אישית עם סיסמת הורים")
        PerkRow(Icons.Default.Face, "בית מותאם אישית לפי מה שאהבת")
    }
}

// ─────────────────────────────── רכיבים ───────────────────────────────

@Composable
private fun OnbField(
    value: String, onChange: (String) -> Unit, label: String, icon: ImageVector,
    password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, tint = ThemeState.subtext2) },
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(15.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ThemeState.accent,
            unfocusedBorderColor = ThemeState.divider,
            focusedLabelColor = ThemeState.accent,
            unfocusedLabelColor = ThemeState.subtext2,
            focusedTextColor = ThemeState.text,
            unfocusedTextColor = ThemeState.text,
            cursorColor = ThemeState.accent,
        ),
    )
}

@Composable
private fun GenderCard(label: String, emoji: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(18.dp))
            .then(if (selected) Modifier.background(Brush.linearGradient(ThemeState.accentColors)) else Modifier.background(ThemeState.card))
            .border(1.dp, if (selected) Color.Transparent else ThemeState.divider, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 34.sp)
        Spacer(Modifier.height(8.dp))
        Text(label, color = if (selected) Color.White else ThemeState.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LevelCard(num: Int, title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(ThemeState.card)
            .border(if (selected) 2.dp else 1.dp, if (selected) ThemeState.accent else ThemeState.divider, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = ThemeState.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = ThemeState.subtext2, fontSize = 12.sp, lineHeight = 17.sp)
        }
        if (selected) {
            Box(modifier = Modifier.size(26.dp).clip(RoundedCornerShape(50)).background(ThemeState.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun ArtistChip(name: String, avatar: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(50))
            .then(if (selected) Modifier.background(Brush.linearGradient(ThemeState.accentColors)) else Modifier.background(ThemeState.card))
            .border(1.dp, if (selected) Color.Transparent else ThemeState.divider, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(start = 5.dp, end = 14.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (avatar != null) {
            AsyncImage(model = avatar, contentDescription = null,
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(50)).background(ThemeState.bg))
        } else {
            Box(
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(50)).background(ThemeState.bg),
                contentAlignment = Alignment.Center,
            ) { Text(name.firstOrNull()?.uppercase() ?: "?", color = ThemeState.subtext2, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        }
        Spacer(Modifier.width(8.dp))
        if (selected) {
            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(name, color = if (selected) Color.White else ThemeState.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PerkRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(ThemeState.card), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = ThemeState.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(13.dp))
        Text(text, color = ThemeState.text, fontSize = 13.5f.sp)
    }
}
