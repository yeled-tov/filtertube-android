package com.filtertube.app.data

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * לקוח HTTP אחד משותף לכל האפליקציה.
 *
 * ## הבעיה
 * היו 17 מופעים נפרדים של OkHttpClient — אחד כמעט בכל קובץ שניגש לרשת.
 * כל מופע מחזיק **מאגר חיבורים משלו ומאגר תהליכונים משלו**. בפועל:
 *
 *  • חיבור פתוח ל-googlevideo שנוצר בחילוץ הזרם לא היה זמין להורדה,
 *    ולהיפך — כל אחד פתח חיבור חדש לאותו מארח בדיוק
 *  • כל חיבור חדש ל-HTTPS הוא לחיצת יד TLS מלאה, כלומר עוד סבב רשת
 *    לפני שמגיע בייט אחד של תוכן
 *  • 17 מאגרי תהליכונים שיושבים בזיכרון גם כשאף בקשה לא רצה
 *
 * ## הפתרון
 * מופע בסיס אחד, וכל אתר קריאה גוזר ממנו עם `newBuilder()`. זה בדיוק
 * הדפוס שהתיעוד של OkHttp ממליץ עליו: הגרסה הנגזרת **חולקת** את מאגר
 * החיבורים ואת מאגר התהליכונים, ומשנה רק את מה שהיא צריכה — למשל
 * timeout שונה.
 */
object Http {

    /**
     * מאגר חיבורים נדיב יחסית.
     *
     * ניגון סרטון יחיד נוגע בו-זמנית ב-googlevideo (וידאו), googlevideo
     * (אודיו), youtube.com ו-i.ytimg.com. שמונה חיבורים בהמתנה מכסים את
     * זה בלי לפתוח הכל מחדש בכל מעבר לשיר הבא.
     */
    private val pool = ConnectionPool(8, 5, TimeUnit.MINUTES)

    private val dispatcher = Dispatcher().apply {
        maxRequests = 48
        // ── למה 16 ולא פחות ──────────────────────────────────────────────
        // תקרה נמוכה מדי הייתה מאטה דווקא את מה שעובד: הורדה מפוצלת לוקחת
        // עד 8 חיבורים לקובץ, ושלוש הורדות במקביל הן 24 בקשות לאותו מארח.
        // 8 היו הופכות את ההורדות לאיטיות פי שלושה.
        //
        // ומצד שני — ההצפה שהפילה את ה-RSS נשלטת ממילא בסמפור ייעודי שם,
        // ולא ברמת הלקוח. התקרה כאן היא רשת ביטחון, לא מנגנון הוויסות.
        maxRequestsPerHost = 16
    }

    /**
     * הבסיס. אין לקרוא לו ישירות לבקשות עם דרישות זמן מיוחדות —
     * גוזרים ממנו ב-[newBuilder].
     */
    val shared: OkHttpClient = OkHttpClient.Builder()
        .connectionPool(pool)
        .dispatcher(dispatcher)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /** גוזר לקוח עם הגדרות משלו, תוך שיתוף מאגר החיבורים והתהליכונים. */
    fun newBuilder(): OkHttpClient.Builder = shared.newBuilder()
}
