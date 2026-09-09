package com.filtertube.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * האם יש חיבור אינטרנט שימושי כרגע.
 *
 * הבדיקה היא על NET_CAPABILITY_VALIDATED ולא רק על "יש רשת": רשת Wi-Fi
 * שמחוברת לנתב בלי אינטרנט מדווחת כמחוברת, וזה בדיוק המצב שבו האפליקציה
 * נראית תקועה במקום להגיד שאין חיבור.
 */
object Connectivity {

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true // בלי המנהל אין דרך לדעת — עדיף להניח שיש רשת
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
