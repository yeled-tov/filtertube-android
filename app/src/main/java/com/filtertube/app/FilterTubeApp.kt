package com.filtertube.app

import android.app.Application
import com.filtertube.app.data.CrashLog
import com.filtertube.app.data.NewPipeDownloader
import com.filtertube.app.data.RemoteConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization

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
    }
}
