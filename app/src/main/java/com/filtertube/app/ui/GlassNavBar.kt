package com.filtertube.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.filtertube.app.ThemeState
import com.filtertube.app.ui.theme.Motion
import com.filtertube.app.ui.theme.Radius

data class GlassNavItem(val route: String, val label: String, val icon: ImageVector)

/**
 * סרגל הניווט התחתון.
 *
 * ## למה גלולה נפתחת ולא חמישה אייקונים שווים
 * כשכל הפריטים נראים אותו דבר, המשתמש צריך לקרוא אייקון כדי לדעת איפה הוא.
 * הגלולה עונה על "איפה אני" מהמרחק — היא הדבר היחיד הצבעוני בשורה — ורק
 * הפריט הפעיל משלם במקום על תווית טקסט.
 *
 * ## למה התנועה חשובה כאן
 * המעבר בין לשוניות הוא הפעולה הכי תכופה באפליקציה. כשהגלולה נפתחת ונסגרת
 * ברוך, העין עוקבת אחרי אותו אלמנט שזז; כשהיא קופצת, נראה שהשורה נבנתה
 * מחדש. זה ההבדל בין ממשק שמרגיש יקר לאחד שמרגיש זול, והוא כולו בעיתוי.
 */
@Composable
fun GlassNavBar(items: List<GlassNavItem>, currentRoute: String?, onClick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(ThemeState.surface)) {
        // קו שיער בראש הסרגל — בלעדיו התוכן שנגלל מתחתיו נראה כאילו הוא
        // נמחק לתוך הסרגל במקום לעבור מאחוריו.
        HorizontalDivider(color = ThemeState.divider, thickness = 0.5.dp)
        Row(
            modifier = Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                NavBarItem(item = item, selected = currentRoute == item.route) { onClick(item.route) }
            }
        }
    }
}

@Composable
private fun NavBarItem(item: GlassNavItem, selected: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(
        targetValue = if (selected) Color.White else ThemeState.subtext,
        animationSpec = Motion.normal(),
        label = "navTint",
    )
    val hPadding by animateDpAsState(
        targetValue = if (selected) 15.dp else 13.dp,
        animationSpec = Motion.normal(),
        label = "navPadding",
    )

    Row(
        modifier = Modifier
            .clip(Radius.md)
            .then(
                if (selected) {
                    Modifier.background(Brush.horizontalGradient(ThemeState.accentColors))
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = hPadding, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(item.icon, item.label, tint = tint, modifier = Modifier.size(23.dp))
        // התווית נפתחת לרוחב ולא רק מופיעה: הופעה פתאומית של טקסט דוחפת את
        // השכנים בקפיצה, ופתיחה לרוחב היא מה שגורם לשורה כולה לזוז ברכות.
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn(Motion.normal()) + expandHorizontally(Motion.normal()),
            exit = fadeOut(Motion.quick()) + shrinkHorizontally(Motion.quick()),
        ) {
            Row {
                Spacer(Modifier.width(7.dp))
                Text(item.label, color = Color.White, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
