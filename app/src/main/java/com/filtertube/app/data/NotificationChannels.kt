package com.filtertube.app.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * ערוצי ההתראות של האפליקציה.
 *
 * ## למה זה חייב לרוץ בעליית האפליקציה
 * מאנדרואיד 8 ומעלה, התראה שמפנה לערוץ שלא קיים **נזרקת בשקט** — בלי
 * שגיאה ובלי שום סימן. התראת FCM מגיעה גם כשהאפליקציה סגורה, ולכן הערוץ
 * חייב להיות קיים מראש ולא להיווצר במסך כלשהו שאולי לא נפתח מעולם.
 *
 * ## למה ערוץ חדש ולא שדרוג של הקיים
 * את חשיבות הערוץ אי אפשר להעלות אחרי שנוצר — המערכת מכבדת את מה שהמשתמש
 * רואה בהגדרות. ערוץ קיים ב-IMPORTANCE_DEFAULT יישאר כזה לנצח, וזה בדיוק
 * ההבדל בין התראה שקופצת מיד לבין אחת שממתינה עד שהמכשיר מתעורר מעצמו.
 */
object NotificationChannels {

    /** בקשות שממתינות לאדמין — חייבות להגיע מיד, גם בחיסכון בסוללה. */
    const val ADMIN_ALERTS = "admin_alerts"

    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(ADMIN_ALERTS) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                ADMIN_ALERTS,
                "בקשות ממתינות",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "בקשות ערוץ ובקשות פרימיום שממתינות לאישור"
                enableVibration(true)
            },
        )
    }
}
