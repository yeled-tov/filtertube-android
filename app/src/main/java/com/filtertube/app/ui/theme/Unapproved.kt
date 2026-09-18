package com.filtertube.app.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

/**
 * מאפיר פריט שהערוץ שלו אינו ברשימה המאושרת.
 *
 * ## למה שקיפות לבדה לא הספיקה
 * עד עכשיו פריט לא מאושר צויר עם `alpha(0.45f)`. שקיפות מחווירה צבע אבל
 * לא מבטלת אותו: כריכה אדומה נשארת אדומה, רק חלשה יותר. על מסך מלא
 * פריטים זה נקרא כמו "תמונה שעוד נטענת" ולא כמו "זה לא זמין", ולכן
 * המשתמש ממשיך ללחוץ ולא מבין למה כלום לא קורה.
 *
 * כאן העיבוד נעשה על השכבה כולה — תמונה, טקסט וסמלים יחד — במטריצת
 * רוויה 0, כלומר אפור אמיתי. ההבדל בין "מאושר" ל"לא מאושר" הופך להבדל
 * שרואים מקצה המסך, ולא הבדל בעוצמה.
 *
 * ## למה saveLayer ולא colorFilter על כל תמונה בנפרד
 * הפילטר חייב לחול על מה שכבר צויר, ולכן הוא חייב שכבה משלו. היתרון
 * הצדדי הוא שאין צורך לחווט פרמטר "מאושר" דרך כל רכיב שורה באפליקציה:
 * העטיפה עובדת על כל תוכן שהוא, וגם על רכיבים שייכתבו אחר כך.
 *
 * ## מה זה **לא**
 * זו הצגה בלבד. האפור אינו מה שמונע ניגון — את זה עושה הרשימה הלבנה
 * בכל מסך בנפרד, ולחיצה על פריט אפור מגיעה לטופס הבקשה ולא לנגן. אסור
 * להסתמך על הקובץ הזה כשכבת אכיפה.
 */
fun Modifier.unapprovedLook(enabled: Boolean = true, alpha: Float = 0.55f): Modifier =
    if (!enabled) this else drawWithContent {
        val paint = Paint().apply {
            this.alpha = alpha
            colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        }
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
            drawContent()
            canvas.restore()
        }
    }
