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
 * ## הגשר
 * כשהמזהה אינו ברשימה, ההשוואה נופלת לשם. שוויון מדויק לבדו לא הספיק:
 * ברשימה הלבנה רשום "ששון איפרם שאולוב" ואילו יוטיוב מיוזיק מציגה "ששון
 * שאולוב", ולכן מותרת גם הכלה של מילים לשני הכיוונים.
 *
 * ## שלוש המגבלות שמונעות מזה להיות פרצה
 * 1. **התאמה יחידה בלבד.** אם שני ערוצים מאושרים יכולים להתאים, אין דרך
 *    לדעת איזה — ואין אישור. ניחוש ברשימה לבנה הוא בדיוק מה שאסור.
 * 2. **לפחות שתי מילות זיהוי**, אחרי סינון מילים ריקות מתוכן ("הרשמי",
 *    "ערוץ"). שם בן מילה אחת לא עובר את הגשר בכלל.
 * 3. **רק בספרייה.** הפיד, החיפוש והנגן ממשיכים להשוות מזהה למזהה בלבד.
 *    הגשר חל על פריטים שהמשתמש עצמו סימן בחשבון שלו — לא על תוכן חדש
 *    שנכנס לאפליקציה.
 *
 * הסיכון שנותר הוא ערוץ שאינו מאושר ושמו מכיל בדיוק את מילות השם של ערוץ
 * מאושר. זה מחיר מודע: בלעדיו שירים של אמנים מאושרים פשוט אינם מנוגנים.
 */
class ApprovedChannels(channels: List<Channel>) {

    private val ids: Set<String> = channels.mapTo(HashSet()) { it.youtubeChannelId }

    private val all: List<Channel> = channels

    /** שם מנורמל → הערוצים המאושרים שנושאים אותו. */
    private val byName: Map<String, List<Channel>> = channels.groupBy { normalize(it.name) }

    /** מילות השם של כל ערוץ מאושר, לגישור על שמות שאינם זהים בדיוק. */
    private val tokensOf: List<Pair<Channel, Set<String>>> =
        channels.map { it to tokens(it.name) }

    /** ריק = הרשימה עוד לא נטענה. אז לא מאפירים כלום, כדי לא להבהב. */
    fun isEmpty(): Boolean = ids.isEmpty()

    fun approves(channelId: String, channelName: String): Boolean =
        channelFor(channelId, channelName) != null

    fun approves(video: Video): Boolean = approves(video.channelId, video.channelName)

    /** הערוץ המאושר שמאחורי הסרטון, או null. */
    fun channelFor(channelId: String, channelName: String): Channel? {
        if (channelId.isBlank()) return null
        all.firstOrNull { it.youtubeChannelId == channelId }?.let { return it }

        // ── הגשר ─────────────────────────────────────────────────────────
        // המזהה אינו ברשימה. זה קורה כשהמעלה הוא ערוץ ה-Topic האוטומטי של
        // אמן מאושר, ואז הדרך היחידה לזהות אותו היא השם.
        val artist = topicArtist(channelName) ?: channelName
        if (artist.isBlank()) return null

        byName[normalize(artist)]?.singleOrNull()?.let { return it }

        // שמות לא תמיד זהים בין המקורות: ברשימה הלבנה רשום "ששון איפרם
        // שאולוב" ואילו יוטיוב מיוזיק מציגה "ששון שאולוב". התאמה מדויקת
        // בלבד מפספסת בדיוק את המקרים האלה, ולכן מותרת גם הכלה של מילים.
        val want = tokens(artist)
        if (want.size < MIN_TOKENS) return null
        val matches = tokensOf.filter { (_, have) ->
            have.size >= MIN_TOKENS && (have.containsAll(want) || want.containsAll(have))
        }
        // יחיד ותו לא: אם שני ערוצים מאושרים יכולים להתאים, אין דרך לדעת
        // איזה מהם — ו"ניחוש" ברשימה לבנה הוא בדיוק מה שאסור.
        return matches.singleOrNull()?.first
    }

    fun channelFor(video: Video): Channel? = channelFor(video.channelId, video.channelName)

    /** הערוצים הלא-מאושרים שבין הפריטים, עם מונה — לחלון האבחון. */
    fun unapproved(videos: List<Video>): List<Triple<String, String, Int>> =
        videos.filter { !approves(it) }
            .groupBy { it.channelId to it.channelName }
            .map { (key, items) -> Triple(key.second, key.first, items.size) }
            .sortedByDescending { it.third }

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

        /**
         * מילות השם, בלי מילים ריקות מתוכן.
         *
         * "הרשמי", "official" וכו' מופיעות בשמות ערוצים ואינן מזהות אף אחד;
         * בלי סינונן "הרב א' הרשמי" ו"הרב ב' הרשמי" היו נראים דומים.
         */
        private val STOPWORDS = setOf(
            "הרשמי", "הערוץ", "ערוץ", "official", "channel", "music", "hd", "tv",
        )

        private const val MIN_TOKENS = 2

        fun tokens(name: String): Set<String> =
            normalize(name).split(' ')
                .map { it.trim('"', '\'', '-', '–', '—', '.', ',', '(', ')') }
                .filter { it.length > 1 && it !in STOPWORDS }
                .toSet()
    }
}
