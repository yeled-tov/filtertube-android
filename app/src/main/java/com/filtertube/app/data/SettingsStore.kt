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

    /** הצבע הראשי של האפליקציה (ARGB int). ברירת מחדל אדום. */
    var accentColor: Int
        get() = prefs.getInt(KEY_ACCENT, 0xFFFF0000.toInt())
        set(value) = prefs.edit().putInt(KEY_ACCENT, value).apply()

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
        private const val KEY_QUALITY = "preferred_quality"
        private const val KEY_PLAYER_STYLE = "player_style"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_ADMIN_UNLOCKED = "admin_unlocked"
        private const val KEY_NOTIFY = "new_video_notifications"
    }
}
