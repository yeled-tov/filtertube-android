package com.filtertube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filtertube.app.ThemeState

data class GlassNavItem(val route: String, val label: String, val icon: ImageVector)

/**
 * סרגל ניווט צף בסגנון זכוכית (iOS) — פיל מעוגל שקוף-למחצה שמרחף מעל התוכן,
 * כך שרואים את התוכן מבעדו. קומפקטי יותר מ-NavigationBar הרגיל.
 */
@Composable
fun GlassNavBar(items: List<GlassNavItem>, currentRoute: String?, onClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(ThemeState.bg2.copy(alpha = 0.82f))
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            val tint = if (selected) ThemeState.accent else ThemeState.subtext
            // הפריט הנבחר מודגש בכרית מעוגלת בצבע הראשי (כמו בגרסת החנות)
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) ThemeState.accent.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onClick(item.route) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(item.icon, item.label, tint = tint, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(2.dp))
                Text(item.label, color = tint, fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
