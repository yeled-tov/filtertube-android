package com.filtertube.app

import android.app.Application
import com.filtertube.app.data.NewPipeDownloader
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization

/**
 * Application class - מאותחל פעם אחת בפתיחת האפליקציה.
 * מאתחל את NewPipeExtractor.
 */
class FilterTubeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(
            NewPipeDownloader.getInstance(),
            Localization("he", "IL"),
        )
    }
}
