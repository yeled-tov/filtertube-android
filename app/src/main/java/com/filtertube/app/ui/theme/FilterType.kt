package com.filtertube.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.filtertube.app.R

/**
 * הטיפוגרפיה של FilterTube.
 *
 * ## למה פונט משלנו
 * ברירת המחדל של אנדרואיד לעברית היא לא החלטה עיצובית — היא מה שהמכשיר
 * במקרה מביא, והיא משתנה בין יצרנים. Rubik נבחר כי הוא נבנה מלכתחילה
 * לעברית **ולטינית יחד**, כך ש"FilterTube" ו"הספרייה שלי" נראים כמו אותה
 * אפליקציה ולא כמו שתי אפליקציות שהודבקו.
 *
 * ## למה קבצים סטטיים ולא פונט משתנה
 * minSdk כאן הוא 24, ופונטים משתנים (variable) נתמכים רק מ-26. קובץ נפרד
 * לכל משקל עובד בכל מכשיר; המחיר הוא ~250KB, אחרי צמצום לתווים שהאפליקציה
 * באמת מציגה (עברית, לטינית, ספרות, פיסוק, ₪).
 */
val Rubik = FontFamily(
    Font(R.font.rubik_regular, FontWeight.Normal),
    Font(R.font.rubik_medium, FontWeight.Medium),
    Font(R.font.rubik_semibold, FontWeight.SemiBold),
    Font(R.font.rubik_bold, FontWeight.Bold),
    Font(R.font.rubik_extrabold, FontWeight.ExtraBold),
)

/**
 * סולם הגדלים.
 *
 * ## למה רווח-שורה נדיב מהרגיל
 * לעברית אין אותיות עולות ויורדות כמו ב-b/g הלטיניות, ולכן שורות עבריות
 * נראות דחוסות יותר באותו lineHeight שנראה נכון באנגלית. היחס כאן הוא
 * ~1.35 ומעלה, וזה מה שמונע מפסקה בעברית להיראות כמו גוש.
 *
 * ## למה אין letterSpacing חיובי
 * ריווח אותיות בעברית שובר את הרצף הוויזואלי של המילה — מה שעוזר לכותרות
 * לטיניות פוגע בעברית. רק בגדלים הגדולים יש ריווח שלילי קל, שם זה מהדק.
 */
val FilterTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Rubik, fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp, lineHeight = 48.sp, letterSpacing = (-0.8).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Rubik, fontWeight = FontWeight.ExtraBold,
        fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.6).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Rubik, fontWeight = FontWeight.Bold,
        fontSize = 27.sp, lineHeight = 34.sp, letterSpacing = (-0.4).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Rubik, fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp, lineHeight = 31.sp, letterSpacing = (-0.3).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Rubik, fontWeight = FontWeight.Bold,
        fontSize = 21.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Rubik, fontWeight = FontWeight.Bold,
        fontSize = 19.sp, lineHeight = 26.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Rubik, fontWeight = FontWeight.Bold,
        fontSize = 17.sp, lineHeight = 24.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Rubik, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 21.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Rubik, fontWeight = FontWeight.SemiBold,
        fontSize = 13.5.sp, lineHeight = 19.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Rubik, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Rubik, fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp, lineHeight = 19.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Rubik, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Rubik, fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp, lineHeight = 19.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Rubik, fontWeight = FontWeight.SemiBold,
        fontSize = 12.5.sp, lineHeight = 17.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Rubik, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 15.sp,
    ),
)
