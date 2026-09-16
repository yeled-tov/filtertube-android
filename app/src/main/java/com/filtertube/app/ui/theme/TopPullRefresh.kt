package com.filtertube.app.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.filtertube.app.ThemeState

/**
 * משיכה לרענון — אבל רק ממשיכה שהתחילה בראש העמוד.
 *
 * ## הבעיה עם משיכה רגילה
 * הרענון הסטנדרטי מופעל בכל פעם שהגלילה נתקעת בקצה העליון. כלומר מי שגולל
 * חזרה למעלה דרך חמישים סרטונים, ובתנופה אחת מגיע לראש — מקבל רענון שלא
 * ביקש, והמקום שבו היה אובד. ככל שהרשימה ארוכה יותר, כך זה קורה יותר.
 *
 * ## הפתרון
 * הדגל נקבע ברגע שמתחילה גלילה חדשה: האם באותו רגע כבר היינו בראש. תנופה
 * שהתחילה מאמצע הרשימה תגיע לראש עם דגל כבוי, ותיעצר שם בלי לרענן. כדי
 * לרענן צריך להרים את האצבע ולמשוך שוב — וזו בדיוק הכוונה המפורשת שמבדילה
 * בין "חזרתי למעלה" לבין "תביא לי חדש".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopPullRefresh(
    isRefreshing: Boolean,
    /** האם הרשימה בראש ממש (הפריט הראשון, בלי היסט). */
    atTop: () -> Boolean,
    /** האם גלילה פעילה כרגע — משמש לזיהוי תחילת מחווה. */
    scrolling: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    // מתחיל דלוק: בכניסה למסך הרשימה בראש ועוד לא הייתה גלילה.
    var armed by remember { mutableStateOf(true) }

    LaunchedEffect(scrolling) {
        // רק בתחילת מחווה. בסופה הערך נשאר כפי שהיה, כדי שמשיכה שהתחילה
        // מלמעלה לא תיפסל באמצע רק מפני שהרשימה זזה קצת.
        if (scrolling) armed = atTop()
    }

    Box(
        modifier = modifier.pullToRefresh(
            isRefreshing = isRefreshing,
            state = state,
            enabled = armed,
            onRefresh = onRefresh,
        ),
    ) {
        content()
        PullToRefreshDefaults.Indicator(
            state = state,
            isRefreshing = isRefreshing,
            modifier = Modifier.align(Alignment.TopCenter),
            containerColor = ThemeState.surface,
            color = ThemeState.accent,
        )
    }
}
