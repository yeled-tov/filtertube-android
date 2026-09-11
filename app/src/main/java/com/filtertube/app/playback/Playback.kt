package com.filtertube.app.playback

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import com.filtertube.app.data.AccountDataGuard
import com.filtertube.app.data.ChannelsRepository
import com.filtertube.app.data.LibraryStore
import com.filtertube.app.data.SettingsStore
import com.filtertube.app.data.Diagnostics
import com.filtertube.app.data.PlaybackPriority
import com.filtertube.app.data.StreamData
import com.filtertube.app.data.defaultTrackIndex
import com.filtertube.app.data.StreamRepository
import com.filtertube.app.data.Video
import com.filtertube.app.data.audioOnlyCategories
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * לוגיקת ניגון משותפת — בניית פריטי מדיה, הפעלת תור רדיו אוטונומי, ומטמון StreamData.
 */
@UnstableApi
object Playback {

    const val EXTRA_IS_AUDIO = "filtertube_is_audio"
    private const val CACHE_CAP = 60

    private val dataCache = LinkedHashMap<String, StreamData>()
    private val pendingNext = ArrayDeque<Video>()
    private val playbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var activeController: MediaController? = null

    fun cachedData(videoId: String?): StreamData? = videoId?.let { dataCache[it] }

    private fun cache(videoId: String, data: StreamData) {
        dataCache[videoId] = data
        while (dataCache.size > CACHE_CAP) {
            val oldest = dataCache.keys.firstOrNull() ?: break
            dataCache.remove(oldest)
        }
    }

    /** Add a video after the current item, or remember it for the next session. */
    suspend fun enqueueNext(context: Context, video: Video): Boolean {
        val data = runCatching { StreamRepository.getStream(video.id) }.getOrNull()
        if (data == null) {
            synchronized(pendingNext) { pendingNext.removeAll { it.id == video.id }; pendingNext.addLast(video) }
            return false
        }
        cache(video.id, data)
        val controller = activeController
        if (controller == null) {
            synchronized(pendingNext) { pendingNext.removeAll { it.id == video.id }; pendingNext.addLast(video) }
            return true
        }
        return runCatching {
            val settings = SettingsStore(context)
            val item = buildItem(data, video.id, forcedAudio(null, settings.filterLevel, settings.audioOnlyMode), defaultQuality(data, settings.preferredQuality))
            withContext(Dispatchers.Main) {
                val index = (controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount)
                controller.addMediaItem(index, item)
            }
            true
        }.getOrElse {
            synchronized(pendingNext) { pendingNext.removeAll { it.id == video.id }; pendingNext.addLast(video) }
            false
        }
    }

    /**
     * מנקה בקשות "הוסף לתור" שממתינות.
     *
     * בלי זה, סרטון שנוסף ל-pendingNext לפני העצירה היה נכנס לתור ברגע
     * שהמשתמש מפעיל משהו אחר — כלומר "הסגירה" לא באמת ניקתה הכל.
     */
    fun clearPending() {
        synchronized(pendingNext) { pendingNext.clear() }
    }

    private suspend fun addPendingNext(context: Context, controller: MediaController) {
        val requested = synchronized(pendingNext) {
            val copy = pendingNext.toList(); pendingNext.clear(); copy
        }.take(10)
        if (requested.isEmpty()) return
        val settings = SettingsStore(context)
        for (video in requested) {
            val data = runCatching { StreamRepository.getStream(video.id) }.getOrNull() ?: continue
            cache(video.id, data)
            val item = buildItem(data, video.id, forcedAudio(null, settings.filterLevel, settings.audioOnlyMode), defaultQuality(data, settings.preferredQuality))
            withContext(Dispatchers.Main) {
                controller.addMediaItem((controller.currentMediaItemIndex + 1).coerceAtMost(controller.mediaItemCount), item)
            }
        }
    }

    /** אינדקס איכות ברירת מחדל. ראה [com.filtertube.app.data.defaultTrackIndex]. */
    fun defaultQuality(data: StreamData, preferred: Int = 0): Int =
        data.defaultTrackIndex(preferred)

    /**
     * האם הפריט הזה חייב להתנגן כאודיו בלבד.
     *
     * שלושה מקורות, וכל אחד מהם מספיק:
     *  • [audioOnlyMode] — בחירה גלובלית של המשתמש, חלה בכל רמות הסינון
     *  • קטגוריה שהיא אודיו-בלבד לפי מדיניות התוכן
     *  • מוזיקה ברמת הסינון המחמירה
     */
    fun forcedAudio(category: String?, level: Int, audioOnlyMode: Boolean = false): Boolean =
        audioOnlyMode || category in audioOnlyCategories || (level == 1 && category == "music")

    /** גרסה שקוראת את ההעדפה בעצמה — לנתיבים שאין להם SettingsStore ביד. */
    fun forcedAudio(context: Context, category: String?): Boolean {
        val settings = SettingsStore(context)
        return forcedAudio(category, settings.filterLevel, settings.audioOnlyMode)
    }

    /**
     * פריט מדיה מקובץ מקומי — בלי פתרון זרם ובלי רשת.
     *
     * אין כאן EXTRA_AUDIO_URL ואין User-Agent: הקובץ כבר ממוזג ושמור על
     * המכשיר, ולכן FilterTubeMediaSourceFactory מנגן אותו כמו שהוא.
     */
    /**
     * האם באמת אפשר לפתוח את הקובץ שהורד.
     *
     * קיום רשומה בספרייה לא מבטיח קיום קובץ: המשתמש יכול למחוק אותו
     * מ"הורדות", המערכת יכולה לנקות, והתקנה מחדש של האפליקציה מאבדת את
     * הבעלות על רשומת ה-MediaStore. בלי הבדיקה הזו הנגן היה מקבל URI מת
     * ונתקע, במקום פשוט לנגן מהרשת.
     */
    private fun localFileReadable(context: Context, uri: String): Boolean {
        if (uri.isBlank()) return false
        return runCatching {
            context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false
        }.getOrDefault(false)
    }

    /**
     * כתובת האודיו לפי הגדרת "איכות שמע" של המשתמש.
     *
     * 0 אוטומטי ו-1 גבוהה מנגנים את הזרם הטוב ביותר; 2 בוחר את הקל ביותר,
     * וזה ההבדל האמיתי בנתונים — לא תווית במסך.
     */
    private fun audioUrlFor(data: StreamData): String {
        val choice = audioQualityChoice
        val low = data.lowAudioUrl
        return if (choice == 2 && !low.isNullOrBlank()) low
        else data.bestAudioUrl ?: data.bestVideoUrl
    }

    /** נקרא פעם אחת לכל הכנה, ב-IO, ונשמר כאן כדי לא לגעת בדיסק בבניית הפריט. */
    @Volatile
    private var audioQualityChoice: Int = 0

    fun localItem(video: Video): MediaItem =
        MediaItem.Builder()
            .setUri(Uri.parse(video.localUri))
            .setMediaId(video.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(video.title)
                    .setArtist(video.channelName)
                    .setArtworkUri(video.thumbnailUrl.takeIf { it.isNotBlank() }?.let(Uri::parse))
                    .build(),
            )
            .build()

    fun buildItem(data: StreamData, videoId: String, audio: Boolean, qualityIndex: Int = defaultQuality(data)): MediaItem {
        val extras = Bundle().apply { putBoolean(EXTRA_IS_AUDIO, audio) }
        data.streamUserAgent?.let { extras.putString(FilterTubeMediaSourceFactory.EXTRA_USER_AGENT, it) }
        val uri: String = if (audio) {
            audioUrlFor(data)
        } else {
            val t = data.tracks.getOrNull(qualityIndex)
            if (t == null) data.bestVideoUrl
            else {
                if (!t.audioUrl.isNullOrEmpty()) extras.putString(FilterTubeMediaSourceFactory.EXTRA_AUDIO_URL, t.audioUrl)
                t.videoUrl
            }
        }
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(videoId)
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setExtras(extras).build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(data.title)
                    .setArtist(data.uploaderName)
                    .setArtworkUri(data.thumbnailUrl?.let { Uri.parse(it) })
                    .build(),
            )
            .build()
    }

    /** מזהה של פריט מדיה שמקורו בטלפון עצמו ולא ביוטיוב. */
    const val LOCAL_ID_PREFIX = "local:"

    /**
     * מנגן רשימת קבצים מקומיים כתור אמיתי, החל מ-[startIndex].
     *
     * זה המסלול של "נגן רגיל בטלפון": אין כאן פתרון זרם, אין בדיקת רשימה
     * לבנה ואין רשת. הרשימה כולה נמסרת לנגן בבת אחת, כדי ש"הבא"/"הקודם",
     * ההתקדמות האוטומטית לשיר הבא ופקדי המסך הנעול יעבדו כמו בכל נגן.
     */
    suspend fun startLocalQueue(
        context: Context,
        controller: MediaController?,
        items: List<Video>,
        startIndex: Int,
    ) {
        val c = controller ?: return
        if (items.isEmpty()) return
        val index = startIndex.coerceIn(0, items.lastIndex)
        // openFileDescriptor לכל פריט הוא IPC — לא על תהליכון ה-UI.
        val playable = withContext(Dispatchers.IO) {
            items.map { localItem(it) }
        }
        Diagnostics.log("PLAYBACK מקומי: ${items.size} פריטים, מתחיל ב-${items[index].title}")
        c.setMediaItems(playable, index, 0L)
        c.prepare()
        c.play()
    }

    /**
     * מתחיל ניגון של [video] מיד, ומפעיל ברקע בניית תור רדיו אוטונומי.
     *
     * [station] — תחנה שנבנתה מראש (רדיו אישי). כשהיא לא ריקה היא זו שמנוגנת,
     * ומנוע ה"סרטונים הקשורים" של יוטיוב לא נכנס לתמונה עד שהיא נגמרת.
     */
    suspend fun start(
        context: Context,
        controller: MediaController?,
        video: Video,
        station: List<Video> = emptyList(),
    ) {
        val c = controller ?: return
        // מכריזים על חזית: עבודות הרקע (רדיו, חימום, העשרה) ימתינו כדי לא
        // לחנוק את ההורדה של הזרם שהמשתמש מחכה לו ממש עכשיו.
        PlaybackPriority.begin()
        try {
            startInternal(context, c, video, station)
        } finally {
            // משחררים רק אחרי שהנגן הספיק למלא באפר, לא ברגע ש-play() חזר.
            playbackScope.launch {
                delay(PLAYBACK_GRACE_MS)
                PlaybackPriority.end()
            }
        }
    }

    /**
     * כל מה שצריך לקרוא מהדיסק לפני שמתחילים לנגן.
     *
     * מוגדר כאן ולא כמחלקה מקומית בתוך startInternal: מחלקת data מקומית בתוך
     * פונקציה הפילה את ניתוח ה-lint ("Lint found 1 error" בלי שם קובץ),
     * והבנייה נעצרה בלי שום שורת שגיאה שמצביעה על המקום.
     */
    private data class Prep(
        val level: Int,
        val preferred: Int,
        val catById: Map<String, String>,
        val offline: Video?,
        val audioOnly: Boolean,
    )

    /**
     * זמן החסד שבו הרשת שמורה לנגן אחרי הלחיצה.
     *
     * שלוש שניות לא הספיקו. הן מכסות את תחילת הניגון, אבל הנגן ממשיך למלא
     * באפר הרבה אחרי שהצליל יצא — וברגע שהשער נפתח, בניית תור הרדיו והחימום
     * מראש הציפו את הרשת. היומן הראה עצירה של 26 שניות שהתחילה בדיוק בשנייה
     * השלישית של השיר.
     */
    private const val PLAYBACK_GRACE_MS = 12_000L

    private suspend fun startInternal(
        context: Context,
        c: MediaController,
        video: Video,
        station: List<Video>,
    ) {
        activeController = c
        // יציאה שקטה כאן נראית למשתמש בדיוק כמו תקלה: המסך נשאר על "טוען..."
        // בלי שום הסבר. רושמים ליומן כדי שהמקרה הזה יהיה ניתן לאבחון.
        val firebaseUser = FirebaseAuth.getInstance().currentUser
            ?.takeIf { it.isEmailVerified }
            ?: run {
                Diagnostics.log("PLAYBACK: אין משתמש מאומת — הניגון לא התחיל")
                return
            }
        val expectedUid = firebaseUser.uid
        val generation = AccountDataGuard.generation()
        // נבנה בעצלתיים: הבנייה עצמה נוגעת ב-SharedPreferences וב-FirebaseAuth,
        // והשימוש היחיד בו הוא כתיבת ההיסטוריה — אחרי ש-play() כבר נקרא.
        val library by lazy { LibraryStore(context) }
        fun sessionCurrent(): Boolean {
            val current = FirebaseAuth.getInstance().currentUser
            return current?.uid == expectedUid &&
                current.isEmailVerified &&
                AccountDataGuard.generation() == generation
        }
        // ── כל ההכנה יורדת מתהליכון ה-UI ────────────────────────────────
        // startInternal נקרא מ-rememberCoroutineScope, כלומר מ-Main. כל מה
        // שמתחת רץ *לפני* שהצליל יוצא: קריאת ההגדרות, מפת 168 הערוצים,
        // פענוח JSON של רשימת ההורדות, ופתיחת מתאר קובץ מול ContentResolver.
        // כל אלה נגיעות דיסק ו-IPC, והן הצטברו לעיכוב מורגש בכל לחיצה על
        // סרטון — הרגרסיה שהחזירה את "עולה מהר אבל לא מתחיל מהר".
        val prep = withContext(Dispatchers.IO) {
            val settings = SettingsStore(context)
            val channels = ChannelsRepository.getCachedChannelsFast(context)
            // קובץ מקומי שהגיע ישירות (נגן המכשיר) קודם לחיפוש בספריית ההורדות:
            // ל-Video כזה אין בכלל מזהה יוטיוב, ולכן downloadedVideo לא היה מוצא אותו.
            val direct = video.takeIf { it.localUri.isNotBlank() && localFileReadable(context, it.localUri) }
            val downloaded = direct ?: LibraryStore(context).downloadedVideo(video.id)
                ?.takeIf { localFileReadable(context, it.localUri) }
            audioQualityChoice = settings.audioQuality
            Prep(
                level = settings.filterLevel,
                preferred = settings.preferredQuality,
                catById = channels.associate { it.youtubeChannelId to it.category },
                offline = downloaded,
                audioOnly = settings.audioOnlyMode,
            )
        }
        val level = prep.level
        val catById = prep.catById

        // ── קובץ שהורד קודם לרשת ────────────────────────────────────────
        // אם הסרטון כבר על המכשיר, אין שום סיבה לפתור זרם: זה מיידי, זה לא
        // צורך נתונים, וזה עובד גם בלי חיבור.
        val offlineCopy = prep.offline
        if (offlineCopy != null) {
            Diagnostics.log("PLAYBACK ${video.id}: מנגן מקובץ שהורד")
            if (!sessionCurrent()) return
            c.setMediaItem(localItem(offlineCopy))
            c.prepare()
            c.play()
            // קבצים מהטלפון לא נכנסים להיסטוריית הצפייה: ההיסטוריה הזו מזינה את
            // הרדיו האישי ואת ההמלצות, ואין שום דרך להסיק טעם יוטיוב משיר שהועבר
            // מהמחשב.
            if (!offlineCopy.id.startsWith(LOCAL_ID_PREFIX)) runCatching { library.addToHistory(offlineCopy) }
            return
        }
        val preferred = prep.preferred
        val data = StreamRepository.getStream(video.id)
        if (!sessionCurrent()) return
        cache(video.id, data)

        com.filtertube.app.data.LibraryBadges.markWatched(video.id)
        val audio = forcedAudio(catById[data.channelId], level, prep.audioOnly)
        val firstItem = buildItem(data, video.id, audio, defaultQuality(data, preferred))

        if (!sessionCurrent()) return
        c.setMediaItem(firstItem)
        c.prepare()
        c.play()

        // כתיבת ההיסטוריה מפענחת ומקודדת JSON שלם ומתזמנת גיבוי לענן. אין שום
        // סיבה שהמשתמש יחכה לזה לפני שהצליל יוצא, אז זה עבר לכאן.
        runCatching {
            library.addToHistory(
                Video(
                    id = video.id,
                    title = data.title.ifBlank { video.title },
                    channelName = data.uploaderName.ifBlank { video.channelName },
                    channelId = data.channelId.ifBlank { video.channelId },
                    thumbnailUrl = data.thumbnailUrl ?: video.thumbnailUrl,
                    // תאריך ההעלאה האמיתי נשמר; זמן הצפייה נרשם ב-watchedAt בתוך addToHistory.
                    publishedAt = video.publishedAt,
                    durationSec = data.durationSec.takeIf { it > 0L } ?: video.durationSec,
                    viewCount = data.viewCount.takeIf { it > 0L } ?: video.viewCount,
                ),
            )
        }
        addPendingNext(context, c)

        // הפעלה מבוזרת ומהירה ברקע של תור הרדיו (ללא שום delay חוסם!)
        RadioQueueManager.startQueue(context, c, video, playbackScope, station)
    }
}
