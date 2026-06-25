package com.filtertube.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.filtertube.app.ThemeState

data class GlassNavItem(val route: String, val label: String, val icon: ImageVector)

/**
 * סרגל ניווט תחתון — זהה 1:1 לגרסת החנות: גלולה מרחפת כהה, והפריט הנבחר מודגש
 * בכרית בגרדיאנט הצבע הראשי עם אייקון + תווית; פריט שאינו נבחר מציג אייקון בלבד.
 */
@Composable
fun GlassNavBar(items: List<GlassNavItem>, currentRoute: String?, onClick: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 26.dp, end = 26.dp, bottom = 16.dp)
            .shadow(16.dp, RoundedCornerShape(22.dp))
            .background(ThemeState.surface.copy(alpha = 0.96f), RoundedCornerShape(22.dp))
            .border(1.dp, ThemeState.divider, RoundedCornerShape(22.dp))
            .height(62.dp)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            Row(
                modifier = Modifier
                    .then(
                        if (selected) Modifier.background(
                            Brush.horizontalGradient(ThemeState.accentColors),
                            RoundedCornerShape(16.dp),
                        ) else Modifier,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onClick(item.route) }
                    .padding(horizontal = if (selected) 16.dp else 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    item.icon, item.label,
                    tint = if (selected) Color.White else ThemeState.subtext,
                    modifier = Modifier.size(22.dp),
                )
                if (selected) {
                    Spacer(Modifier.width(7.dp))
                    Text(item.label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}
