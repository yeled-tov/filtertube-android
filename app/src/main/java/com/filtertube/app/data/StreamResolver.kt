package com.filtertube.app.data

/**
 * מנוע חילוץ/פתרון כתובות ניגון (Stream Resolver) של וידאו.
 */
interface StreamResolver {
    val name: String

    /**
     * מנסה לחלץ מידע וזרמי ניגון עבור [videoId].
     * מחזיר [StreamData] תקין ומאומת בלבד, או null אם הניסיון נכשל.
     *
     * [force] מדלג על בדיקת ה-cooldown. זה מסלול המוצא האחרון: כשכל המנועים
     * בצינון בו-זמנית, "לא לנסות כלום" הוא התוצאה הגרועה ביותר האפשרית —
     * המשתמש מקבל כישלון תוך אפס מילישניות ושום סרטון לא מתנגן. עדיף לנסות
     * מנוע שנכשל לאחרונה מאשר לא לנסות בכלל.
     */
    suspend fun resolve(videoId: String, force: Boolean = false): StreamData?
}
