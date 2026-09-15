package com.filtertube.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.filtertube.app.ThemeState

/**
 * כרטיס שמכיל קבוצת שורות.
 *
 * ## למה קבוצה ולא שורה-שורה
 * כשכל שורה היא כרטיס נפרד עם מרווח קטן בינה לבין השכנה, המסך נקרא כערימת
 * מלבנים שכולם באותה חשיבות — אין שום סימן לאילו שורות שייכות זו לזו.
 * כשהקבוצה היא הכרטיס, החלוקה הלוגית הופכת לחלוקה ויזואלית, וזה מה שגורם
 * למסך ארוך להיראות מסודר במקום עמוס.
 */
@Composable
fun GroupCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(ThemeState.card),
        content = content,
    )
}

/** כותרת קטע מעל [GroupCard] — אפורה וקטנה, כדי שהיא תסמן ולא תתחרה. */
@Composable
fun GroupHeader(title: String) {
    Text(
        title,
        color = ThemeState.subtext,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(start = 26.dp, end = 20.dp, top = 22.dp, bottom = 7.dp),
    )
}

/**
 * שורה בתוך [GroupCard].
 *
 * @param subtitle שורת הסבר. null = שורה חד-שורתית וצרה יותר.
 * @param trailingText מונה או ערך נוכחי בקצה השורה.
 * @param locked מסמן שהיעד מוגן בקוד הורים.
 * @param last משמיט את הקו המפריד — לשורה האחרונה בקבוצה.
 */
@Composable
fun GroupRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String? = null,
    trailingText: String? = null,
    locked: Boolean = false,
    last: Boolean = false,
    onClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = if (subtitle == null) 11.dp else 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // אריח האייקון נצבע בגוון עצמו בשקיפות נמוכה ולא באפור אחיד: כך
            // הצבע מופיע פעמיים — ברקע ובאייקון — ונקרא כתווית ולא ככתם.
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp)) }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title, color = ThemeState.text,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    // מנעול כאייקון ולא כאמוג'י בתוך הכותרת: אמוג'י נשלט ע"י
                    // גופן המערכת, משנה צורה בין מכשירים ואינו מיישר לטקסט.
                    if (locked) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.Lock, "מוגן בקוד",
                            tint = ThemeState.subtext, modifier = Modifier.size(13.dp),
                        )
                    }
                }
                if (subtitle != null) {
                    Spacer(Modifier.height(1.dp))
                    Text(subtitle, color = ThemeState.subtext, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (trailingText != null) {
                Spacer(Modifier.width(8.dp))
                Text(trailingText, color = ThemeState.subtext, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight, null,
                tint = ThemeState.divider, modifier = Modifier.size(20.dp),
            )
        }
        // קו מפריד שמתחיל אחרי האייקון ולא מקצה לקצה — מפריד בין שורות בתוך
        // הקבוצה בלי לחתוך את הכרטיס לשניים.
        if (!last) {
            HorizontalDivider(
                color = ThemeState.divider.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 65.dp, end = 14.dp),
            )
        }
    }
}
