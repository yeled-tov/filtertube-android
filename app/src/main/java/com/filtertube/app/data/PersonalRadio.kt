package com.filtertube.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * "רדיו אישי" — בוחר סרטון פתיחה שמתאים לטעם של המשתמש, בלי שהוא יחפש כלום.
 *
 * ## למה זה רק בחירת זרע אחד
 * התור עצמו כבר קיים: [com.filtertube.app.playback.RadioQueueManager] בונה
 * תור אוטומטי מתמשך סביב הסרטון שמתנגן. כלומר כל מה שחסר כדי ש"לחיצה אחת
 * תפעיל רדיו" זה להחליט **במה להתחיל**, ומשם המנגנון הקיים ממשיך לבד.
 *
 * ## איך נקבע הטעם
 * שלושה מקורות, כולם כבר על המכשיר — אין כאן שום מודל ושום שירות חיצוני:
 *
 * 1. **מה שנצפה** ([LibraryStore.localHistory]) — כל צפייה מחזקת את הערוץ.
 * 2. **מה שנאהב** ([LibraryStore.likes]) — לייק הוא אות חזק יותר מצפייה.
 * 3. **מה שחופש** ([SettingsStore.getSearchHistory]) — מילות החיפוש נבדקות
 *    מול הכותרת ושם הערוץ, כי חיפוש הוא הצהרת כוונה מפורשת.
 *
 * המועמדים הם הפיד השמור של הערוצים המאושרים, כך שהסינון נשמר ממילא.
 *
 * ## למה בחירה אקראית מתוך הראש
 * לו היינו לוקחים תמיד את הציון הגבוה ביותר, אותה לחיצה הייתה מנגנת אותו
 * שיר בכל פעם. במקום זה נבחר אחד מתוך [SEED_POOL] המובילים — עדיין מתוך
 * מה שהמשתמש אוהב, אבל לא צפוי מראש.
 */
object PersonalRadio {

    /** מתוך כמה מהמובילים נבחר הזרע בפועל. */
    private const val SEED_POOL = 25

    /** ניקוד לכל צפייה בערוץ. */
    private const val WATCH_WEIGHT = 3

    /** ניקוד לכל לייק בערוץ — אות חזק יותר מצפייה. */
    private const val LIKE_WEIGHT = 7

    /** ניקוד להתאמה למילת חיפוש אחרונה. */
    private const val SEARCH_WEIGHT = 12

    /** כמה חיפושים אחרונים נלקחים בחשבון. */
    private const val SEARCH_DEPTH = 10

    /**
     * מחזיר סרטון פתיחה לרדיו, או null אם אין מה לנגן (פיד ריק).
     *
     * לא זורק אף פעם: כישלון בקריאת ההעדפות פירושו רדיו פחות מותאם, לא רדיו
     * שבור. במקרה כזה נבחר סרטון טרי מהפיד.
     */
    suspend fun pickSeed(context: Context): Video? = withContext(Dispatchers.Default) {
        val feed = runCatching { FeedCache.loadFeed(context) }.getOrNull().orEmpty()
            .filter { it.id.isNotBlank() && !it.isShort }
        if (feed.isEmpty()) return@withContext null

        val store = LibraryStore(context)
        val settings = SettingsStore(context)

        val history = runCatching { store.localHistory() }.getOrNull().orEmpty()
        val likes = runCatching { store.likes() }.getOrNull().orEmpty()
        val searches = runCatching { settings.getSearchHistory() }.getOrNull().orEmpty()
            .take(SEARCH_DEPTH)
            .map { normalize(it) }
            .filter { it.length >= 2 }

        val affinity = HashMap<String, Int>()
        history.forEach { affinity.merge(it.channelId, WATCH_WEIGHT, Int::plus) }
        likes.forEach { affinity.merge(it.channelId, LIKE_WEIGHT, Int::plus) }

        // כבר נצפה — לא נתחיל ממנו רדיו. הסרטונים האלה עדיין יכולים להופיע
        // בהמשך התור; זו רק בחירת נקודת ההתחלה.
        val watched = history.mapTo(HashSet()) { it.id }

        val scored = feed.asSequence()
            .filter { it.id !in watched }
            .map { video ->
                var score = affinity[video.channelId] ?: 0
                if (searches.isNotEmpty()) {
                    val haystack = normalize("${video.title} ${video.channelName}")
                    if (searches.any { haystack.contains(it) }) score += SEARCH_WEIGHT
                }
                video to score
            }
            .sortedWith(
                compareByDescending<Pair<Video, Int>> { it.second }
                    .thenByDescending { it.first.publishedAt },
            )
            .take(SEED_POOL)
            .map { it.first }
            .toList()

        // הפיד כולו נצפה כבר — נופלים חזרה לטרי ביותר במקום להחזיר כלום.
        val pool = scored.ifEmpty { feed.sortedByDescending { it.publishedAt }.take(SEED_POOL) }
        if (pool.isEmpty()) return@withContext null

        val seed = pool[Random.nextInt(pool.size)]
        Diagnostics.log(
            "RADIO אישי: זרע ${seed.id} (${seed.channelName}) " +
                "מתוך ${pool.size} מועמדים · ${history.size} צפיות, ${likes.size} לייקים, " +
                "${searches.size} חיפושים",
        )
        seed
    }

    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")

    private fun normalize(text: String): String =
        text.lowercase().replace(NON_ALNUM, " ").trim()
}
