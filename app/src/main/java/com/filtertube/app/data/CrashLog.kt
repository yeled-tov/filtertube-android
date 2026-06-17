package com.filtertube.app.data

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter
import java.util.Date

/**
 * לוכד קריסות גלובלי — שומר את ה-stack trace האחרון ל-SharedPreferences
 * (סינכרוני, כי התהליך עומד למות), כדי שנוכל להציג אותו בפתיחה הבאה ולאבחן.
 */
object CrashLog {
    private const val PREFS = "filtertube_crash"
    private const val KEY = "last_crash"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val text = buildString {
                    append("זמן: ").append(Date()).append('\n')
                    append("אנדרואיד: ").append(android.os.Build.VERSION.RELEASE)
                        .append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n")
                    append("מכשיר: ").append(android.os.Build.MANUFACTURER).append(' ')
                        .append(android.os.Build.MODEL).append("\n\n")
                    append(sw.toString())
                }
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY, text).commit()
            } catch (_: Throwable) { /* אסור שהלוכד עצמו יקרוס */ }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun lastCrash(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
