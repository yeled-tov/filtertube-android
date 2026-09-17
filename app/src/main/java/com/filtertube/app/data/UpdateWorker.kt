package com.filtertube.app.data

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * בדיקת עדכון ברקע — ההתראה על גרסה חדשה מגיעה למגש ההתראות של הטלפון.
 *
 * ## למה זה נדרש
 * בדיקת העדכון רצה עד עכשיו רק בעליית האפליקציה, והציגה חלון בתוך המסך.
 * זה אומר שמי שלא פתח את האפליקציה פשוט לא ידע שיש גרסה חדשה — וזו בדיוק
 * הנקודה שבה עדכון חשוב (תיקון ניגון, תיקון סינון) לא מגיע ליעד.
 *
 * ## למה WorkManager ולא בדיקה בהפעלה
 * בדיקה בהפעלה דורשת שהמשתמש כבר יפתח את האפליקציה. WorkManager רץ גם
 * כשהיא סגורה, שורד אתחול מכשיר, ומכבד את מצב הסוללה של המערכת.
 *
 * ההתראה יוצאת **פעם אחת לכל בנייה**: בלי [SettingsStore.notifiedUpdateBuild]
 * אותה התראה הייתה קופצת בכל בדיקה מחדש. דילוג מפורש של המשתמש
 * ([SettingsStore.skippedUpdateBuild]) מכובד גם כאן, כמו בחלון שבאפליקציה.
 */
class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val settings = SettingsStore(ctx)

        val update = runCatching {
            UpdateChecker.check(includeTestBuilds = settings.testChannel)
        }.getOrNull() ?: return Result.success()

        if (!update.isNewer) return Result.success()
        if (update.build <= settings.skippedUpdateBuild) return Result.success()
        if (update.build <= settings.notifiedUpdateBuild) return Result.success()

        NotificationChannels.ensure(ctx)
        notify(ctx, update)
        settings.notifiedUpdateBuild = update.build
        Diagnostics.log("עדכון: התראה על ${update.displayName}")
        return Result.success()
    }

    private fun notify(ctx: Context, update: UpdateChecker.Update) {
        // הלחיצה פותחת את האפליקציה; חלון העדכון עצמו נפתח שם, עם רשימת
        // השינויים וכפתור ההורדה — אותו מסלול בדיוק, ולא שני מסלולים שונים
        // שצריך לתחזק בנפרד.
        val intent = Intent(ctx, com.filtertube.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_OPEN_UPDATE, true)
        }
        val pending = PendingIntent.getActivity(
            ctx, 9100, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val text = update.changes.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: "לחץ כדי לראות מה השתנה ולהוריד"

        val notification = NotificationCompat.Builder(ctx, NotificationChannels.UPDATES)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("FilterTube — ${update.displayName}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(update.changes.take(5).joinToString("\n") { "· $it" }.ifBlank { text }))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { manager.notify(NOTIF_ID, notification) }
    }

    companion object {
        const val EXTRA_OPEN_UPDATE = "ft_open_update"
        private const val NOTIF_ID = 4301
    }
}
