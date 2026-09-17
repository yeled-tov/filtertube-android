package com.filtertube.app.ui.theme

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * מחוות הנגן — החלקה להחלפת רצועה, והחלקה למטה לסגירה.
 *
 * ## למה מחווה אחת ולא שתיים נפרדות
 * אצבע אמיתית לא זזה בקו ישר. מחווה אופקית ומחווה אנכית שרשומות בנפרד
 * מתחרות זו בזו: החלקה ימינה עם נטייה קלה למטה יכולה להפעיל את שתיהן,
 * או אף אחת. כאן נאסף ההיסט הכולל, וההכרעה נופלת **פעם אחת בשחרור** לפי
 * הציר הדומיננטי — כלומר התנועה מתפרשת כמו שהמשתמש התכוון לה.
 *
 * ## כיוון
 * **שמאלה = הבא, ימינה = הקודם** — כמו שדף בעברית מתקדם. זו גם בדיוק
 * המחווה שכבר קיימת במיני-נגן, ושתי מחוות הפוכות לאותה פעולה בשני מסכים
 * של אותה אפליקציה הן בדיוק מה שגורם לאנשים להפסיק להשתמש בשתיהן.
 *
 * ## למה שני ספים שונים
 * החלפת רצועה היא פעולה הפיכה (מחליקים חזרה), ולכן הסף שלה נמוך. סגירת
 * הנגן קוטעת את מה שהמשתמש עושה, ולכן היא דורשת תנועה ארוכה בהרבה — אחרת
 * כל גלישה קלה של האצבע סוגרת את המסך.
 *
 * [onDrag] מאפשר למסך להזיז את התוכן עם האצבע. בלי משוב כזה המחווה
 * מרגישה כמו הימור: או שמשהו קרה, או שלא.
 */
fun Modifier.playerSwipeGestures(
    tracksEnabled: Boolean,
    dismissEnabled: Boolean,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDismiss: () -> Unit,
    onDrag: (dx: Float, dy: Float) -> Unit = { _, _ -> },
    onDragFinished: () -> Unit = {},
): Modifier = this.pointerInput(tracksEnabled, dismissEnabled) {
    if (!tracksEnabled && !dismissEnabled) return@pointerInput

    val trackThreshold = 72.dp.toPx()
    val dismissThreshold = 150.dp.toPx()
    var dx = 0f
    var dy = 0f

    detectDragGestures(
        onDragStart = { dx = 0f; dy = 0f },
        onDrag = { change, amount ->
            // צריכת האירוע עוצרת את המסכים שמתחת מלפרש את אותה תנועה
            // כגלילה משלהם.
            change.consume()
            dx += amount.x
            dy += amount.y
            onDrag(
                if (tracksEnabled) dx else 0f,
                // למעלה אף פעם לא סוגר, ולכן אין סיבה שהתוכן יזוז לשם.
                if (dismissEnabled) dy.coerceAtLeast(0f) else 0f,
            )
        },
        onDragEnd = {
            when {
                dismissEnabled && dy > dismissThreshold && dy > abs(dx) -> onDismiss()
                tracksEnabled && abs(dx) > trackThreshold && abs(dx) > abs(dy) ->
                    if (dx < 0) onNext() else onPrevious()
            }
            dx = 0f; dy = 0f
            onDragFinished()
        },
        onDragCancel = {
            dx = 0f; dy = 0f
            onDragFinished()
        },
    )
}
