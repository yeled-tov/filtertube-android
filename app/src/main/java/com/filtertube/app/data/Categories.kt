package com.filtertube.app.data

/** סדר תצוגה מועדף לטאבים בדף הבית; קטגוריות לא מוכרות נדחפות לסוף. */
private val categoryOrder = listOf("torah", "music", "kids", "diy", "cooking", "dati_light", "news", "general")

/** תווית עברית לקטגוריה (משתמש במפה המשותפת [categoryLabels]). */
fun categoryLabelHe(cat: String): String = categoryLabels[cat] ?: cat

/** מחזיר את הקטגוריות הקיימות, ממוינות לפי סדר התצוגה המועדף. */
fun sortedCategories(cats: Collection<String>): List<String> =
    cats.distinct().sortedBy { categoryOrder.indexOf(it).let { i -> if (i < 0) categoryOrder.size else i } }
