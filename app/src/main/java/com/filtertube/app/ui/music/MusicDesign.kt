package com.filtertube.app.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.filtertube.app.ThemeState
import com.filtertube.app.data.ChannelAvatars
import com.filtertube.app.data.Video

/**
 * מידות הממשק של FilterMusic, לפי Metrolist.
 *
 * ערך מידה הוא עובדה ולא קוד — הקוד עצמו נכתב כאן מאפס. Metrolist מפורסמת
 * תחת GPL-3.0, ולכן העתקת מקור ממנה הייתה מחייבת את FilterTube כולה להפוך
 * ל-GPL ולקוד פתוח.
 */
object MusicDim {
    val listItemHeight = 64.dp
    val listThumbnail = 48.dp
    val gridThumbnail = 128.dp
    val albumThumbnail = 144.dp
    val artistCircle = 96.dp
    val navBarHeight = 80.dp
    val miniPlayerHeight = 64.dp
    val thumbnailCorner = 6.dp
    val screenPadding = 12.dp
    val playerPadding = 32.dp
}

/**
 * תמונת שיר — ריבועית.
 *
 * התמונות של יוטיוב הן 16:9. חיתוך ל-Crop הוא ההבדל הוויזואלי הגדול ביותר
 * בין אפליקציית וידאו לאפליקציית מוזיקה, והוא מה שגורם לרשת להיראות נכון.
 */
@Composable
fun SongArt(video: Video, size: Dp, modifier: Modifier = Modifier, corner: Dp = MusicDim.thumbnailCorner) {
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(corner)).background(ThemeState.bg2),
        contentAlignment = Alignment.Center,
    ) {
        // כשאין תמונה לסרטון — הסמל של הערוץ, ורק אם גם הוא חסר תו מוזיקלי.
        val fallback = ChannelAvatars.avatar(video.channelId)
        val model = video.thumbnailUrl.takeIf { it.isNotBlank() } ?: fallback
        if (model != null) {
            AsyncImage(
                model = model, contentDescription = null,
                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(Icons.Default.MusicNote, null, tint = ThemeState.subtext, modifier = Modifier.size(size / 3))
        }
    }
}

/** שורת שיר — 64dp גובה, תמונה 48dp, שתי שורות טקסט, ואפשרות לתוכן נגרר. */
@Composable
fun SongListItem(
    video: Video,
    active: Boolean = false,
    playing: Boolean = false,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MusicDim.listItemHeight)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = MusicDim.screenPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let { it(); Spacer(Modifier.width(4.dp)) }
        Box(contentAlignment = Alignment.Center) {
            SongArt(video, MusicDim.listThumbnail)
            // סימון "זה מה שמתנגן" על התמונה עצמה, כמו ביוטיוב מיוזיק.
            if (active) {
                Box(
                    modifier = Modifier.size(MusicDim.listThumbnail)
                        .clip(RoundedCornerShape(MusicDim.thumbnailCorner))
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Equalizer, null,
                        tint = if (playing) ThemeState.accent else Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                video.title,
                color = if (active) ThemeState.accent else ThemeState.text,
                fontSize = 14.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                video.channelName, color = ThemeState.subtext,
                fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.let { Spacer(Modifier.width(6.dp)); it() }
    }
}

/** כרטיס בגריד אופקי — תמונה ריבועית ושתי שורות מתחת. */
@Composable
fun MusicGridItem(video: Video, size: Dp = MusicDim.gridThumbnail, onClick: () -> Unit) {
    Column(modifier = Modifier.width(size).clickable(onClick = onClick)) {
        SongArt(video, size)
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

/** אמן — עיגול עם הסמל האמיתי של הערוץ. */
@Composable
fun ArtistCircle(
    channelId: String,
    name: String,
    highlighted: Boolean,
    size: Dp = MusicDim.artistCircle,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(size).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(ThemeState.bg2),
            contentAlignment = Alignment.Center,
        ) {
            val avatar = ChannelAvatars.avatar(channelId)
            if (avatar != null) {
                AsyncImage(
                    model = avatar, contentDescription = null,
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                )
            } else {
                // עד שהסמל נמשך — צללית אדם, לא אות באנגלית. שם עברי שמוצג
                // כאות לטינית בודדת נראה כמו תקלה, וזה גם לא מזהה כלום.
                Icon(
                    Icons.Default.Person, null,
                    tint = ThemeState.subtext, modifier = Modifier.size(size / 2.4f),
                )
            }
            if (highlighted) {
                Box(
                    modifier = Modifier.fillMaxSize().clip(CircleShape)
                        .background(ThemeState.accent.copy(alpha = 0.22f)),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            name, color = ThemeState.text, fontSize = 12.sp,
            maxLines = 2, textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis, lineHeight = 15.sp,
        )
    }
}

/**
 * כותרת קטע — שם גדול ומודגש בצבע ההדגשה, עם חץ כשיש לאן ללחוץ.
 *
 * זו הצורה שחוזרת בכל קטע ב-Metrolist וביוטיוב מיוזיק, והיא מה שנותן לדף
 * הבית את הקצב שלו: כותרת גדולה, רצועה, כותרת גדולה, רצועה.
 */
@Composable
fun NavigationTitle(
    title: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = MusicDim.screenPadding, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            label?.let {
                Text(it, color = ThemeState.subtext, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Text(
                title, color = ThemeState.accent, fontSize = 20.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft, null,
                tint = ThemeState.subtext, modifier = Modifier.size(22.dp),
            )
        }
    }
}
