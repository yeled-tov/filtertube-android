package com.filtertube.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.filtertube.app.data.CrashLog
import com.filtertube.app.data.NewPipeDownloader
import com.filtertube.app.data.NewVideoWorker
import com.filtertube.app.data.RemoteConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization
import java.util.concurrent.TimeUnit

class FilterTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)   // ← לוכד קריסות לפני הכל
        NewPipe.init(
            NewPipeDownloader.getInstance(),
            Localization("he", "IL"),
        )
        // מושכים את קונפיג הענן בהפעלה — "עדכון API בלי קוד" (נופל לברירת מחדל אם נכשל)
        CoroutineScope(Dispatchers.IO).launch { RemoteConfig.refresh() }

        // בדיקת רקע תקופתית להתראות על סרטון חדש בערוץ מאושר (כל ~6 שעות)
        val notifyWork = PeriodicWorkRequestBuilder<NewVideoWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork("ft_new_videos", ExistingPeriodicWorkPolicy.KEEP, notifyWork)
    }
}
