package com.filtertube.app.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger

/**
 * שער עדיפות: עבודות רקע ממתינות בזמן שסרטון מתחיל לנגן.
 *
 * ## הבעיה שזה פותר
 * ברגע שהמשתמש לוחץ על סרטון, הרשת עמוסה בדיוק בעבודות שאמורות היו לעזור:
 * תור הרדיו מחלץ 10 סרטונים (4 במקביל), החימום מראש מחלץ 8, וההעשרה מחלצת
 * עד 24 ערוצים. כלומר עד תריסר חילוצי NewPipe מקבילים — בדיוק כשהנגן מנסה
 * למלא באפר של הזרם שהמשתמש מחכה לו.
 *
 * התוצאה הייתה שהסרטון "נפתר" תוך ~1.4 שניות, אבל הצליל יצא רק אחרי 5–7
 * שניות: הפתרון היה מהיר, ההורדה בפועל נחנקה.
 *
 * ## הפתרון
 * הניגון מכריז על עצמו כחזית, וכל עבודת רקע קוראת ל-[awaitIdle] לפני כל
 * יחידת רשת. אף עבודה לא מבוטלת — היא רק ממתינה את השניות שבהן זה קריטי.
 *
 * ## למה יש timeout
 * אם מסלול כלשהו ישכח לשחרר את השער, עבודות הרקע היו נתקעות לנצח. לכן
 * ההמתנה מוגבלת: במקרה הגרוע חוזרים להתנהגות הקודמת, לא לתקיעה.
 */
object PlaybackPriority {

    /** מעבר לזה מניחים שמשהו השתבש וממשיכים בכל מקרה. */
    private const val MAX_WAIT_MS = 8_000L

    private val active = AtomicInteger(0)

    @Volatile
    private var gate: CompletableDeferred<Unit> =
        CompletableDeferred<Unit>().also { it.complete(Unit) }

    /** מסמן שניגון תופס עכשיו את הרשת. חייב להיות מלווה ב-[end] ב-finally. */
    fun begin() {
        if (active.incrementAndGet() == 1) {
            gate = CompletableDeferred()
            Diagnostics.log("PRIORITY: ניגון בחזית — עבודות רקע ממתינות")
        }
    }

    fun end() {
        if (active.decrementAndGet() <= 0) {
            active.set(0)
            gate.complete(Unit)
            Diagnostics.log("PRIORITY: החזית פנויה — עבודות הרקע ממשיכות")
        }
    }

    /** נקודת המתנה לעבודת רקע, לפני כל יחידת רשת. */
    suspend fun awaitIdle() {
        if (active.get() == 0) return
        withTimeoutOrNull(MAX_WAIT_MS) { gate.await() }
    }
}
