package com.filtertube.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * הגדרות מקומיות + היסטוריית חיפוש — דרך SharedPreferences.
 */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("filtertube_settings", Context.MODE_PRIVATE)

    var shortsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SHORTS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHORTS, value).apply()

    /**
     * רמת סינון:
     *  1 = מחמיר (מוזיקה מתנגנת אודיו בלבד; ערוצי "דתי לייט" מוסתרים)
     *  2 = רגיל (הכל וידאו; ערוצי "דתי לייט" מוסתרים)
     *  3 = דתי לייט (ערוצי "דתי לייט" מוצגים ומתנגנים אודיו בלבד)
     */
    var filterLevel: Int
        get() = prefs.getInt(KEY_LEVEL, 2)
        set(value) = prefs.edit().putInt(KEY_LEVEL, value).apply()

    /** GitHub token לפאנל הניהול (מאוחסן מקומית) */
    var githubToken: String
        get() = prefs.getString(KEY_GH_TOKEN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GH_TOKEN, value).apply()

    /** מצב ניהול נחשף (7 לחיצות על "אודות") — מסתיר את ניהול הערוצים מלקוחות רגילים. */
    var adminUnlocked: Boolean
        get() = prefs.getBoolean(KEY_ADMIN_UNLOCKED, false)
        set(value) = prefs.edit().putBoolean(KEY_ADMIN_UNLOCKED, value).apply()

    /**
     * סיסמת הורים על הגדרות הסינון (כולל הצגת Shorts).
     * ריק = עדיין לא נקבעה סיסמה. כל שינוי ברמת הסינון דורש קודם אימות.
     */
    var filterPassword: String
        get() = prefs.getString(KEY_FILTER_PW, "") ?: ""
        set(value) = prefs.edit().putString(KEY_FILTER_PW, value).apply()

    val hasFilterPassword: Boolean get() = filterPassword.isNotEmpty()

    fun checkFilterPassword(input: String): Boolean = filterPassword == input.trim()

    /** קצב רענון גבוה (120 הרץ) — תצוגה חלקה במכשירים שתומכים. */
    var highRefreshRate: Boolean
        get() = prefs.getBoolean(KEY_HIGH_HZ, true)
        set(value) = prefs.edit().putBoolean(KEY_HIGH_HZ, value).apply()

    /** הצבע הראשי של האפליקציה (ARGB int). ברירת מחדל #FF2D43 (אמבר). */
    var accentColor: Int
        get() = prefs.getInt(KEY_ACCENT, 0xFFFF2D43.toInt())
        set(value) = prefs.edit().putInt(KEY_ACCENT, value).apply()

    /** הצבע המשני לגרדיאנט (ARGB int). ברירת מחדל #FF6A5C. */
    var accent2Color: Int
        get() = prefs.getInt(KEY_ACCENT2, 0xFFFF6A5C.toInt())
        set(value) = prefs.edit().putInt(KEY_ACCENT2, value).apply()

    /** איכות ניגון מועדפת לפי גובה (px). 0 = אוטומטי (עד 720). */
    var preferredQuality: Int
        get() = prefs.getInt(KEY_QUALITY, 0)
        set(value) = prefs.edit().putInt(KEY_QUALITY, value).apply()

    /** עיצוב הנגן: 1 = "מתנגן עכשיו" (נוכחי), 2 = בקרים על הוידאו + הבא בתור מתחת. */
    var playerStyle: Int
        get() = prefs.getInt(KEY_PLAYER_STYLE, 1)
        set(value) = prefs.edit().putInt(KEY_PLAYER_STYLE, value).apply()

    /** מצב נושא: 0 = לפי המערכת, 1 = כהה, 2 = בהיר. */
    var themeMode: Int
        get() = prefs.getInt(KEY_THEME_MODE, 1)
        set(value) = prefs.edit().putInt(KEY_THEME_MODE, value).apply()

    /** התראות על סרטון חדש בערוץ מאושר (בדיקת רקע תקופתית). */
    var newVideoNotifications: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFY, value).apply()

    /** כמה הורדות לרוץ במקביל (1–4). */
    var concurrentDownloads: Int
        get() = prefs.getInt(KEY_DL_CONCURRENT, 3).coerceIn(1, 4)
        set(value) = prefs.edit().putInt(KEY_DL_CONCURRENT, value.coerceIn(1, 4)).apply()

    /** כמה חיבורים מקביליים לכל קובץ (1–8) — מאיץ הורדה שנחנקת ע"י ה-CDN. */
    var connectionsPerDownload: Int
        get() = prefs.getInt(KEY_DL_CONNECTIONS, 4).coerceIn(1, 8)
        set(value) = prefs.edit().putInt(KEY_DL_CONNECTIONS, value.coerceIn(1, 8)).apply()

    /** הורדה אוטומטית של כל סרטון ש"אהבתי". */
    var autoDownloadLikes: Boolean
        get() = prefs.getBoolean(KEY_DL_AUTO_LIKES, false)
        set(value) = prefs.edit().putBoolean(KEY_DL_AUTO_LIKES, value).apply()

    /** המשך ניגון כשהאפליקציה ברקע. כבוי = עצירה ביציאה מהאפליקציה. */
    var backgroundPlay: Boolean
        get() = prefs.getBoolean(KEY_BG_PLAY, true)
        set(value) = prefs.edit().putBoolean(KEY_BG_PLAY, value).apply()

    // ── הרשמה / פרופיל ──────────────────────────────────────────────────
    /** האם המשתמש סיים את מסך ההרשמה הראשוני. */
    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var userEmail: String
        get() = prefs.getString(KEY_USER_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()

    /** "male" / "female". */
    var userGender: String
        get() = prefs.getString(KEY_USER_GENDER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_GENDER, value).apply()

    /** תחילת תקופת הניסיון (מילישניות). 0 = לא התחיל. */
    var trialStartMillis: Long
        get() = prefs.getLong(KEY_TRIAL_START, 0L)
        set(value) = prefs.edit().putLong(KEY_TRIAL_START, value).apply()

    /** מנוי בתשלום פעיל (נקבע ע"י שרת התשלומים לאחר רכישה מאומתת). */
    var premiumPurchased: Boolean
        get() = prefs.getBoolean(KEY_PREMIUM_PAID, false)
        set(value) = prefs.edit().putBoolean(KEY_PREMIUM_PAID, value).apply()

    /** פרימיום פעיל — ניסיון 60 יום פעיל *או* מנוי בתשלום (הורדות, ניגון ברקע, חלון צף). */
    val premiumActive: Boolean
        get() {
            if (premiumPurchased) return true
            val start = trialStartMillis
            if (start == 0L) return true   // עוד לא נקבע — אל תחסום
            return System.currentTimeMillis() - start < 60L * 24 * 60 * 60 * 1000
        }

    /** ימים שנותרו בניסיון (לתצוגה). */
    val trialDaysLeft: Int
        get() {
            val start = trialStartMillis
            if (start == 0L) return 60
            val left = 60 - ((System.currentTimeMillis() - start) / (24L * 60 * 60 * 1000)).toInt()
            return left.coerceAtLeast(0)
        }

    /** צורת פס ההתקדמות בנגן: 0 = ישר, 1 = גלי, 2 = זיגזג. */
    var seekBarShape: Int
        get() = prefs.getInt(KEY_SEEK_SHAPE, 1)
        set(value) = prefs.edit().putInt(KEY_SEEK_SHAPE, value).apply()

    /** עובי פס ההתקדמות (1–6 dp לערך). */
    var seekBarThickness: Int
        get() = prefs.getInt(KEY_SEEK_THICK, 3).coerceIn(1, 6)
        set(value) = prefs.edit().putInt(KEY_SEEK_THICK, value.coerceIn(1, 6)).apply()

    /** זוהר (glow) סביב פס ההתקדמות. */
    var seekBarGlow: Boolean
        get() = prefs.getBoolean(KEY_SEEK_GLOW, true)
        set(value) = prefs.edit().putBoolean(KEY_SEEK_GLOW, value).apply()

    // ── היסטוריית חיפוש ──────────────────────────────────────────────────
    fun getSearchHistory(): List<String> {
        val raw = prefs.getString(KEY_HISTORY, "") ?: ""
        return raw.split("\n").filter { it.isNotBlank() }
    }

    fun addSearchQuery(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val current = getSearchHistory().toMutableList()
        current.remove(q)              // הסר כפילות
        current.add(0, q)              // הוסף בראש
        val trimmed = current.take(20) // שמור עד 20 אחרונים
        prefs.edit().putString(KEY_HISTORY, trimmed.joinToString("\n")).apply()
    }

    fun removeSearchQuery(query: String) {
        val current = getSearchHistory().toMutableList()
        current.remove(query)
        prefs.edit().putString(KEY_HISTORY, current.joinToString("\n")).apply()
    }

    fun clearSearchHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    companion object {
        private const val KEY_SHORTS = "shorts_enabled"
        private const val KEY_HISTORY = "search_history"
        private const val KEY_LEVEL = "filter_level"
        private const val KEY_GH_TOKEN = "github_token"
        private const val KEY_FILTER_PW = "filter_password"
        private const val KEY_HIGH_HZ = "high_refresh_rate"
        private const val KEY_ACCENT = "accent_color"
        private const val KEY_ACCENT2 = "accent2_color"
        private const val KEY_QUALITY = "preferred_quality"
        private const val KEY_PLAYER_STYLE = "player_style"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_ADMIN_UNLOCKED = "admin_unlocked"
        private const val KEY_NOTIFY = "new_video_notifications"
        private const val KEY_DL_CONCURRENT = "dl_concurrent"
        private const val KEY_DL_CONNECTIONS = "dl_connections"
        private const val KEY_DL_AUTO_LIKES = "dl_auto_likes"
        private const val KEY_BG_PLAY = "background_play"
        private const val KEY_ONBOARDED = "onboarding_done"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_GENDER = "user_gender"
        private const val KEY_TRIAL_START = "trial_start_millis"
        private const val KEY_PREMIUM_PAID = "premium_purchased"
        private const val KEY_SEEK_SHAPE = "seek_bar_shape"
        private const val KEY_SEEK_THICK = "seek_bar_thickness"
        private const val KEY_SEEK_GLOW = "seek_bar_glow"
    }
}
