package com.filtertube.app.data

import kotlin.math.pow

/**
 * רשימת השמעה שנבנתה אוטומטית מהטעם של המשתמש.
 *
 * @param seedChannel הערוץ שסביבו נבנה המיקס, אם יש — לצורך תמונת הכריכה.
 */
data class Mix(
    val id: String,
    val title: String,
    val subtitle: String,
    val songs: List<Video>,
    val seedChannel: String? = null,
)

/**
 * בניית המיקסים.
 *
 * ## איך זה עובד אצל ספוטיפיי, ומה מזה אפשר כאן
 * "Daily Mix" של ספוטיפיי בנוי משני דברים: אשכולות של אמנים שנשמעים יחד
 * אצל אותו מאזין, ודגימה מתוך כל אשכול. את האשכולות הם לומדים ממיליוני
 * משתמשים — מידע שאין לנו, ובכוונה: כאן שום דבר לא עוזב את המכשיר.
 *
 * מה שכן יש הוא ההיסטוריה של המשתמש עצמו, הלייקים שלו והקטגוריות של
 * הרשימה הלבנה. משלושת אלה אפשר לבנות בדיוק את אותם סוגי מיקסים —
 * "האמן שלך", "הסגנון שלך", "עוד כמו מה ששמעת" — בלי שרת ובלי פרופיל
 * שנשמר אצל מישהו.
 *
 * ## למה מיקס חייב מינימום שירים
 * רשימת השמעה בת שלושה שירים היא לא רשימה, היא תזכורת. מיקס שלא מגיע
 * ל-[MIN_SONGS] פשוט לא נוצר — עדיף פחות מיקסים טובים מהרבה ריקים.
 */
object MixBuilder {

    private const val MIN_SONGS = 6
    private const val MAX_SONGS = 40
    private const val MAX_ARTIST_MIXES = 4

    /** כמה אמנים מובילים נחשבים "הטעם" לצורך מיקס יומי. */
    private const val CORE_ARTISTS = 6

    fun build(
        pool: List<Video>,
        likes: List<Video>,
        history: List<Video>,
        profile: TasteProfile,
        categoryOf: (String) -> String?,
        channelName: (String) -> String?,
        now: Long = System.currentTimeMillis(),
    ): List<Mix> {
        val all = (pool + likes + history).distinctBy { it.id }.filter { it.id.isNotBlank() }
        if (all.isEmpty()) return emptyList()

        val mixes = ArrayList<Mix>()
        val heard = (likes + history).mapTo(HashSet()) { it.id }

        // ── 1. המיקס היומי ────────────────────────────────────────────────
        // מהאמנים המובילים של המשתמש, מעורבב. זה המקבילה ל-Daily Mix:
        // לא הכי חדש ולא הכי אהוב, אלא חתך מייצג של הטעם.
        val core = profile.channelAffinity.entries
            .sortedByDescending { it.value }
            .take(CORE_ARTISTS)
            .map { it.key }
            .toSet()
        if (core.isNotEmpty()) {
            val daily = all.filter { it.channelId in core }
                .shuffledStable(now / 86_400_000)   // מתחלף פעם ביום
                .take(MAX_SONGS)
            if (daily.size >= MIN_SONGS) {
                mixes += Mix(
                    id = "daily",
                    title = "המיקס היומי שלך",
                    subtitle = "מהאמנים שאתה הכי שומע",
                    songs = daily,
                    seedChannel = core.firstOrNull(),
                )
            }
        }

        // ── 2. מיקס לכל אמן מוביל ─────────────────────────────────────────
        profile.channelAffinity.entries
            .sortedByDescending { it.value }
            .take(MAX_ARTIST_MIXES)
            .forEach { (channelId, _) ->
                val name = channelName(channelId) ?: return@forEach
                val songs = all.filter { it.channelId == channelId }
                    .shuffledStable(channelId.hashCode().toLong())
                    .take(MAX_SONGS)
                if (songs.size >= MIN_SONGS) {
                    mixes += Mix(
                        id = "artist_$channelId",
                        title = "מיקס $name",
                        subtitle = "${songs.size} שירים",
                        songs = songs,
                        seedChannel = channelId,
                    )
                }
            }

        // ── 3. מיקס לפי סגנון ─────────────────────────────────────────────
        profile.categoryAffinity.entries
            .sortedByDescending { it.value }
            .take(3)
            .forEach { (category, _) ->
                val label = categoryLabels[category] ?: return@forEach
                val songs = all.filter { categoryOf(it.channelId) == category }
                    .shuffledStable(category.hashCode().toLong())
                    .take(MAX_SONGS)
                if (songs.size >= MIN_SONGS) {
                    mixes += Mix(
                        id = "cat_$category",
                        title = label,
                        subtitle = "הסגנון שאתה הכי שומע",
                        songs = songs,
                        seedChannel = songs.firstOrNull()?.channelId,
                    )
                }
            }

        // ── 4. גילויים ────────────────────────────────────────────────────
        // מה שעוד לא שמעת, מקטגוריות שכן אהובות עליך. בלי הקטע הזה מיקס
        // אישי הופך למעגל סגור שמחזיר תמיד את אותם שירים.
        val discovery = all.asSequence()
            .filter { it.id !in heard }
            .filter { (profile.categoryAffinity[categoryOf(it.channelId)] ?: 0.0) > 0.15 }
            .sortedByDescending { it.publishedAt }
            .take(MAX_SONGS)
            .toList()
        if (discovery.size >= MIN_SONGS) {
            mixes += Mix(
                id = "discovery",
                title = "גילויים",
                subtitle = "לא שמעת עדיין",
                songs = discovery,
                seedChannel = discovery.firstOrNull()?.channelId,
            )
        }

        // ── 5. שוב ושוב ───────────────────────────────────────────────────
        val repeats = likes.filter { it.id in history.mapTo(HashSet()) { h -> h.id } }
            .take(MAX_SONGS)
        if (repeats.size >= MIN_SONGS) {
            mixes += Mix(
                id = "repeat",
                title = "שוב ושוב",
                subtitle = "אהבת גם שמעת",
                songs = repeats,
                seedChannel = repeats.firstOrNull()?.channelId,
            )
        }

        return mixes
    }

    /**
     * ערבוב יציב לפי זרע.
     *
     * shuffle() רגיל מחזיר סדר אחר בכל composition, והמסך היה קופץ בכל
     * גלילה. זרע קבוע נותן סדר "מעורבב" שנשאר יציב — ומתחלף רק כשהזרע
     * מתחלף, למשל פעם ביום במיקס היומי.
     */
    private fun <T> List<T>.shuffledStable(seed: Long): List<T> =
        shuffled(java.util.Random(seed))
}
