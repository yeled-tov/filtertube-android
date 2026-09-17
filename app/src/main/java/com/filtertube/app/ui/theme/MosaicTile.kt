package com.filtertube.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.filtertube.app.ThemeState

/**
 * קובייה שבנויה ממה שיש בתוכה.
 *
 * ## למה פסיפס ולא אייקון
 * אייקון של לב אומר "אהבתי" — אבל לא אומר *מה* אהבת. הכריכות של הפריטים
 * הראשונים עונות על זה במבט אחד, ולכן הקובייה מפסיקה להיות תווית והופכת
 * להצצה. זו אותה שפה שיוטיוב מיוזיק ומטרוליסט משתמשות בה לאוספים.
 *
 * ## למה דווקא ארבע
 * תמונה אחת נראית כמו פריט בודד ולא כמו אוסף; תשע קטנות מדי מכדי לזהות
 * משהו. ארבע היא המספר שבו עוד רואים מה בפנים וכבר מבינים שיש עוד.
 *
 * ## למה הכיתוב על התמונה ולא מתחתיה
 * כיתוב מתחת מקצר את התמונה בכל שורה. מעליה הוא לא עולה שום מקום, ובלבד
 * שיש רקע שמבטיח קריאוּת — כאן מדרג כהה שמתחזק כלפי מטה, שלא פוגע בחלק
 * העליון של הכריכות ובכל זאת נותן לטקסט על מה לשבת.
 */
@Composable
fun MosaicTile(
    title: String,
    subtitle: String,
    images: List<String>,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(18.dp))
            .background(ThemeState.card)
            .clickable(onClick = onClick),
    ) {
        val shown = images.filter { it.isNotBlank() }.take(4)
        if (shown.isEmpty()) {
            // אוסף ריק — האייקון חוזר להיות הנושא, על רקע בגוון שלו.
            Box(
                modifier = Modifier.fillMaxSize().background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = tint.copy(alpha = 0.55f), modifier = Modifier.size(40.dp))
            }
        } else {
            MosaicGrid(shown)
        }

        // מדרג כהה — בלעדיו טקסט לבן מעל כריכה בהירה נעלם.
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.35f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.78f),
                ),
            ),
        )

        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(15.dp))
                androidx.compose.foundation.layout.Spacer(Modifier.size(5.dp))
                Text(
                    title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * פריסת הכריכות לפי כמה יש.
 *
 * פריט אחד ממלא את הקובייה, שניים מתחלקים לאורך, ושלושה או ארבעה יושבים
 * ברשת 2×2. כשיש שלושה, האחרון נמתח על כל השורה — כך אין חור ריק שנראה
 * כמו תקלת טעינה.
 */
@Composable
private fun MosaicGrid(images: List<String>) {
    val gap = 1.5.dp
    when (images.size) {
        1 -> Tile(images[0], Modifier.fillMaxSize())
        2 -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Tile(images[0], Modifier.weight(1f).fillMaxSize())
            Tile(images[1], Modifier.weight(1f).fillMaxSize())
        }
        3 -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Tile(images[0], Modifier.weight(1f).fillMaxSize())
                Tile(images[1], Modifier.weight(1f).fillMaxSize())
            }
            Tile(images[2], Modifier.weight(1f).fillMaxWidth())
        }
        else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Tile(images[0], Modifier.weight(1f).fillMaxSize())
                Tile(images[1], Modifier.weight(1f).fillMaxSize())
            }
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Tile(images[2], Modifier.weight(1f).fillMaxSize())
                Tile(images[3], Modifier.weight(1f).fillMaxSize())
            }
        }
    }
}

@Composable
private fun Tile(url: String, modifier: Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.background(ThemeState.bg2),
    )
}
