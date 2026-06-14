package com.filtertube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    shortsEnabled: Boolean,
    onShortsToggle: (Boolean) -> Unit,
    filterLevel: Int,
    onFilterLevelChange: (Int) -> Unit,
    onOpenAdmin: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F))) {
        Text(
            "הגדרות", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp),
        )
        HorizontalDivider(color = Color(0xFF272727))

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            // רמת סינון
            SettingsSection("רמת סינון")
            FilterLevelOption(
                selected = filterLevel == 1,
                title = "רמה 1 — מחמיר",
                desc = "סרטוני מוזיקה מתנגנים כאודיו בלבד (ללא וידאו). שאר התוכן כווידאו רגיל.",
                onClick = { onFilterLevelChange(1) },
            )
            FilterLevelOption(
                selected = filterLevel == 2,
                title = "רמה 2 — רגיל",
                desc = "כל הסרטונים מתנגנים כווידאו רגיל. ערוצי \"דתי לייט\" אינם מוצגים.",
                onClick = { onFilterLevelChange(2) },
            )
            FilterLevelOption(
                selected = filterLevel == 3,
                title = "רמה 3 — דתי לייט",
                desc = "מוסיף את ערוצי \"דתי לייט\" (שתסווג בפאנל הניהול). " +
                    "כל התוכן שלהם מתנגן כאודיו בלבד, ללא וידאו. בשאר הרמות הערוצים האלה מוסתרים.",
                onClick = { onFilterLevelChange(3) },
            )

            // תצוגה
            SettingsSection("תצוגה")
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBox(Icons.Default.PlayArrow, Color(0xFFFF0000))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("הצג טאב Shorts", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("סרטונים קצרים מהערוצים המאושרים", color = Color(0xFF888888), fontSize = 12.sp)
                }
                Switch(
                    checked = shortsEnabled, onCheckedChange = onShortsToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White, checkedTrackColor = Color(0xFFFF0000),
                        uncheckedThumbColor = Color(0xFF888888), uncheckedTrackColor = Color(0xFF333333),
                    ),
                )
            }

            // ניהול
            SettingsSection("ניהול")
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenAdmin)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBox(Icons.Default.AdminPanelSettings, Color(0xFFFFAA00))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("פאנל ניהול ערוצים", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("הוסף / הסר ערוצים מהרשימה הלבנה", color = Color(0xFF888888), fontSize = 12.sp)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color(0xFF666666))
            }

            // אודות
            SettingsSection("אודות")
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBox(Icons.Default.Info, Color(0xFFAAAAAA))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("FilterTube", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text("פלטפורמת וידאו מסוננת — רק ערוצים מאושרים", color = Color(0xFF888888), fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun FilterLevelOption(selected: Boolean, title: String, desc: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected, onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Color(0xFFFF0000), unselectedColor = Color(0xFF666666)),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(desc, color = Color(0xFF888888), fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun IconBox(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Box(
        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1F1F1F)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint) }
}

@Composable
private fun SettingsSection(title: String) {
    Text(title, color = Color(0xFFFF0000), fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}
