package com.filtertube.app.data

import kotlin.math.abs
import kotlin.math.pow

/**
 * פרופיל הטעם של המשתמש — נבנה ממה שהוא עשה, לא ממה שהוא הצהיר.
 *
 * כל המקורות מקומיים, ולכן הדירוג עובד גם בלי רשת ובלי חשבון.
 */
data class TasteProfile(
    /** מזהה ערוץ → 0..1. כמה המשתמש אוהב את הערוץ הזה. */
    val channelAffinity: Map<String, Double>,
    /** קטגוריה → 0..1. מכליל מערוץ שנצפה לערוצים דומים לו. */
    val categoryAffinity: Map<String, Double>,
    /** מילים מחיפושים אחרונים — כוונה מפורשת, לא רק התנהגות. */
    val terms: List<String>,
) {
    val isEmpty: Boolean get() = channelAffinity.isEmpty() && terms.isEmpty()

    companion object {
        /** לייק הוא הצהרה מפורשת, ולכן שווה יותר מצפייה. */
        private const val LIKE_WEIGHT = 3.0
        private const val SUBSCRIBE_WEIGHT = 2.0
        private const val WATCH_WEIGHT = 1.0

        /** אחרי כמה ימים צפייה שווה חצי ממשקלה. */
        private const val WATCH_HALF_LIFE_DAYS = 21.0

        fun build(
            likes: List<Video>,
            history: List<Video>,
            subscriptions: List<String>,
            searchTerms: List<String>,
            categoryOf: (String) -> String?,
            now: Long = System.currentTimeMillis(),
        ): TasteProfile {
            val raw = HashMap<String, Double>()
            fun add(channelId: String, weight: Double) {
                if (channelId.isNotBlank()) raw[channelId] = (raw[channelId] ?: 0.0) + weight
            }

            likes.forEach { add(it.channelId, LIKE_WEIGHT) }
            subscriptions.forEach { add(it, SUBSCRIBE_WEIGHT) }

            // ── דעיכה לפי זמן ─────────────────────────────────────────────
            // מה שנצפה אתמול מלמד על הטעם של היום יותר ממה שנצפה לפני חצי
            // שנה. בלי הדעיכה, פרופיל של משתמש ותיק קופא על העבר שלו ולא
            // מתעדכן לעולם.
            history.forEach { video ->
                val ageDays = ((now - video.watchedAt).coerceAtLeast(0L)) / 86_400_000.0
                val decay = 0.5.pow(ageDays / WATCH_HALF_LIFE_DAYS)
                add(video.channelId, WATCH_WEIGHT * decay)
            }

            val max = raw.values.maxOrNull() ?: 0.0
            val channels = if (max <= 0.0) emptyMap() else raw.mapValues { it.value / max }

            // הכללה לקטגוריה: מי שצופה בשלושה ערוצי מוזיקה סביר שירצה גם
            // ערוץ מוזיקה רביעי שלא ראה מעולם.
            val catRaw = HashMap<String, Double>()
            channels.forEach { (id, score) ->
                val cat = categoryOf(id) ?: return@forEach
                catRaw[cat] = (catRaw[cat] ?: 0.0) + score
            }
            val catMax = catRaw.values.maxOrNull() ?: 0.0
            val categories = if (catMax <= 0.0) emptyMap() else catRaw.mapValues { it.value / catMax }

            val terms = searchTerms.asSequence()
                .flatMap { it.trim().lowercase().split(' ').asSequence() }
                .filter { it.length > 2 }
                .distinct()
                .take(20)
                .toList()

            return TasteProfile(channels, categories, terms)
        }
    }
}

/**
 * דירוג מסך הבית.
 *
 * ## הבעיה שזה פותר
 * הדירוג הקודם היה `publishedAt + אהדה × יומיים`. ערוץ שמעלה עשרים סרטונים
 * ביום תופס בו את כל הראש פשוט כי כל אחד מהם טרי — בלי שום קשר לשאלה אם
 * המשתמש אוהב אותו. זה מה שהפך את מסך הבית לערוץ אחד.
 *
 * ## שלושת השינויים
 * 1. **ציון ולא זמן.** טריות היא רכיב אחד מתוך כמה, ולא ציר הדירוג עצמו.
 * 2. **דעיכה, לא סף.** סרטון בן יומיים טוב מסרטון בן שבוע, אבל לא פי אלף —
 *    ולכן ההפרש בין שניהם לא יכול לקבור אהדה אמיתית.
 * 3. **גיוון כפוי.** אחרי הציון מגיע מעבר שני שמטיל קנס הולך וגדל על כל
 *    סרטון נוסף מאותו ערוץ. זה מה שבאמת שובר את ההשתלטות: גם הערוץ האהוב
 *    ביותר לא יכול לתפוס יותר מכמה מקומות ברצף.
 */
object FeedRanker {

    private const val W_AFFINITY = 0.45
    private const val W_RECENCY = 0.33
    private const val W_CATEGORY = 0.12
    private const val W_TERMS = 0.10

    /** אחרי כמה ימים סרטון שווה חצי מהציון הטרי שלו. */
    private const val RECENCY_HALF_LIFE_DAYS = 6.0

    /** כמה נשאר מהציון בכל סרטון נוסף מאותו ערוץ. */
    private const val CHANNEL_DECAY = 0.45

    /** סרטון שכבר נצפה יורד — אבל לא נעלם, כי לפעמים רוצים לחזור אליו. */
    private const val WATCHED_FACTOR = 0.35

    fun rank(
        videos: List<Video>,
        profile: TasteProfile,
        categoryOf: (String) -> String?,
        watchedIds: Set<String> = emptySet(),
        now: Long = System.currentTimeMillis(),
    ): List<Video> {
        if (videos.isEmpty()) return videos
        // בלי פרופיל אין מה להתאים, אבל גיוון עדיין נדרש — וזה בדיוק המצב
        // של משתמש חדש, שאצלו הערוץ הפורה ביותר משתלט הכי חזק.
        val scored = videos.map { it to score(it, profile, categoryOf, watchedIds, now) }
            .sortedByDescending { it.second }
        return diversify(scored)
    }

    private fun score(
        video: Video,
        profile: TasteProfile,
        categoryOf: (String) -> String?,
        watchedIds: Set<String>,
        now: Long,
    ): Double {
        val ageDays = if (video.publishedAt <= 0L) 30.0
        else ((now - video.publishedAt).coerceAtLeast(0L)) / 86_400_000.0
        val recency = 0.5.pow(ageDays / RECENCY_HALF_LIFE_DAYS)

        val affinity = profile.channelAffinity[video.channelId] ?: 0.0
        val category = categoryOf(video.channelId)?.let { profile.categoryAffinity[it] } ?: 0.0

        // התאמת מילים מחיפושים אחרונים — כוונה מפורשת, ולכן שווה משלה.
        val haystack = "${video.title} ${video.channelName}".lowercase()
        val termHit = if (profile.terms.any { haystack.contains(it) }) 1.0 else 0.0

        var total = W_AFFINITY * affinity +
            W_RECENCY * recency +
            W_CATEGORY * category +
            W_TERMS * termHit

        if (video.id in watchedIds) total *= WATCHED_FACTOR

        // רעש קטן ויציב לפי המזהה: בלעדיו שני סרטונים עם אותו ציון בדיוק
        // מופיעים תמיד באותו סדר, והפיד נראה קפוא בין רענונים.
        total += (abs(video.id.hashCode()) % 1000) / 1000.0 * 0.02
        return total
    }

    /**
     * מעבר הגיוון.
     *
     * בחירה חמדנית: בכל צעד נבחר הסרטון בעל הציון הגבוה ביותר *אחרי* קנס
     * שגדל מעריכית לפי כמה סרטונים מאותו ערוץ כבר נבחרו. ערוץ אהוב עדיין
     * מקבל את המקום הראשון — אבל לא גם את השני, השלישי והרביעי.
     */
    private fun diversify(scored: List<Pair<Video, Double>>): List<Video> {
        val remaining = scored.toMutableList()
        val taken = HashMap<String, Int>()
        val out = ArrayList<Video>(scored.size)

        while (remaining.isNotEmpty()) {
            var bestIndex = 0
            var bestValue = Double.NEGATIVE_INFINITY
            // חלון ולא כל הרשימה: הסריקה כבר ממוינת, ומעבר לחלון אין סיכוי
            // מעשי שקנס יהפוך את הסדר. זה מה שמשאיר את זה ליניארי בפועל.
            val window = minOf(remaining.size, 80)
            for (i in 0 until window) {
                val (video, base) = remaining[i]
                val penalty = CHANNEL_DECAY.pow(taken[video.channelId] ?: 0)
                val value = base * penalty
                if (value > bestValue) {
                    bestValue = value
                    bestIndex = i
                }
            }
            val (picked, _) = remaining.removeAt(bestIndex)
            taken[picked.channelId] = (taken[picked.channelId] ?: 0) + 1
            out.add(picked)
        }
        return out
    }
}
