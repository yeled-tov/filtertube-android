package com.filtertube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.filtertube.app.BuildConfig
import com.filtertube.app.ThemeState
import com.filtertube.app.data.BugReport
import com.filtertube.app.data.Diagnostics
import com.filtertube.app.data.SettingsStore
import kotlinx.coroutines.launch

/**
 * מסך אבחון מהירות/עצירות — מציג ביומן: איזה מקור ניצח (VR/IOS/NewPipe), כמה זמן
 * לקחה הטעינה, ועצירות/באפר באמצע הניגון (משך + שנייה). אפשר לשלוח אליי בלחיצה.
 */
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context) }
    var lines by remember { mutableStateOf(Diagnostics.snapshot()) }
    var sending by remember { mutableStateOf(false) }

    fun refresh() { lines = Diagnostics.snapshot() }
    fun copy() {
        val clip = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clip.setPrimaryClip(android.content.ClipData.newPlainText("diag", Diagnostics.text()))
        android.widget.Toast.makeText(context, "הועתק", android.widget.Toast.LENGTH_SHORT).show()
    }

    Column(modifier = Modifier.fillMaxSize().background(ThemeState.bg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 28.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "חזור", tint = ThemeState.text)
            }
            Text("אבחון מהירות / עצירות", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                color = ThemeState.text, modifier = Modifier.weight(1f))
            TextButton(onClick = { refresh() }) { Text("רענן", color = ThemeState.accent) }
        }
        HorizontalDivider(color = ThemeState.divider)

        Text(
            "הפעל סרטון, חזור לכאן ולחץ ״רענן״. ⚠ = עצירה באמצע ניגון.",
            color = ThemeState.subtext, fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        if (lines.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("אין עדיין אירועים — הפעל סרטון", color = ThemeState.subtext, fontSize = 14.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                items(lines) { line ->
                    val warn = line.contains("⚠") || line.contains("✖") || line.contains("נכשל")
                    Text(
                        line,
                        color = if (warn) Color(0xFFFF6A5C) else ThemeState.subtext2,
                        fontSize = 12.sp, lineHeight = 17.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    )
                }
            }
        }

        HorizontalDivider(color = ThemeState.divider)
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = {
                    val token = settings.githubToken.ifBlank { BuildConfig.BUG_REPORT_TOKEN }
                    if (token.isBlank()) { copy(); return@Button }
                    sending = true
                    scope.launch {
                        val ok = BugReport.submit(token, "== אבחון ==\n" + Diagnostics.text())
                        sending = false
                        android.widget.Toast.makeText(context,
                            if (ok) "נשלח ✓" else "השליחה נכשלה — הועתק במקום", android.widget.Toast.LENGTH_SHORT).show()
                        if (!ok) copy()
                    }
                },
                enabled = !sending,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)),
                colors = ButtonDefaults.buttonColors(containerColor = ThemeState.accent),
            ) { Text(if (sending) "שולח…" else "שלח אליי") }
            OutlinedButton(onClick = { copy() }, modifier = Modifier.weight(1f)) { Text("העתק", color = ThemeState.text) }
            OutlinedButton(onClick = { Diagnostics.clear(); refresh() }) { Text("נקה", color = ThemeState.subtext2) }
        }
    }
}
