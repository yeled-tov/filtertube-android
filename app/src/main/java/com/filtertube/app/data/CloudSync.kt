package com.filtertube.app.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * שכבת סנכרון ראשונית למעבר עתידי ל-Firebase/Firestore.
 * כרגע היא שומרת את הפרופיל המקומית ומוכנה לשילוב ענן בהמשך.
 */
object CloudSync {
    private const val TAG = "CloudSync"

    suspend fun syncUserProfile(settings: SettingsStore): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            Log.d(TAG, "syncUserProfile: name=${settings.userName}, email=${settings.userEmail}, gender=${settings.userGender}")
            true
        }.getOrDefault(false)
    }
}
