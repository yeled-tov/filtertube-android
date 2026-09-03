package com.filtertube.app.data

/**
 * פארסר מרכזי ויחיד לחילוץ videoId של יוטיוב מכל סוגי הקישורים הנפוצים.
 */
object YouTubeUrlParser {

    private val patterns = listOf(
        Regex("""(?:v=|v/|embed/|shorts/|live/|youtu\.be/)([A-Za-z0-9_-]{11})"""),
        Regex("""[?&]v=([A-Za-z0-9_-]{11})""")
    )

    fun extractVideoId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        for (pattern in patterns) {
            pattern.find(url)?.let {
                val id = it.groupValues[1]
                if (id.length == 11) return id
            }
        }
        return null
    }
}
