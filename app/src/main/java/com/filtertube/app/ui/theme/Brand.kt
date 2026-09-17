package com.filtertube.app.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
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

/**
 * מחליף המצבים בין FilterTube ל-FilterMusic.
 *
 * ## למה מחליף ולא כפתור
 * קודם ישב כאן כפתור קטן עם המילה "Music". מי שלא ידע מראש שיש אפליקציית
 * מוזיקה לא היה לומד את זה מהכפתור: הוא לא נראה לחיץ, הוא לא אמר לאן הוא
 * מוביל, והוא נעלם בפינה.
 *
 * מחליף פותר את שלוש הבעיות בבת אחת בלי מילה של הסבר: רואים ששני מצבים
 * קיימים, רואים באיזה מהם נמצאים, ורואים שאפשר לעבור לשני. זו אותה שפה
 * שהמשתמש כבר מכיר מיוטיוב וממיוזיק — רק שכאן זו אפליקציה אחת ולכן המעבר
 * הוא מיידי במקום לצאת ולהיכנס.
 *
 * הוא מחליף גם את כותרת האפליקציה: החצי המסומן *הוא* השם, ולכן אין צורך
 * בשורת כותרת נוספת שגונבת גובה.
 */
@Composable
fun ModeSwitch(
    musicMode: Boolean,
    modifier: Modifier = Modifier,
    onSelect: (music: Boolean) -> Unit,
) {
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(Radius.pill)
            .background(ThemeState.bg2)
            .border(1.dp, ThemeState.divider, Radius.pill)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ModeSwitchHalf(
            label = "FilterTube",
            icon = Icons.Rounded.PlayArrow,
            selected = !musicMode,
        ) { onSelect(false) }
        ModeSwitchHalf(
            label = "FilterMusic",
            icon = Icons.Rounded.MusicNote,
            selected = musicMode,
        ) { onSelect(true) }
    }
}

@Composable
private fun ModeSwitchHalf(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // הצבע מונפש ולא מוחלף — החלפה פתאומית נראית כמו רענון מסך, ומעבר רך
    // הוא מה שמסגיר שזה אותו רכיב שהשתנה ולא רכיב אחר שהופיע.
    val content by animateColorAsState(
        targetValue = if (selected) Color.White else ThemeState.subtext,
        animationSpec = Motion.normal(),
        label = "modeSwitchContent",
    )
    Row(
        modifier = Modifier
            .clip(Radius.pill)
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
                enabled = !selected,
                onClick = onClick,
            )
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, color = content, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * סמל האפליקציה — ריבוע מעוגל עם גרדיאנט וסמל הניגון.
 *
 * אותו סמל בכל מקום שצריך לייצג את האפליקציה (מסך פתיחה, ראש תפריט, מסך
 * "אודות"), כדי שהזהות תהיה דבר אחד ולא ציור מחדש בכל מסך.
 */
@Composable
fun BrandMark(size: androidx.compose.ui.unit.Dp = 32.dp, music: Boolean = false) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3.2f))
            .background(Brush.linearGradient(ThemeState.accentColors)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (music) Icons.Rounded.MusicNote else Icons.Rounded.PlayArrow,
            null,
            tint = Color.White,
            modifier = Modifier.size(size * 0.62f),
        )
    }
}
