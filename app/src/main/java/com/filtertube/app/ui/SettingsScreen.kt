package com.filtertube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
) {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0F0F0F))) {
        Text(
            "הגדרות",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 12.dp),
        )
        HorizontalDivider(color = Color(0xFF272727))

        // תצוגה
        SettingsSection("תצוגה")
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1F1F1F)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.PlayArrow, null, tint = Color(0xFFFF0000)) }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("הצג טאב Shorts", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text("סרטונים קצרים מהערוצים המאושרים", color = Color(0xFF888888), fontSize = 12.sp)
            }
            Switch(
                checked = shortsEnabled,
                onCheckedChange = onShortsToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFFFF0000),
                    uncheckedThumbColor = Color(0xFF888888),
                    uncheckedTrackColor = Color(0xFF333333),
                ),
            )
        }

        // אודות
        SettingsSection("אודות")
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF1F1F1F)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Info, null, tint = Color(0xFFAAAAAA)) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("FilterTube", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text("פלטפורמת וידאו מסוננת — רק ערוצים מאושרים", color = Color(0xFF888888), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        title,
        color = Color(0xFFFF0000),
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
