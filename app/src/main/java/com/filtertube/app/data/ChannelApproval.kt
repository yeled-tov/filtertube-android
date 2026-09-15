package com.filtertube.app.data

/**
 * מי מאושר — התשובה במקום אחד.
 *
 * ## הבעיה שזה פותר
 * ערוץ של אמן ביוטיוב מיוזיק אינו הערוץ שהעלה את השיר. כשאמן מוסר מוזיקה
 * דרך חברת תקליטים, יוטיוב מייצרת לו ערוץ אוטומטי בשם "<האמן> - Topic",
 * והשירים מועלים משם. זה ערוץ אמיתי עם מזהה משלו — ולכן "ששון איפרם
 * שאולוב" יכול להיות מאושר ברשימה הלבנה, בעוד שהשיר שלו הועלה ע"י ערוץ
 * שמזהה אחר לגמרי, והוא הוצג אפור.
 *
 * ownerChannel כבר פתר חצי מהבעיה: הוא מחליף את *האמן* (שאינו מעלה) במי
 * שבאמת העלה. אבל כשמי שבאמת העלה הוא ערוץ ה-Topic, ההשוואה עדיין נכשלת,
 * כי ערוץ ה-Topic אינו ברשימה הלבנה ולעולם לא יהיה — אף אחד לא מוסיף
 * ידנית ערוצים אוטומטיים שאינם מופיעים בחיפוש.
 *
 * ## הגשר, ולמה הוא צר בכוונה
 * ערוץ Topic מקבל אישור **רק** אם השם שלפני הסיומת זהה בדיוק לשם של ערוץ
 * מאושר. לא הכלה, לא דמיון, לא התחלה-של — שוויון מלא אחרי ניקוי רווחים
 * וניקוד.
 *
 * הגשר מוגבל לערוצי Topic דווקא ולא לכל השוואת שמות, כי "X - Topic" הוא
 * מבנה שיוטיוב יוצרת מהתוכן של X עצמו. התרת השוואת שמות כללית הייתה
 * אומרת שכל ערוץ ששמו כשם ערוץ מאושר עובר — וזו כבר פרצה ברשימה הלבנה,
 * לא תיקון באג.
 */
class ApprovedChannels(channels: List<Channel>) {

    private val ids: Set<String> = channels.mapTo(HashSet()) { it.youtubeChannelId }

    /** שם מנורמל → הערוץ המאושר, לגישור ערוצי Topic. */
    private val byName: Map<String, Channel> = channels.associateBy { normalize(it.name) }

    /** ריק = הרשימה עוד לא נטענה. אז לא מאפירים כלום, כדי לא להבהב. */
    fun isEmpty(): Boolean = ids.isEmpty()

    fun approves(channelId: String, channelName: String): Boolean {
        if (channelId.isBlank()) return false
        if (channelId in ids) return true
        val artist = topicArtist(channelName) ?: return false
        return normalize(artist) in byName
    }

    fun approves(video: Video): Boolean = approves(video.channelId, video.channelName)

    /** הערוץ המאושר שמאחורי הסרטון — לרבות דרך גשר ה-Topic. null = לא מאושר. */
    fun channelFor(video: Video): Channel? {
        if (video.channelId.isBlank()) return null
        byName.values.firstOrNull { it.youtubeChannelId == video.channelId }?.let { return it }
        val artist = topicArtist(video.channelName) ?: return null
        return byName[normalize(artist)]
    }

    companion object {
        /**
         * סיומת ערוץ אוטומטי. שלושת סוגי המקף נתמכים כי יוטיוב מחזירה מקף
         * רגיל באנגלית ומקף ארוך בחלק מהשפות, ו"נושא" הוא התרגום העברי.
         */
        private val TOPIC_SUFFIX = Regex("""\s*[-–—]\s*(Topic|נושא)\s*$""", RegexOption.IGNORE_CASE)

        /** ניקוד עברי — לא נראה על המסך אבל שובר השוואת מחרוזות. */
        private val NIKUD = Regex("""[֑-ׇ]""")
        private val SPACES = Regex("""\s+""")

        /** שם האמן שמאחורי ערוץ Topic, או null אם זה אינו ערוץ Topic. */
        fun topicArtist(channelName: String): String? {
            val match = TOPIC_SUFFIX.find(channelName) ?: return null
            val artist = channelName.substring(0, match.range.first).trim()
            return artist.ifBlank { null }
        }

        fun normalize(name: String): String =
            name.replace(NIKUD, "").replace(SPACES, " ").trim().lowercase()
    }
}
