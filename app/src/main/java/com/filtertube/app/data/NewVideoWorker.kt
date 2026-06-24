package com.filtertube.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * בדיקת רקע תקופתית (WorkManager) — מושכת את הפיד של הערוצים המאושרים, ומתריעה על
 * סרטונים חדשים שטרם נראו. בריצה הראשונה רק "זוכרת" את הקיים (בלי התראות), כדי לא
 * להציף. נשמרת רשימת מזהים שנראו ב-SharedPreferences (מוגבלת בגודל).
 */
class NewVideoWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val settings = SettingsStore(ctx)
        if (!settings.newVideoNotifications) return Result.success()

        val channels = runCatching {
            ChannelsRepository.getChannels(ctx).forLevel(settings.filterLevel)
        }.getOrNull().orEmpty()
        if (channels.isEmpty()) return Result.success()

        val videos = runCatching { YouTubeRepository.fetchAllChannelsFeed(channels) }.getOrNull().orEmpty()
        if (videos.isEmpty()) return Result.success()

        val prefs = ctx.getSharedPreferences("ft_notify", Context.MODE_PRIVATE)
        val seen = prefs.getStringSet(KEY_SEEN, emptySet())?.toMutableSet() ?: mutableSetOf()
        val firstRun = seen.isEmpty()
        val fresh = videos.filter { it.id !in seen }

        seen.addAll(videos.map { it.id })
        val capped = if (seen.size > SEEN_CAP) seen.toList().takeLast(SEEN_CAP).toSet() else seen
        prefs.edit().putStringSet(KEY_SEEN, capped).apply()

        if (firstRun || fresh.isEmpty()) return Result.success()   // אין התראות בריצה הראשונה
        notifyNew(ctx, fresh)
        return Result.success()
    }

    private fun notifyNew(ctx: Context, fresh: List<Video>) {
        ensureChannel(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val title: String
        val text: String
        val deepLinkId: String?
        if (fresh.size == 1) {
            val v = fresh.first()
            title = "סרטון חדש: ${v.channelName}"; text = v.title; deepLinkId = v.id
        } else {
            title = "${fresh.size} סרטונים חדשים בערוצים שלך"
            text = fresh.take(3).joinToString(" · ") { it.channelName }; deepLinkId = null
        }

        val intent = Intent(ctx, com.filtertube.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            if (deepLinkId != null) data = Uri.parse("https://www.youtube.com/watch?v=$deepLinkId")
        }
        val pi = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        runCatching { nm.notify(NOTIF_ID, notif) }
    }

    private fun ensureChannel(ctx: Context) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "סרטונים חדשים", NotificationManager.IMPORTANCE_DEFAULT),
                )
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "new_videos"
        private const val NOTIF_ID = 4201
        private const val KEY_SEEN = "seen_ids"
        private const val SEEN_CAP = 3000
    }
}
