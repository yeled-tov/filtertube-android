package com.filtertube.app.data

/**
 * פארסר אמין לפורמט ISO 8601 Duration של יוטיוב (למשל: PT4M32S, PT1H12M8S, PT45S).
 */
object IsoDurationParser {

    private val regex = Regex("""PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?""")

    fun parseToSeconds(isoDuration: String?): Long {
        if (isoDuration.isNullOrBlank()) return 0L
        val match = regex.find(isoDuration) ?: return 0L
        val hours = match.groupValues[1].toLongOrNull() ?: 0L
        val minutes = match.groupValues[2].toLongOrNull() ?: 0L
        val seconds = match.groupValues[3].toLongOrNull() ?: 0L
        return hours * 3600L + minutes * 60L + seconds
    }
}
