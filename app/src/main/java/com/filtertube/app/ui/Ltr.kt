package com.filtertube.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * כופה כיווניות שמאל-לימין על כל מה שבפנים.
 *
 * ## למה זה קיים כרכיב אחד
 * הממשק בעברית, ולכן Compose מרנדר כל שורה מימין לשמאל — הילד הראשון נכנס
 * בימין. לפקדי מדיה זה שגוי: זמן זורם משמאל לימין בכל נגן בעולם, כולל
 * בממשקים בעברית. בלי זה "0:00" הופיע בימין, הזמן הכולל בשמאל, ופס
 * ההתקדמות התמלא מימין לשמאל.
 *
 * זה היה מפוזר כ-CompositionLocalProvider ידני בכל אתר בנפרד, ובכל פעם
 * שנוסף פקד חדש שכחתי אחד. עכשיו יש שם אחד לחפש.
 */
@Composable
fun Ltr(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr, content = content)
}

/**
 * מחרוזת זמן שתמיד תוצג נכון, גם בתוך פסקה בעברית.
 *
 * ## הבעיה שזה פותר
 * "1:02:11" הוא רצף של ספרות ונקודתיים. אלגוריתם ה-bidi של המערכת מסווג
 * נקודתיים כתו ניטרלי, ולכן בהקשר עברי הוא יכול להיבלע לכיוון ההפוך —
 * והזמן מוצג הפוך על המסך גם כשהפריסה עצמה נכונה.
 *
 * התו U+200E (LEFT-TO-RIGHT MARK) מקבע את ההקשר לשמאל-לימין. הוא בעל
 * רוחב אפס ולא נראה למשתמש.
 */
fun timeLtr(text: String): String = "‎$text"
