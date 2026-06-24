package com.filtertube.app.data

/**
 * התאמת מסך הבית להעדפות המשתמש — לפי היסטוריית הצפייה המקומית, ובתוך הרשימה הלבנה
 * בלבד (לא שוברים את הסינון). כל צפייה בערוץ "מקדמת" את סרטוניו בדירוג, אבל מבלי לקבור
 * לגמרי תוכן טרי מערוצים אחרים — שילוב של טריות (publishedAt) ואהדה (כמה צפית בערוץ).
 */
fun personalizeFeed(videos: List<Video>, history: List<Video>): List<Video> {
    if (history.isEmpty()) return videos
    val affinity = history.groupingBy { it.channelId }.eachCount()
    val bonusMs = 2L * 24 * 3600 * 1000   // כל צפייה בערוץ ≈ יומיים "קידום" בדירוג
    return videos.sortedByDescending { it.publishedAt + (affinity[it.channelId] ?: 0) * bonusMs }
}
