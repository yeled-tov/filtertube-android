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
    }
}
