package com.filtertube.app.data

/**
 * מנוע חילוץ/פתרון כתובות ניגון (Stream Resolver) של וידאו.
 */
interface StreamResolver {
    val name: String

    /**
     * מנסה לחלץ מידע וזרמי ניגון עבור [videoId].
     * מחזיר [StreamData] תקין ומאומת בלבד, או null אם הניסיון נכשל.
     */
    suspend fun resolve(videoId: String): StreamData?
}
