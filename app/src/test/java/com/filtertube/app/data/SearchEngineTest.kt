package com.filtertube.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * בדיקות להתאמת שמות ערוצים בעברית.
 *
 * הרגרסיה שנבדקת כאן: החיפוש החזיר "לא נמצאו תוצאות" גם כשהערוץ המבוקש היה
 * ברשימה המאושרת, כי הוא הסתמך על חיפוש גלובלי ולא ניסה להתאים שם ערוץ ישירות.
 */
class SearchEngineTest {

    private val channels = listOf(
        Channel("UC1", "מוטי שטיינמץ", "music"),
        Channel("UC2", "אברהם פריד", "music"),
        Channel("UC3", "שיעורי תורה — הרב זמיר כהן", "torah"),
    )

    @Test
    fun findsChannelByExactName() = runBlocking {
        val hits = SearchEngine.channelSuggestions(channels, "מוטי שטיינמץ")
        assertEquals(listOf("מוטי שטיינמץ"), hits)
    }

    @Test
    fun findsChannelByPartialName() = runBlocking {
        val hits = SearchEngine.channelSuggestions(channels, "פריד")
        assertEquals(listOf("אברהם פריד"), hits)
    }

    @Test
    fun ignoresPunctuationAndApostropheVariants() = runBlocking {
        // גרש/מקף/רווח כפול לא צריכים לשבור התאמה
        val hits = SearchEngine.channelSuggestions(channels, "זמיר  כהן")
        assertTrue(hits.any { it.contains("זמיר כהן") })
    }

    @Test
    fun tooShortQueryReturnsNothing() = runBlocking {
        assertEquals(emptyList<String>(), SearchEngine.channelSuggestions(channels, "מ"))
    }

    @Test
    fun unknownChannelReturnsNothing() = runBlocking {
        assertEquals(emptyList<String>(), SearchEngine.channelSuggestions(channels, "ערוץ שלא קיים"))
    }
}
