package com.filtertube.app.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * מצב "אהבתי" ו"נצפה" לכל הסרטונים, כ-Compose state משותף.
 *
 * למה לא לשאול את [LibraryStore] בכל שורה: כל קריאה שם פותחת SharedPreferences
 * ומפענחת JSON שלם. ברשימה עם מאות שורות זה מגמגם. כאן נטענת קבוצת מזהים אחת
 * לכל מסך, וכל השורות קוראות ממנה בזיכרון.
 */
object LibraryBadges {

    var liked by mutableStateOf<Set<String>>(emptySet())
        private set

    var watched by mutableStateOf<Set<String>>(emptySet())
        private set

    /** טוען מחדש מהאחסון המקומי. בטוח לקרוא בכל כניסה למסך. */
    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        val store = LibraryStore(context)
        val likedIds = runCatching { store.likedIds() }.getOrDefault(emptySet())
        val watchedIds = runCatching { store.watchedIds() }.getOrDefault(emptySet())
        withContext(Dispatchers.Main) {
            liked = likedIds
            watched = watchedIds
        }
    }

    /** עדכון מיידי אחרי לחיצה על "אהבתי", בלי לחכות לטעינה מחדש. */
    fun setLiked(videoId: String, value: Boolean) {
        if (videoId.isBlank()) return
        liked = if (value) liked + videoId else liked - videoId
    }

    fun markWatched(videoId: String) {
        if (videoId.isBlank()) return
        watched = watched + videoId
    }

    fun clear() {
        liked = emptySet()
        watched = emptySet()
    }
}
