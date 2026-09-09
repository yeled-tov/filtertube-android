package com.filtertube.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.pow
import kotlin.random.Random

/**
 * "רדיו אישי" — תחנה שנבנית ממה שהמשתמש באמת מקשיב לו.
 *
 * ## למה הגרסה הקודמת לא עבדה
 * היא בחרה **סרטון פתיחה אחד** מהפיד, ומשם RadioQueueManager בנה את כל שאר
 * התור לפי "סרטונים קשורים" של יוטיוב. כלומר שיר אחד היה מותאם, וכל השאר
 * הגיע ממנוע ההמלצות של יוטיוב — בלי שום קשר ללייקים, להיסטוריה או לחיפושים.
 * גרוע מזה: כל מה שכבר נצפה נפסל כנקודת פתיחה, ולכן דווקא השירים האהובים
 * ביותר — אלה שהמשתמש חוזר אליהם — היו הראשונים לצאת מהמשחק.
 *
 * ## מה עכשיו
 * [buildStation] מחזיר **תחנה שלמה** — רשימה מסודרת שמנוגנת כמו שהיא. שלוש
 * שכבות, בסדר הזה:
 *
 * 1. **מה שהוא אהב** — הסרטונים שסימן ב"אהבתי". זו ההצהרה המפורשת ביותר,
 *    והיא פותחת את התחנה.
 * 2. **מה שהוא באמת שמע** — היסטוריית הצפייה, בדילוג על מה שהתנגן ממש עכשיו
 *    כדי שלא יחזור מיד.
 * 3. **מה שמתאים לסגנון** — הפיד המאושר, מדורג לפי פרופיל הטעם שנבנה משתי
 *    השכבות הראשונות: אילו ערוצים, אילו קטגוריות, ואילו מילים חוזרות
 *    בכותרות ובחיפושים.
 *
 * השכבות משתלבות זו בזו ([interleave]) ולא מוגשות בגושים, כדי שהתחנה תישמע
 * כמו רדיו ולא כמו שלוש רשימות שהודבקו.
 *
 * הכל מקומי: אין כאן מודל, אין שירות חיצוני, ואין בקשת רשת. הסינון נשמר
 * ממילא — כל המועמדים מגיעים מהערוצים המאושרים.
 */
object PersonalRadio {

    /** אורך התחנה שנבנית מראש. כשהיא נגמרת, מנגנון הרדיו הרגיל ממשיך. */
    private const val STATION_SIZE = 24

    /** משקל ערוץ לכל סרטון שסומן ב"אהבתי" — האות החזק ביותר. */
    private const val LIKE_WEIGHT = 10.0

    /** משקל ערוץ לכל סרטון בהיסטוריה, לפני דעיכה לפי זמן. */
    private const val WATCH_WEIGHT = 4.0

    /** אחרי כמה ימים משקל הצפייה נחתך בחצי. */
    private const val HALF_LIFE_DAYS = 21.0

    /** משקל התאמת קטגוריה. */
    private const val CATEGORY_WEIGHT = 6.0

    /** משקל התאמת מילה מהכותרות האהובות או מהחיפושים. */
    private const val KEYWORD_WEIGHT = 5.0

    /** כמה מילות מפתח נשמרות בפרופיל. */
    private const val KEYWORD_DEPTH = 24

    /** כמה סרטונים אחרונים בהיסטוריה נחשבים "בדיוק שמעתי" ולא חוזרים מיד. */
    private const val COOLDOWN_RECENT = 12

    /**
     * תחנה מוכנה לניגון, או רשימה ריקה אם אין ממה לבנות.
     *
     * לא זורק לעולם: כישלון בקריאת ההעדפות פירושו תחנה פחות מותאמת, לא תחנה
     * שבורה.
     */
    suspend fun buildStation(context: Context): List<Video> = withContext(Dispatchers.Default) {
        val store = LibraryStore(context)
        val settings = SettingsStore(context)

        val likes = runCatching { store.likes() }.getOrNull().orEmpty()
            .filter { it.id.isNotBlank() }
        val history = runCatching { store.localHistory() }.getOrNull().orEmpty()
            .filter { it.id.isNotBlank() }
        val searches = runCatching { settings.getSearchHistory() }.getOrNull().orEmpty()
        val feed = runCatching { FeedCache.loadFeed(context) }.getOrNull().orEmpty()
            .filter { it.id.isNotBlank() && !it.isShort }

        // הקטגוריה היא תכונה של הערוץ, לא של הסרטון — היא מגיעה מהרשימה
        // המאושרת. בלי המפה הזו "סגנון" היה מתנוון לשם הערוץ בלבד.
        val catById = runCatching {
            ChannelsRepository.getCachedChannelsFast(context)
                .associate { it.youtubeChannelId to it.category }
        }.getOrNull().orEmpty()

        val profile = buildProfile(likes, history, searches, catById)

        // מה שהתנגן ממש עכשיו לא חוזר מיד — אבל שאר ההיסטוריה כן, כי בדיוק
        // שם נמצאים השירים שהמשתמש חוזר אליהם.
        val cooldown = history.take(COOLDOWN_RECENT).mapTo(HashSet()) { it.id }

        val tierLoved = likes.filter { it.id !in cooldown }.shuffled()
        val tierHeard = history.drop(COOLDOWN_RECENT)
            .sortedByDescending { score(it, profile, catById) }
            .take(STATION_SIZE)
        val chosen = HashSet<String>()
        tierLoved.forEach { chosen += it.id }
        tierHeard.forEach { chosen += it.id }
        val tierMatch = feed.asSequence()
            .filter { it.id !in chosen && it.id !in cooldown }
            .map { it to score(it, profile, catById) }
            .sortedByDescending { it.second }
            .take(STATION_SIZE)
            .map { it.first }
            .toList()

        val station = interleave(tierLoved, tierHeard, tierMatch)
            .distinctBy { it.id }
            .take(STATION_SIZE)

        Diagnostics.log(
            "RADIO אישי: תחנה של ${station.size} — " +
                "${tierLoved.size} אהובים, ${tierHeard.size} מההיסטוריה, ${tierMatch.size} לפי סגנון · " +
                "פרופיל: ${profile.channels.size} ערוצים, ${profile.categories.size} קטגוריות, " +
                "${profile.keywords.size} מילות מפתח",
        )
        if (station.isEmpty()) {
            Diagnostics.log("RADIO אישי: אין ממה לבנות — ${likes.size} לייקים, ${history.size} היסטוריה, ${feed.size} בפיד")
        }
        station
    }

    // ── פרופיל הטעם ───────────────────────────────────────────────────────
    private data class Profile(
        val channels: Map<String, Double>,
        val categories: Map<String, Double>,
        val keywords: Set<String>,
    )

    private fun buildProfile(
        likes: List<Video>,
        history: List<Video>,
        searches: List<String>,
        catById: Map<String, String>,
    ): Profile {
        val now = System.currentTimeMillis()
        val channels = HashMap<String, Double>()

        likes.forEach { channels.merge(it.channelId, LIKE_WEIGHT, Double::plus) }

        // צפייה מלפני חודשיים אומרת פחות מצפייה מאתמול. הדעיכה מעריכית עם
        // זמן מחצית חיים קבוע, כך ש"מה שאני שומע עכשיו" גובר על "מה ששמעתי פעם".
        history.forEach { video ->
            val ageDays = ((now - video.watchedAt).coerceAtLeast(0L)) / 86_400_000.0
            val decay = 0.5.pow(ageDays / HALF_LIFE_DAYS)
            channels.merge(video.channelId, WATCH_WEIGHT * decay, Double::plus)
        }

        // קטגוריה נגזרת מהערוץ, ולכן נבנית מאותם משקלים.
        val categories = HashMap<String, Double>()
        channels.forEach { (channelId, weight) ->
            val category = catById[channelId] ?: return@forEach
            categories.merge(category, weight, Double::plus)
        }

        // מילים חוזרות בכותרות שנאהבו ובחיפושים — הן שנותנות את "הסגנון"
        // ברזולוציה שערוץ שלם לא נותן (זמר אחד, שיר אחד, סגנון אחד).
        val keywords = LinkedHashSet<String>()
        searches.forEach { q -> keywords += tokens(q) }
        likes.take(30).forEach { keywords += tokens(it.title) }
        history.take(30).forEach { keywords += tokens(it.title) }

        return Profile(channels, categories, keywords.take(KEYWORD_DEPTH).toSet())
    }

    private fun score(video: Video, profile: Profile, catById: Map<String, String>): Double {
        var score = profile.channels[video.channelId] ?: 0.0
        catById[video.channelId]?.let { category ->
            if (profile.categories.containsKey(category)) score += CATEGORY_WEIGHT
        }
        if (tokens(video.title).any { it in profile.keywords }) score += KEYWORD_WEIGHT
        return score
    }

    /**
     * משלב את השכבות במקום להדביק אותן.
     *
     * תחנה שמנגנת קודם את כל הלייקים ואז את כל ההיסטוריה נשמעת כמו שתי
     * רשימות, לא כמו רדיו. השילוב לוקח לסירוגין מכל שכבה שעוד נשארה בה
     * משהו, ומתחיל תמיד מהשכבה הראשונה — כלומר משיר אהוב.
     */
    private fun interleave(vararg tiers: List<Video>): List<Video> {
        val cursors = IntArray(tiers.size)
        val out = ArrayList<Video>()
        var moved = true
        while (moved) {
            moved = false
            tiers.forEachIndexed { i, tier ->
                if (cursors[i] < tier.size) {
                    out += tier[cursors[i]]
                    cursors[i]++
                    moved = true
                }
            }
        }
        return out
    }

    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")

    /** מילים באורך 3+ מתוך טקסט מנורמל — בלי מילות קישור קצרות שמתאימות לכל דבר. */
    private fun tokens(text: String): List<String> =
        text.lowercase().split(NON_ALNUM).filter { it.length >= 3 }

    /** נשמר לתאימות עם קוראים ישנים: הסרטון הראשון בתחנה. */
    suspend fun pickSeed(context: Context): Video? = buildStation(context).firstOrNull()
}
