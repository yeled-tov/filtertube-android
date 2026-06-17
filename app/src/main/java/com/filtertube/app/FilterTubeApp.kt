package com.filtertube.app

import android.app.Application
import com.filtertube.app.data.CrashLog
import com.filtertube.app.data.NewPipeDownloader
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
    }
}
