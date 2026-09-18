package com.filtertube.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * הסנכרון המלא של החשבון — היסטוריה, לייקים, מנויים, מוזיקה ופלייליסטים.
 *
 * ## למה זה כאן ולא במסך הספרייה
 * קודם כל זה רץ בתוך `rememberCoroutineScope()` של מסך הספרייה. scope כזה
 * מת יחד עם המסך: ברגע שהמשתמש עבר ללשונית אחרת — וזה בדיוק מה שאדם עושה
 * כשהסנכרון לוקח דקה וחצי — כל מה שעוד לא נשמר בוטל באמצע. ביומן זה נראה
 * כך:
 *
 *     SYNC פלייליסטים: 13 נמצאו במיוזיק
 *     SYNC פלייליסט "Favorite Songs": 100 שירים · 38 מאושרים
 *     SYNC פלייליסטים: נכשל — rememberCoroutineScope left the composition
 *
 * כלומר המשיכה עבדה מצוין, והשמירה נקטעה. הפלייליסטים הם השלב האחרון
 * והארוך ביותר (בקשה נפרדת לכל אחד), ולכן הם אלה שנפלו תמיד.
 *
 * כאן ה-scope שייך לתהליך ולא למסך. הסנכרון מתחיל מהמסך ונגמר בזמנו שלו,
 * בין אם המשתמש נשאר להסתכל ובין אם לא.
 *
 * ## איך המסכים יודעים שהסתיים
 * [completed] הוא מונה סיומים ומצב Compose גם יחד: מסך שמפתח בו LaunchedEffect
 * קורא מחדש מהספרייה ברגע שהסנכרון נגמר. כך המסך לא מחזיק את התוצאה של
 * הסנכרון — הוא מחזיק את מה שיש בספרייה, והסנכרון רק כותב לשם.
 */
object AccountSync {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** האם סנכרון רץ כרגע. שני מסכים יכולים להציג את אותו חיווי. */
    var running by mutableStateOf(false)
        private set

    /** שורת המצב שמוצגת למשתמש. נשארת גם אחרי הסיום, כסיכום. */
    var status by mutableStateOf("")
        private set

    /** עולה ב-1 בסוף כל סנכרון — מפתח רענון למסכים. */
    var completed by mutableStateOf(0)
        private set

    /**
     * מתחיל סנכרון, אם אין אחד באוויר.
     *
     * הבדיקה הזו חשובה: הכניסה למסך הספרייה מפעילה סנכרון, ומשתמש שנכנס
     * ויוצא כמה פעמים בזמן שהראשון עדיין רץ היה פותח שלושה סנכרונים
     * מקבילים על אותן עוגיות — הדרך המהירה ביותר לחטוף חסימה מיוטיוב.
     */
    fun start(context: Context) {
        if (running) return
        val app = context.applicationContext
        val accountStore = AccountStore(app)
        if (!accountStore.isLoggedIn) return

        running = true
        status = "מסנכרן את החשבון שלך..."
        scope.launch {
            try {
                sync(app, accountStore)
            } catch (e: Exception) {
                status = "שגיאה בסנכרון מלא: ${e.message}"
            } finally {
                running = false
                completed += 1
            }
        }
    }

    private suspend fun sync(context: Context, accountStore: AccountStore) {
        val store = LibraryStore(context)
        val approvedList = ChannelsRepository.getChannels(context)
        val approved = ApprovedChannels(approvedList)

        // ערוץ שלא זוהה נפסל. באפליקציית רשימה לבנה "לא ידוע" אינו "מותר",
        // ו-InnerTube מחזירה לא מעט פריטים שלא הצליחה לחלץ להם מזהה ערוץ —
        // כלומר זו הדרך העיקרית שבה תוכן לא מאושר יכול היה להגיע לספרייה
        // ומשם לנגן.
        val hist = InnerTube.history(accountStore.cookies).filter { approved.approves(it) }
        store.setHistory(hist)
        val rec = InnerTube.recommendations(accountStore.cookies).filter { approved.approves(it) }
        store.setRecommendations(rec)

        // ── לייקים, מנויים ומוזיקה — אותן עוגיות, אותה פעולה ──────────────
        // כל מקור מדווח שלושה מספרים: כמה הגיעו, כמה נפלו כי לא זוהה להם
        // ערוץ, וכמה נשארו אחרי הרשימה הלבנה. "התחבר והכל נשאר ריק" יכול
        // לנבוע מכל אחד מהשלושה, ובלי הפירוט אי אפשר לדעת מאיזה.
        fun report(name: String, items: List<Video>): List<Video> {
            val noChannel = items.count { it.channelId.isBlank() }
            val kept = items.filter { approved.approves(it) }
            Diagnostics.log(
                "SYNC $name: ${items.size} התקבלו · $noChannel בלי מזהה ערוץ · " +
                    "${kept.size} מאושרים",
            )
            return kept
        }

        // ── מוזיקה קודם, ובכוונה ──────────────────────────────────────────
        // "מוזיקה שאהבתי" אינה רשימה נפרדת אצל יוטיוב אלא *תת-קבוצה* של
        // אותו פלייליסט LL: שיר שסומן במיוזיק מופיע גם ב"אהבתי" הרגיל. לכן
        // מי שמושך את שתיהן בנפרד מקבל את אותם שירים פעמיים.
        //
        // הפתרון: המיוזיק היא הקובעת מה שיר. מה שהיא החזירה הולך
        // ל-FilterMusic, ומה שנשאר ב"אהבתי" אחרי החיסור הוא הווידאו האמיתי
        // של FilterTube. החלוקה נעשית לפי הסיווג של יוטיוב עצמה.
        val musicRaw = InnerTube.fillOwners(
            InnerTube.likedMusic(accountStore.cookies),
        ) { !approved.approves(it) }
        // ── נשמר הכל, כולל מערוצים שלא אושרו ──────────────────────────────
        // מסך "אהבתי" מציג את הלא-מאושרים באפור ומאפשר לבקש להוסיף את הערוץ
        // שלהם. הרשימה הלבנה נאכפת בלחיצה — פריט אפור לא מתנגן.
        report("מיוזיק", musicRaw)
        if (musicRaw.isNotEmpty()) store.setMusicLikes(musicRaw)
        val musicIds = musicRaw.mapTo(HashSet()) { it.id }

        // ── משלימים ערוץ לכל מה שלא נמצא ברשימה, לא רק לריקים ─────────────
        // פלייליסט "אהבתי" מחזיר לחלק מהפריטים את ערוץ ה-Topic של האמן ולא
        // את המעלה. הם קיבלו מזהה — ולכן לא נחשבו "ריקים" — אבל המזהה לא
        // היה ברשימה הלבנה, והשיר הוצג אפור למרות שהערוץ שהעלה אותו מאושר.
        val likedRaw = InnerTube.fillOwners(
            InnerTube.likedVideos(accountStore.cookies),
        ) { !approved.approves(it) }
        val liked = likedRaw.filter { it.id !in musicIds }
        report("אהבתי", liked)
        if (liked.isNotEmpty()) store.setYoutubeLikes(liked)

        val allSubs = InnerTube.subscriptions(accountStore.cookies)
        val subsFromCookies = allSubs.map { (id, name) -> SubChannel(id, name) }
        Diagnostics.log(
            "SYNC מנויים: ${allSubs.size} התקבלו · " +
                "${allSubs.count { approved.approves(it.first, it.second) }} מאושרים",
        )
        if (subsFromCookies.isNotEmpty()) store.setSubscriptions(subsFromCookies)

        // ── הפלייליסטים מיוטיוב מיוזיק ────────────────────────────────────
        // נמשכים אחרונים בכוונה: זו הקריאה היקרה ביותר (בקשה לכל פלייליסט
        // בנפרד), והיא לא אמורה לעכב את הלייקים וההיסטוריה שהמשתמש מחכה להם.
        // כישלון כאן לא נוגע בשום דבר אחר.
        val importedPlaylists = importPlaylists(accountStore, store, approved)

        status = "סונכרן ✓ ${hist.size} בהיסטוריה · ${liked.size} לייקים · " +
            "${musicRaw.size} שירים ממיוזיק · ${subsFromCookies.size} מנויים · " +
            "$importedPlaylists אלבומים · ${rec.size} המלצות"
    }

    /**
     * מייבא את הפלייליסטים של יוטיוב מיוזיק לספרייה המקומית.
     *
     * כל פלייליסט נשמר בפני עצמו, מיד אחרי שנמשך: אם הבקשה השלישית תיפול,
     * שני הראשונים כבר בספרייה. שמירה מרוכזת בסוף הייתה הופכת תקלה אחת
     * לאובדן של הכל.
     *
     * ## למה נשמר גם מה שלא אושר
     * בהתחלה סוננו כאן השירים הלא-מאושרים והפלייליסט נשמר חתוך. התוצאה
     * הייתה אלבום שמספר שקר: 100 שירים ביוטיוב, 38 באפליקציה, ואפס רמז
     * לאן נעלמו ה-62 — בדיוק אותה תקלה שכבר תוקנה ב"אהבתי" ובמנויים.
     *
     * עכשיו נשמר הכל, והמסך מאפיר את מה שלא אושר. אפור אינו דלת: הלחיצה
     * עליו מגיעה לטופס הבקשה ולא לנגן, ו"נגן הכל" מדלג עליו. כלומר
     * הרשימה הלבנה נאכפת בדיוק כמו קודם — רק שעכשיו אפשר גם לבקש להוסיף
     * את הערוץ במקום לתהות לאן השיר נעלם.
     */
    private suspend fun importPlaylists(
        accountStore: AccountStore,
        store: LibraryStore,
        approved: ApprovedChannels,
    ): Int = runCatching {
        val lists = InnerTube.musicPlaylists(accountStore.cookies)
        Diagnostics.log("SYNC פלייליסטים: ${lists.size} נמצאו במיוזיק")
        var saved = 0
        lists.forEach { list ->
            val songs = runCatching {
                // fillOwners משלים את הערוץ המעלה לכל מה שלא נמצא ברשימה.
                // בלעדיו שיר מערוץ Topic של אמן מאושר היה נראה לא מאושר.
                val items = InnerTube.fillOwners(
                    InnerTube.playlistItems(accountStore.cookies, list.id),
                ) { !approved.approves(it) }
                Diagnostics.log(
                    "SYNC פלייליסט \"${list.title}\": ${items.size} שירים · " +
                        "${items.count { approved.approves(it) }} מאושרים",
                )
                items
            }.getOrElse {
                Diagnostics.log("SYNC פלייליסט \"${list.title}\": נכשל — ${it.message}")
                emptyList()
            }
            if (songs.isEmpty()) return@forEach
            store.createPlaylist(list.title)
            songs.forEach { store.addToPlaylist(list.title, it) }
            saved += 1
        }
        Diagnostics.log("SYNC פלייליסטים: $saved נשמרו בספרייה")
        saved
    }.getOrElse {
        Diagnostics.log("SYNC פלייליסטים: נכשל — ${it.message}")
        0
    }
}
