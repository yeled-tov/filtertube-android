package com.filtertube.filtertube

import android.os.Build
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

/**
 * קצב רענון גבוה — אותו מימוש בדיוק כמו באפליקציית האנדרואיד הראשית.
 *
 * ## למה בקוד מקורי ולא דרך פלאגין
 * הפלאגין שעושה את זה (flutter_displaymode) אינו מתוחזק, הוא עדיין מכריז
 * על compileSdk 33, וזה מפיל את הבנייה מול ספריות AndroidX עדכניות. מה
 * שהוא עושה בפועל הוא שלוש שורות של API מערכת, ואין סיבה לשלם עליהן
 * בתלות חיצונית שחוסמת עדכוני SDK.
 */
class MainActivity : FlutterActivity() {
    private companion object {
        const val CHANNEL = "filtertube/display"
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "setHighRefreshRate" -> {
                        applyHighRefreshRate()
                        result.success(null)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    /** בוחר את מצב התצוגה עם קצב הרענון הגבוה ביותר באותה רזולוציה. */
    @Suppress("DEPRECATION")
    private fun applyHighRefreshRate() {
        try {
            val display =
                (if (Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay)
                    ?: return
            val current = display.mode ?: return
            val best = display.supportedModes
                .filter {
                    it.physicalWidth == current.physicalWidth &&
                        it.physicalHeight == current.physicalHeight
                }
                .maxByOrNull { it.refreshRate } ?: return
            if (best.modeId != current.modeId) {
                window.attributes = window.attributes.apply {
                    preferredDisplayModeId = best.modeId
                }
            }
        } catch (_: Exception) {
            // בלי קצב גבוה — לא נורא. המסך פשוט נשאר בקצב ברירת המחדל שלו.
        }
    }
}
