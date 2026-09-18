package com.filtertube.app.data

import android.content.Context

/**
 * השער היחיד: האם מותר לגעת בתוכן הזה בכלל.
 *
 * ## למה זה נוצר
 * הרשימה הלבנה נאכפה עד עכשיו **בכל מסך בנפרד** — כל מסך סינן את מה שהוא
 * מציג, והניח שמה שהגיע לנגן כבר עבר סינון. זו הנחה שאי אפשר לקיים:
 * מספיק מסלול אחד שלא סונן כדי שהכל ייפול.
 *
 * ומסלול כזה היה. פריט אפור (ערוץ שלא אושר) אמנם לא נפתח בלחיצה — אבל
 * לחיצה ארוכה, או שלוש הנקודות שבצדו, פתחו את תפריט הפעולות המלא:
 *
 *   • "הורד" — ההורדה ירדה למכשיר בפועל. הקובץ נשמר, וניתן לנגן אותו
 *     מהגלריה, מחוץ לאפליקציה ומחוץ לכל סינון.
 *   • "הבא בתור" — כשלא התנגן כלום, `enqueueNext` פשוט התחיל לנגן אותו.
 *     כלומר שלוש נקודות במקום לחיצה, ואותו סרטון בדיוק מתנגן.
 *
 * `Playback.start` עצמו מעולם לא בדק חברות ברשימה הלבנה. הוא קרא את
 * הקטגוריות רק כדי להחליט אודיו מול וידאו — כלומר כל מי שהחזיק מזהה
 * סרטון יכול היה לנגן אותו.
 *
 * ## מה השתנה
 * השער עבר מהמסכים אל שלוש הנקודות שבהן התוכן באמת נצרך — ניגון, תור
 * והורדה. מסך יכול להציג מה שהוא רוצה; הוא כבר לא זה שמחליט מה מותר.
 * מסך חדש שייכתב מחר לא יכול לפתוח פרצה, כי הוא לא עובר ליד השער.
 *
 * ## מה נשאר פתוח, ובכוונה
 * כשמטמון הערוצים ריק — התקנה ראשונה לפני הסנכרון הראשון — השער מאשר.
 * זו לא פשרה ביטחונית: במצב הזה גם אין מאיפה להגיע לתוכן, כי כל הפיד,
 * החיפוש והספרייה נבנים מאותה רשימה בדיוק. חסימה כאן הייתה רק שוברת
 * התקנה ראשונה ולא מונעת דבר.
 *
 * קבצי מדיה מהמכשיר עצמם ([Playback.LOCAL_ID_PREFIX]) אינם עוברים בשער:
 * הם לא הגיעו מיוטיוב, אין להם ערוץ, והרשימה הלבנה לא אומרת עליהם כלום.
 */
object ContentGate {

    private class Snapshot(val signature: String, val approved: ApprovedChannels)

    @Volatile
    private var cache: Snapshot? = null

    /** מזהה של קובץ מדיה מקומי — ראה [com.filtertube.app.playback.Playback.LOCAL_ID_PREFIX]. */
    private const val LOCAL_PREFIX = "local:"

    /**
     * הרשימה המאושרת לרמת הסינון ולמגדר שנבחרו כרגע.
     *
     * נבנית מחדש רק כשמשהו בהרכב השתנה — רמת סינון, מגדר או מספר הערוצים
     * במטמון. בלי המטמון הזה כל לחיצה על שיר הייתה בונה מפות ואינדקסים
     * של מאתיים ערוצים, בדיוק בשנייה שבה המשתמש מחכה שהצליל ייצא.
     */
    fun approved(context: Context): ApprovedChannels {
        val app = context.applicationContext
        val settings = SettingsStore(app)
        val all = runCatching { ChannelsRepository.getCachedChannelsFast(app) }
            .getOrDefault(emptyList())
        val signature = "${settings.filterLevel}|${settings.userGender}|${all.size}"
        cache?.takeIf { it.signature == signature }?.let { return it.approved }

        val approved = ApprovedChannels(all.forLevel(settings.filterLevel, settings.userGender))
        cache = Snapshot(signature, approved)
        return approved
    }

    /**
     * האם מותר לנגן / לתייק / להוריד את [video].
     *
     * הכישלון כאן הוא **סגור**: כל מה שאינו מזוהה כמאושר נדחה. זה ההפך
     * מהתצוגה, שמאפירה רק כשהרשימה כבר נטענה — במסך "לא יודע" פירושו
     * "אל תבהב", וכאן "לא יודע" פירושו "לא".
     */
    fun allows(context: Context, video: Video): Boolean {
        // מדיה מהמכשיר: לא יוטיוב, אין ערוץ, ואין מה לבדוק.
        if (video.id.startsWith(LOCAL_PREFIX)) return true
        val approved = approved(context)
        // מטמון ריק = התקנה לפני הסנכרון הראשון. ראה הסבר בראש הקובץ.
        if (approved.isEmpty()) return true
        return approved.approves(video)
    }

    /** אותו שער על רשימה — לתורים ולפעולות מרוכזות. */
    fun filter(context: Context, videos: List<Video>): List<Video> {
        if (videos.isEmpty()) return videos
        val approved = approved(context)
        if (approved.isEmpty()) return videos
        return videos.filter { it.id.startsWith(LOCAL_PREFIX) || approved.approves(it) }
    }

    /**
     * דחייה עם רישום ביומן.
     *
     * חסימה שקטה נראית למשתמש בדיוק כמו תקלה, ולמפתח כמו כלום. השורה
     * הזו היא ההבדל בין "לחצתי ולא קרה כלום" לבין תשובה.
     */
    fun deny(context: Context, video: Video, action: String): Boolean {
        val allowed = allows(context, video)
        if (!allowed) {
            Diagnostics.log(
                "GATE חסם $action: ${video.title.take(40)} · ערוץ ${video.channelName} " +
                    "(${video.channelId}) אינו ברשימה המאושרת",
            )
        }
        return !allowed
    }
}
