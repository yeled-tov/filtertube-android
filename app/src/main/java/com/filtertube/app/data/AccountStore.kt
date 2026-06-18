package com.filtertube.app.data

import android.content.Context

/**
 * אחסון מצב ההתחברות ל-YouTube דרך InnerTube (cookies שנלכדו ב-WebView).
 * זה מה שמאפשר סנכרון מלא (היסטוריה/המלצות/לייקים) כמו YMusic/Metrolist —
 * בניגוד ל-API הרשמי המוגבל.
 */
class AccountStore(context: Context) {
    private val prefs = context.getSharedPreferences("filtertube_account", Context.MODE_PRIVATE)

    /** כותרת ה-Cookie המלאה שנלכדה מההתחברות. */
    var cookies: String
        get() = prefs.getString(KEY_COOKIES, "") ?: ""
        set(value) = prefs.edit().putString(KEY_COOKIES, value).apply()

    /** מזהה החשבון (אינדקס) — בדרך כלל 0. */
    var authUser: Int
        get() = prefs.getInt(KEY_AUTHUSER, 0)
        set(value) = prefs.edit().putInt(KEY_AUTHUSER, value).apply()

    /** מחובר אם יש לנו את עוגיית ה-SAPISID (הדרושה לאימות InnerTube). */
    val isLoggedIn: Boolean
        get() = cookies.let { it.contains("SAPISID") || it.contains("__Secure-3PAPISID") }

    fun logout() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_COOKIES = "cookies"
        private const val KEY_AUTHUSER = "authuser"
    }
}
