package com.filtertube.app.data

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * אבחון תקלות מהירות/עצירות — חוצץ-טבעת קטן של אירועים (טעינת זרם, איזה מקור ניצח,
 * כמה זמן, ועצירות/באפר במהלך הניגון). מאפשר לראות במדויק מה איטי/נתקע במקום לנחש.
 *
 * נגיש ממסך "אבחון" בהגדרות; אפשר לשלוח אליי דרך אותו מנגנון של דיווח באגים.
 */
object Diagnostics {

    private const val CAP = 100
    private val entries = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)

    @Synchronized
    fun log(msg: String) {
        entries.addLast("${fmt.format(Date())}  $msg")
        while (entries.size > CAP) entries.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<String> = entries.toList().asReversed()   // החדש למעלה

    @Synchronized
    fun text(): String = entries.toList().asReversed().joinToString("\n")

    @Synchronized
    fun clear() = entries.clear()
}
