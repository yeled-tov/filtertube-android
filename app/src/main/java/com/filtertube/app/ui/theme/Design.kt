package com.filtertube.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * סולם המרווחים.
 *
 * ## למה סולם ולא מספרים חופשיים
 * כשכל מסך בוחר לעצמו 13dp או 15dp, העין קולטת את חוסר-הסדר גם בלי לדעת
 * למה. סולם קבוע הוא מה שגורם למסכים שונים להרגיש כמו אותה אפליקציה.
 * הבסיס הוא 4dp, כמו ברשת של אנדרואיד עצמה.
 */
object Space {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val huge = 32.dp

    /** שוליים צדדיים של מסך — אותו ערך בכל המסכים. */
    val screen = 16.dp

    /** מרווח תחתון שמפנה מקום לסרגל הניווט ולמיני-פלייר. */
    val bottomInset = 150.dp
}

/**
 * עיגול הפינות.
 *
 * ## למה שלוש דרגות ולא אחת
 * פינה מעוגלת מסמנת גודל: ככל שהמשטח גדול יותר, הרדיוס גדול יותר, אחרת
 * כרטיס גדול עם פינה קטנה נראה נוקשה וכפתור קטן עם פינה גדולה נראה כמו
 * טיפה. היחס הזה הוא מה שנותן את התחושה ה"מעוצבת".
 */
object Radius {
    val xs = RoundedCornerShape(8.dp)
    val sm = RoundedCornerShape(12.dp)
    val md = RoundedCornerShape(16.dp)
    val lg = RoundedCornerShape(22.dp)
    val xl = RoundedCornerShape(28.dp)
    val pill = RoundedCornerShape(50)

    /** גיליון שנפתח מלמטה — מעוגל למעלה בלבד. */
    val sheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
}

/**
 * תנועה.
 *
 * ## למה עקומה אחת חוזרת
 * אנימציות שכל אחת בקצב אחר מרגישות כמו אפליקציה שהורכבה מחלקים. עקומה
 * משותפת היא מה שגורם למעברים להרגיש כמו מערכת אחת.
 *
 * emphasized היא העקומה של Material 3: יציאה מהירה, כניסה רכה — היא מה
 * שנותן לתנועה להיראות "יקרה" במקום ליניארית.
 */
object Motion {
    val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val standard = FastOutSlowInEasing

    fun <T> quick() = tween<T>(durationMillis = 150, easing = standard)
    fun <T> normal() = tween<T>(durationMillis = 260, easing = emphasized)
    fun <T> slow() = tween<T>(durationMillis = 420, easing = emphasized)
}

/**
 * גווני הסימון של האייקונים.
 *
 * ## למה פלטה ולא צבע לכל שורה
 * בהגדרות ישבו עד עכשיו שנים-עשר גוונים שנבחרו אחד-אחד: אדום מלא
 * (0xFFFF0000) ליד ירוק עמום (0xFF10B981) ליד צהוב זוהר (0xFFFFC107).
 * הבעיה אינה הריבוי אלא חוסר ההתאמה: כשכל גוון בעוצמה אחרת, העין רואה
 * בלגן ולא קידוד. הגוונים כאן כולם באותה רוויה ובאותה בהירות, ולכן ריבוי
 * הצבעים נקרא כמערכת — בדיוק כמו קידוד צבע במפה טובה.
 *
 * צבע נושא מידע רק כשהוא עקבי, ולכן לכל גוון יש כאן משמעות קבועה:
 * אדום = יוטיוב, ענבר = הגנה וסינון, זהב = פרימיום, ירוק = מדיה, וכו'.
 */
object Tint {
    val red = Color(0xFFE5484D)
    val orange = Color(0xFFF07B3F)
    val amber = Color(0xFFE0A63C)
    val gold = Color(0xFFD4A62A)
    val green = Color(0xFF3BA46C)
    val teal = Color(0xFF29A3A3)
    val blue = Color(0xFF4A7BF7)
    val violet = Color(0xFF8B62F0)
    val pink = Color(0xFFDB5BA0)
}
