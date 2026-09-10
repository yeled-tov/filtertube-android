package com.filtertube.app.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.filtertube.app.ThemeState
import com.filtertube.app.data.Video

/**
 * מידות הממשק של FilterMusic.
 *
 * הערכים לקוחים מ-Metrolist, שהמשתמש ביקש שנראה כמוה. ערך מידה הוא עובדה
 * ולא קוד — הקוד עצמו נכתב כאן מאפס. Metrolist מפורסמת תחת GPL-3.0, ולכן
 * העתקת מקור ממנה הייתה מחייבת את FilterTube כולה להפוך ל-GPL ולקוד פתוח.
 *
 * מה שמייצר את המראה הוא בעיקר היחסים: תמונה ריבועית עם פינות כמעט חדות,
 * שורת רשימה נמוכה (64dp) עם תמונה קטנה (48dp), וכרטיסי גריד של 128dp.
 */
object MusicDim {
    val listItemHeight = 64.dp
    val listThumbnail = 48.dp
    val gridThumbnail = 128.dp
    val artistCircle = 96.dp
    val navBarHeight = 80.dp
    val thumbnailCorner = 6.dp
    val screenPadding = 12.dp
}

/**
 * תמונת שיר — ריבועית, עם fallback לתו מוזיקלי.
 *
 * ל-Video של FilterTube יש תמונה ממוזערת של יוטיוב ביחס 16:9. חיתוך ל-Crop
 * הוא מה שנותן את הריבוע של אפליקציית מוזיקה במקום מלבן של אפליקציית וידאו.
 */
@Composable
fun SongArt(video: Video, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(MusicDim.thumbnailCorner))
            .background(ThemeState.bg2),
        contentAlignment = Alignment.Center,
    ) {
        if (video.thumbnailUrl.isNotBlank()) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Default.MusicNote, null,
                tint = ThemeState.subtext, modifier = Modifier.size(size / 3),
            )
        }
    }
}

/** שורת שיר ברשימה — 64dp גובה, תמונה 48dp. */
@Composable
fun MusicListItem(
    video: Video,
    playing: Boolean = false,
    trailing: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(MusicDim.listItemHeight)
            .clickable(onClick = onClick)
            .padding(horizontal = MusicDim.screenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongArt(video, MusicDim.listThumbnail)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                video.title,
                color = if (playing) ThemeState.accent else ThemeState.text,
                fontSize = 14.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                video.channelName, color = ThemeState.subtext,
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.let { Spacer(Modifier.width(8.dp)); it() }
    }
}

/** כרטיס בגריד אופקי — תמונה 128dp ושתי שורות טקסט מתחת. */
@Composable
fun MusicGridItem(video: Video, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(MusicDim.gridThumbnail)
            .clickable(onClick = onClick),
    ) {
        SongArt(video, MusicDim.gridThumbnail)
        Spacer(Modifier.height(6.dp))
        Text(
            video.title, color = ThemeState.text, fontSize = 13.sp,
            fontWeight = FontWeight.Medium, maxLines = 2,
            overflow = TextOverflow.Ellipsis, lineHeight = 17.sp,
        )
        Text(
            video.channelName, color = ThemeState.subtext, fontSize = 11.5.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** אמן — עיגול עם האות הראשונה, כמו באפליקציות מוזיקה בלי תמונות פרופיל. */
@Composable
fun ArtistCircle(name: String, highlighted: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.width(MusicDim.artistCircle).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(MusicDim.artistCircle)
                .clip(CircleShape)
                .background(if (highlighted) ThemeState.accent else ThemeState.bg2),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.trim().take(1).ifBlank { "?" },
                color = if (highlighted) Color.White else ThemeState.subtext2,
                fontSize = 30.sp, fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            name, color = ThemeState.text, fontSize = 12.sp,
            maxLines = 2, textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis, lineHeight = 15.sp,
        )
    }
}

/** כותרת קטע — טיפוגרפיה גדולה ומודגשת, כמו בכל אפליקציית מוזיקה. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = ThemeState.text, fontSize = 19.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = modifier.padding(horizontal = MusicDim.screenPadding, vertical = 10.dp),
    )
}
