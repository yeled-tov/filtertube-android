package com.filtertube.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth

/**
 * בדיקת רקע תקופתית (WorkManager) — מושכת את הפיד של הערוצים המאושרים, ומתריעה על
 * סרטונים חדשים שטרם נראו. בריצה הראשונה רק "זוכרת" את הקיים (בלי התראות), כדי לא
 * להציף. נשמרת רשימת מזהים שנראו ב-SharedPreferences (מוגבלת בגודל).
 */
class NewVideoWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val firebaseUser = FirebaseAuth.getInstance().currentUser
            ?.takeIf { it.isEmailVerified }
            ?: return Result.success()
        val expectedUid = firebaseUser.uid
        val generation = AccountDataGuard.generation()
        fun sessionCurrent(): Boolean {
            val current = FirebaseAuth.getInstance().currentUser
            return current?.uid == expectedUid &&
                current.isEmailVerified &&
                AccountDataGuard.generation() == generation
        }
        val settings = SettingsStore(ctx)
        if (!settings.newVideoNotifications) return Result.success()

        val store = LibraryStore(ctx)
        val snapshot = AccountDataGuard.withLock {
            if (!sessionCurrent()) return@withLock null
            settings.filterLevel to store.localSubscriptions()
        } ?: return Result.success()
        val all = runCatching {
            ChannelsRepository.getChannels(ctx).forLevel(snapshot.first)
        }.getOrNull().orEmpty()
        if (!sessionCurrent()) return Result.success()
        if (all.isEmpty()) return Result.success()

        // אם יש מנויים — מצמצמים אליהם בלבד; אחרת כל הערוצים המאושרים
        val subs = snapshot.second
        val channels = if (subs.isEmpty()) all else all.filter { it.youtubeChannelId in subs }
        if (channels.isEmpty()) return Result.success()

        val videos = runCatching { YouTubeRepository.fetchAllChannelsFeed(channels) }.getOrNull().orEmpty()
        if (!sessionCurrent()) return Result.success()
        if (videos.isEmpty()) return Result.success()

        val prefs = ctx.getSharedPreferences("ft_notify", Context.MODE_PRIVATE)
        val seenKey = "$KEY_SEEN_PREFIX$expectedUid"
        val fresh = AccountDataGuard.withLock {
            if (!sessionCurrent()) return@withLock null
            val seen =
                prefs.getStringSet(seenKey, emptySet())?.toMutableSet()
                    ?: mutableSetOf()
            val firstRun = seen.isEmpty()
            val unseen = videos.filter { it.id !in seen }
            seen.addAll(videos.map { it.id })
            val capped =
                if (seen.size > SEEN_CAP) seen.toList().takeLast(SEEN_CAP).toSet()
                else seen
            prefs.edit()
                .remove(KEY_SEEN_LEGACY)
                .putStringSet(seenKey, capped)
                .apply()
            if (firstRun || unseen.isEmpty()) return@withLock emptyList()
            store.addNewVideos(unseen)
            notifyNew(ctx, unseen)
            unseen
        } ?: return Result.success()
        if (fresh.isEmpty()) return Result.success()
        return Result.success()
    }

    private fun notifyNew(ctx: Context, fresh: List<Video>) {
        ensureChannel(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val title: String
        val text: String
        if (fresh.size == 1) {
            val v = fresh.first()
            title = "סרטון חדש: ${v.channelName}"; text = v.title
        } else {
            title = "${fresh.size} סרטונים חדשים בערוצים שלך"
            text = fresh.take(3).joinToString(" · ") { it.channelName }
        }

        // לחיצה על ההתראה פותחת את מסך "סרטונים חדשים"
        val intent = Intent(ctx, com.filtertube.app.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("ft_open_inbox", true)
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
        private const val KEY_SEEN_LEGACY = "seen_ids"
        private const val KEY_SEEN_PREFIX = "seen_ids_"
        private const val SEEN_CAP = 3000
    }
}
