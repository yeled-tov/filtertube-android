package com.filtertube.app.data

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * מנוע החיפוש של FilterTube — מסונן לרשימה הלבנה, ובלי מכסת YouTube API.
 *
 * ## הבעיה שזה מתקן
 * הגרסה הקודמת חיפשה דרך `search.list` של YouTube Data API: חיפוש *גלובלי* בכל
 * יוטיוב, שממנו נלקחו 40 תוצאות ואז סוננו לערוצים המאושרים. שתי תקלות נבעו מזה:
 *
 * 1. **"לא נמצאו תוצאות" למרות שהערוץ מאושר.** כששר מוכר מחזיק ערוץ מאושר אבל 40
 *    התוצאות הראשונות בחיפוש הגלובלי הן מערוצים אחרים, הסינון מחק את הכל והמסך
 *    הציג שגיאה — בלי ליפול ל-NewPipe, כי מבחינת הקוד הקריאה "הצליחה".
 * 2. **המכסה נגמרה.** 100 יחידות לחיפוש, מתוך 10,000 ליום, לכל המשתמשים ביחד.
 *
 * ## הפתרון
 * שלושה שלבים, מהמהיר לאיטי, כשכל שלב מציג תוצאות מיד:
 *
 * 1. **אינדקס מקומי** — הפיד השמור של הערוצים המאושרים כבר על המכשיר. תוצאות
 *    מיידיות, אפס רשת, אפס מכסה. מכסה את רוב החיפושים ("שם של זמר" → הסרטונים שלו).
 * 2. **התאמת ערוץ** — אם השאילתה היא שם של ערוץ מאושר, מושכים את הסרטונים של אותו
 *    ערוץ ישירות מה-RSS שלו. זה מה שסוגר בדיוק את התלונה "החיפוש לא מוצא את הערוץ".
 * 3. **NewPipe** — חיפוש עמוק בכל יוטיוב, מסונן לרשימה הלבנה. חינם, בלי מפתח API.
 */
object SearchEngine {

    data class Outcome(
        val videos: List<Video>,
        /** true רק כשכל שלושת המקורות נכשלו — אז מוצגת שגיאה אמיתית ולא "אין תוצאות". */
        val failed: Boolean,
    )

    /**
     * מריץ חיפוש מסונן. [onPartial] נקרא אחרי כל שלב עם כל מה שנאסף עד כה,
     * כדי שהמשתמש יראה תוצאות תוך מילישניות ולא אחרי כמה שניות.
     */
    suspend fun search(
        context: Context,
        rawQuery: String,
        channels: List<Channel>,
        onPartial: suspend (List<Video>) -> Unit = {},
    ): Outcome {
        val query = rawQuery.trim()
        if (query.isEmpty()) return Outcome(emptyList(), failed = false)

        val allowedIds = channels.mapTo(HashSet()) { it.youtubeChannelId }
        val collected = LinkedHashMap<String, Video>()
        var anySourceWorked = false

        suspend fun publish(videos: List<Video>) {
            var added = false
            videos.forEach { video ->
                if (video.id.isNotBlank() && video.channelId in allowedIds && !collected.containsKey(video.id)) {
                    collected[video.id] = video
                    added = true
                }
            }
            if (added) onPartial(collected.values.toList())
        }

        // ── 1. אינדקס מקומי — מיידי ────────────────────────────────────────
        runCatching {
            val cached = buildList {
                FeedCache.loadFeed(context)?.let(::addAll)
                FeedCache.loadShorts(context)?.let(::addAll)
            }
            anySourceWorked = anySourceWorked || cached.isNotEmpty()
            publish(matchLocally(cached, query))
            Diagnostics.log("SEARCH: אינדקס מקומי — ${collected.size} תוצאות")
        }

        // ── 2. ערוץ מאושר ששמו תואם לשאילתה ────────────────────────────────
        val matchedChannels = channels.filter { nameMatches(it.name, query) }.take(3)
        for (channel in matchedChannels) {
            runCatching {
                val videos = YouTubeRepository.fetchChannelVideos(channel.youtubeChannelId, channel.name)
                anySourceWorked = true
                publish(videos)
                Diagnostics.log("SEARCH: ערוץ תואם '${channel.name}' — ${videos.size} סרטונים")
            }.onFailure {
                if (it is CancellationException) throw it
                Diagnostics.log("SEARCH: ערוץ '${channel.name}' נכשל — ${it.message}")
            }
        }

        // ── 3. NewPipe — חיפוש עמוק, מסונן ────────────────────────────────
        runCatching {
            YouTubeRepository.search(query, channels) { partial ->
                // ה-callback של NewPipe סינכרוני; אוספים כאן ומפרסמים אחרי כל עמוד.
                partial.forEach { video ->
                    if (video.id.isNotBlank() && video.channelId in allowedIds) {
                        collected.putIfAbsent(video.id, video)
                    }
                }
            }
        }.onSuccess {
            anySourceWorked = true
            Diagnostics.log("SEARCH: NewPipe הושלם — ${collected.size} תוצאות מצטברות")
            onPartial(collected.values.toList())
        }.onFailure {
            if (it is CancellationException) throw it
            Diagnostics.log("SEARCH: NewPipe נכשל — ${it.message}")
        }

        // ── העשרה במטא-דאטה אמיתי (יחידת מכסה אחת לכל 50) ─────────────────
        val ranked = rank(collected.values.toList(), query)
        val enriched = runCatching { VideoMetadata.enrich(context, ranked, limit = 60) }
            .getOrDefault(ranked)

        return Outcome(
            videos = enriched,
            // שגיאה אמיתית רק אם שום מקור לא ענה. אין תוצאות ≠ תקלה.
            failed = enriched.isEmpty() && !anySourceWorked,
        )
    }

    // ── התאמה טקסטואלית ───────────────────────────────────────────────────
    /**
     * רץ על Dispatchers.Default ולא על הקורוטינה של הקורא.
     *
     * [search] נקרא מ-rememberCoroutineScope, כלומר מ-Dispatchers.Main. האינדקס
     * המקומי הוא כ-2,500 סרטונים, וכל אחד מהם עובר נרמול מלא — זה עבודת מעבד
     * ממשית, ועד עכשיו היא רצה על תהליכון ה-UI וקיפאה את המסך בדיוק ברגע
     * שהמשתמש לוחץ "חפש".
     */
    private suspend fun matchLocally(videos: List<Video>, query: String): List<Video> =
        withContext(Dispatchers.Default) {
            val tokens = normalize(query).split(' ').filter { it.length >= 2 }
            if (tokens.isEmpty()) return@withContext emptyList()
            videos.filter { video ->
                val haystack = normalize("${video.title} ${video.channelName}")
                tokens.all { haystack.contains(it) }
            }
        }

    private fun nameMatches(channelName: String, query: String): Boolean {
        val name = normalize(channelName)
        val q = normalize(query)
        if (q.length < 2) return false
        return name == q || name.contains(q) || q.contains(name)
    }

    // ה-Regex-ים מהודרים פעם אחת ולא בכל קריאה. normalize נקרא פעמיים לכל
    // סרטון באינדקס המקומי — עם ארבעה Regex שנבנים מחדש בכל פעם זה היה
    // עשרות אלפי הידורי ביטוי רגולרי בכל חיפוש.
    private val NIQQUD = Regex("[\\u0591-\\u05C7]")
    private val QUOTES = Regex("[׳'’`\"״“”]")
    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")
    private val SPACES = Regex("\\s+")

    /**
     * מנרמל טקסט עברי לצורך השוואה: מסיר ניקוד, מאחד גרשיים/אפוסטרופים
     * ומצמצם רווחים. בלי זה "משה'ס" ו"משהס" לא היו נחשבים להתאמה.
     */
    private fun normalize(text: String): String = text
        .lowercase()
        .replace(NIQQUD, "")        // ניקוד וטעמים
        .replace(QUOTES, "")        // גרש/גרשיים בכל הווריאציות
        .replace(NON_ALNUM, " ")    // כל השאר → רווח
        .trim()
        .replace(SPACES, " ")

    /**
     * תוצאות מהערוץ שהשם שלו תואם עולות למעלה, ואחריהן הטריות ביותר.
     *
     * הנרמול נעשה פעם אחת לכל סרטון ולא בתוך ה-Comparator: sortedWith קורא
     * להשוואה O(n log n) פעמים, כך שאותו כותרת הייתה מנורמלת שוב ושוב.
     */
    private suspend fun rank(videos: List<Video>, query: String): List<Video> =
        withContext(Dispatchers.Default) {
            val q = normalize(query)
            videos.map { video ->
                Triple(video, nameMatches(video.channelName, q), normalize(video.title).contains(q))
            }.sortedWith(
                compareByDescending<Triple<Video, Boolean, Boolean>> { it.second }
                    .thenByDescending { it.third }
                    .thenByDescending { it.first.publishedAt },
            ).map { it.first }
        }

    /** רשימת הצעות מקומית להשלמה אוטומטית — שמות ערוצים מאושרים שמתאימים לקלט. */
    suspend fun channelSuggestions(channels: List<Channel>, query: String): List<String> =
        withContext(Dispatchers.Default) {
            val q = normalize(query)
            if (q.length < 2) return@withContext emptyList()
            channels.asSequence()
                .filter { normalize(it.name).contains(q) }
                .map { it.name }
                .distinct()
                .take(5)
                .toList()
        }
}
