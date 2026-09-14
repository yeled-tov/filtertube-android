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

    /**
     * מעבר לזה מניחים שמשהו השתבש וממשיכים בכל מקרה.
     *
     * חייב להיות גדול מ-PLAYBACK_GRACE_MS, אחרת עבודת רקע מוותרת על ההמתנה
     * עוד לפני שהנגן שחרר את השער — וכל המנגנון לא שווה כלום.
     */
    private const val MAX_WAIT_MS = 20_000L

    private val active = AtomicInteger(0)

    /**
     * האם הנגן ממלא באפר *ברגע זה*.
     *
     * ## למה חלון קבוע לא הספיק
     * השער נסגר לשתים-עשרה שניות בתחילת הניגון ואז נפתח לתמיד. זה כיסה את
     * הטעינה הראשונה, אבל בדיוק בשנייה השתים-עשרה כל עבודות הרקע יצאו לדרך
     * בבת אחת — עשרה חילוצים לתור הרדיו ועוד שמונה לחימום. הבאפר של השיר
     * התרוקן, והמשתמש שמע בדיוק את מה שתיאר: "מתחיל מהר, אחרי כמה שניות
     * נעצר, גומר לטעון את כל התור, וממשיך".
     *
     * מצב הבאפר הוא המדד הנכון ולא השעון: כשהנגן מתחיל למלא — הוא זקוק
     * לרשת, לא משנה אם זו השנייה השנייה או המאתיים. [MAX_WAIT_MS] מבטיח
     * שעבודת רקע לא תירעב לנצח גם אם הבאפר מתעקש.
     */
    @Volatile
    private var buffering = false

    @Volatile
    private var gate: CompletableDeferred<Unit> =
        CompletableDeferred<Unit>().also { it.complete(Unit) }

    private val busy: Boolean get() = active.get() > 0 || buffering

    @Synchronized
    private fun refresh(reason: String) {
        if (busy) {
            if (gate.isCompleted) {
                gate = CompletableDeferred()
                Diagnostics.log("PRIORITY: $reason — עבודות רקע ממתינות")
            }
        } else if (!gate.isCompleted) {
            gate.complete(Unit)
            Diagnostics.log("PRIORITY: החזית פנויה — עבודות הרקע ממשיכות")
        }
    }

    /** מסמן שניגון תופס עכשיו את הרשת. חייב להיות מלווה ב-[end] ב-finally. */
    fun begin() {
        active.incrementAndGet()
        refresh("ניגון בחזית")
    }

    fun end() {
        if (active.decrementAndGet() < 0) active.set(0)
        refresh("ניגון בחזית")
    }

    /** מדווח על מצב הבאפר של הנגן. נקרא מ-PlaybackService. */
    fun setBuffering(value: Boolean) {
        if (buffering == value) return
        buffering = value
        refresh("הנגן ממלא באפר")
    }

    /** נקודת המתנה לעבודת רקע, לפני כל יחידת רשת. */
    suspend fun awaitIdle() {
        if (!busy) return
        withTimeoutOrNull(MAX_WAIT_MS) { gate.await() }
    }
}
