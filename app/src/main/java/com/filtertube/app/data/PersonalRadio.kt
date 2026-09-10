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
     * משקל זמר שהמשתמש בחר במפורש.
     *
     * גבוה מלייק: לייק הוא תגובה לשיר בודד, בחירת זמר היא הצהרה על טעם.
     */
    private const val ARTIST_WEIGHT = 14.0

    /** כמה שירים מאותו ערוץ מותר בתחנה אחת — כדי שרדיו לא יהפוך לאלבום. */
    private const val MAX_PER_CHANNEL = 3

    /** נוכחות בגרף ה-related של יוטיוב — האות החזק ביותר לדמיון סגנוני. */
    private const val RELATED_WEIGHT = 18.0

    /** אותו זמר: רלוונטי, אבל מכוון לא דומיננטי — רדיו הוא לא אלבום. */
    private const val SAME_ARTIST_WEIGHT = 7.0

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

        val favorites = runCatching { settings.favoriteArtists }.getOrNull().orEmpty()
        val profile = buildProfile(likes, history, searches, catById, favorites)

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
        favoriteArtists: Set<String> = emptySet(),
    ): Profile {
        val now = System.currentTimeMillis()
        val channels = HashMap<String, Double>()

        // הבחירה המפורשת נכנסת ראשונה ובמשקל הגבוה ביותר. זה גם מה שגורם
        // לרדיו לעבוד בהתקנה טרייה, בלי היסטוריה ובלי לייקים.
        favoriteArtists.forEach { channels.merge(it, ARTIST_WEIGHT, Double::plus) }

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

    /**
     * האם צריך לשאול את המשתמש איזה זמרים הוא אוהב.
     *
     * נכון רק כשאין *שום* אות טעם: לא בחירה מפורשת, לא לייקים, ולא היסטוריה.
     * במצב הזה כל "רדיו אישי" הוא בדיה — הוא היה מחזיר את הפיד הכללי. עדיף
     * לשאול שאלה אחת מאשר להעמיד פנים.
     */
    suspend fun needsArtistPicker(context: Context): Boolean = withContext(Dispatchers.Default) {
        val settings = SettingsStore(context)
        if (settings.favoriteArtists.isNotEmpty()) return@withContext false
        if (settings.artistPickerSeen) return@withContext false
        val store = LibraryStore(context)
        val likes = runCatching { store.likes() }.getOrNull().orEmpty()
        val history = runCatching { store.localHistory() }.getOrNull().orEmpty()
        likes.isEmpty() && history.isEmpty()
    }

    /**
     * "רדיו מהשיר הזה" — תור באותו קו של [seed].
     *
     * ## מאיפה מגיע "הסגנון"
     * לא מניחוש. המקור החזק ביותר הוא גרף ה-related של יוטיוב עצמו: אלה
     * הסרטונים שיוטיוב, על סמך התנהגות של מיליוני אנשים, קושר לשיר הזה. זה
     * בדיוק מה שנותן "אותו ז'אנר, לא בהכרח אותו זמר". עליו נוספות שלוש
     * שכבות: אותה קטגוריה מהרשימה המאושרת, מילים משותפות בכותרת, והטעם
     * האישי — כדי שתחנה של אותו שיר תישמע אחרת אצל שני אנשים שונים.
     *
     * ## למה יש תקרה לכל ערוץ
     * בלעדיה הדירוג היה מתכנס לאותו זמר: אותו ערוץ מנצח בכל אחד מהאותות.
     * זה כבר לא רדיו אלא אלבום.
     *
     * הרשימה המוחזרת מסוננת לערוצים מאושרים בלבד — כולל פסילה של פריט
     * שהערוץ שלו לא זוהה כלל.
     */
    suspend fun stationForSeed(context: Context, seed: Video): List<Video> = withContext(Dispatchers.IO) {
        val channels = runCatching { ChannelsRepository.getCachedChannelsFast(context) }.getOrNull().orEmpty()
        val allowed = channels.mapTo(HashSet()) { it.youtubeChannelId }
        val catById = channels.associate { it.youtubeChannelId to it.category }
        val seedCat = catById[seed.channelId]
        val seedTokens = tokens(seed.title).toSet()

        val store = LibraryStore(context)
        val settings = SettingsStore(context)
        val likes = runCatching { store.likes() }.getOrNull().orEmpty()
        val history = runCatching { store.localHistory() }.getOrNull().orEmpty()
        val profile = buildProfile(
            likes, history,
            runCatching { settings.getSearchHistory() }.getOrNull().orEmpty(),
            catById,
            runCatching { settings.favoriteArtists }.getOrNull().orEmpty(),
        )

        // גרף ה-related של יוטיוב. כישלון כאן לא שובר את התחנה — הוא רק
        // מוריד אות אחד מתוך ארבעה.
        val related = runCatching { InnerTube.related(seed.id) }.getOrNull().orEmpty()
        val relatedIds = related.mapTo(HashSet()) { it.id }

        val feed = runCatching { FeedCache.loadFeed(context) }.getOrNull().orEmpty()

        val pool = (related + feed + likes + history)
            .distinctBy { it.id }
            .filter { candidate ->
                candidate.id.isNotBlank() &&
                    candidate.id != seed.id &&
                    !candidate.isShort &&
                    // ערוץ לא מזוהה נפסל. באפליקציית רשימה לבנה "לא ידוע"
                    // הוא לא "מותר" — וגרף ה-related מחזיר גם פריטים בלי
                    // מזהה ערוץ.
                    candidate.channelId in allowed
            }

        val scored = pool.map { candidate ->
            var score = 0.0
            if (candidate.id in relatedIds) score += RELATED_WEIGHT
            val cat = catById[candidate.channelId]
            if (seedCat != null && cat == seedCat) score += CATEGORY_WEIGHT * 2
            if (candidate.channelId == seed.channelId) score += SAME_ARTIST_WEIGHT
            val shared = tokens(candidate.title).count { it in seedTokens }
            if (shared > 0) score += KEYWORD_WEIGHT * minOf(shared, 3)
            // הטעם האישי מוסיף, אבל לא קובע: זו תחנה של *השיר הזה*.
            score += (profile.channels[candidate.channelId] ?: 0.0) * 0.4
            candidate to score
        }.sortedByDescending { it.second }

        val perChannel = HashMap<String, Int>()
        val station = ArrayList<Video>(STATION_SIZE)
        for ((candidate, _) in scored) {
            if (station.size >= STATION_SIZE) break
            val used = perChannel.getOrDefault(candidate.channelId, 0)
            if (used >= MAX_PER_CHANNEL) continue
            perChannel[candidate.channelId] = used + 1
            station += candidate
        }

        Diagnostics.log(
            "RADIO משיר \"${seed.title.take(40)}\": ${station.size} פריטים · " +
                "${related.size} מגרף related, ${pool.size} מועמדים מאושרים, " +
                "${perChannel.size} ערוצים שונים",
        )
        station
    }
}
