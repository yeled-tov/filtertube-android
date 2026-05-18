package com.filtertube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * חיבור ישיר ל-REST API של Supabase.
 *
 * ה-anon key הוא public ומוגן ע"י RLS (Row Level Security) ב-Supabase —
 * משתמשים אנונימיים יכולים רק לקרוא מטבלת `channels`.
 */
object SupabaseClient {
    private const val SUPABASE_URL = "https://blpeuwwbmtrmydzzkgal.supabase.co"
    private const val ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
        "eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJscGV1d3dibXRybXlkenprZ2FsIiwicm9sZSI6ImFub24i" +
        "LCJpYXQiOjE3NzgxODE5MzksImV4cCI6MjA5Mzc1NzkzOX0." +
        "eChiLj1vvl7d9FMSeS3v9VIcA5TWuj2DLfDqSPaRY9k"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * מושך את כל הערוצים המאושרים מ-Supabase, ממוין לפי שם.
     * @throws IOException אם הבקשה נכשלת
     */
    suspend fun fetchChannels(): List<Channel> = withContext(Dispatchers.IO) {
        val url = "$SUPABASE_URL/rest/v1/channels" +
            "?select=id,youtube_channel_id,name,category" +
            "&order=name.asc"

        val request = Request.Builder()
            .url(url)
            .header("apikey", ANON_KEY)
            .header("Authorization", "Bearer $ANON_KEY")
            .header("Accept", "application/json")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Supabase HTTP ${response.code}: ${response.message}")
            }
            val body = response.body?.string() ?: "[]"
            json.decodeFromString<List<Channel>>(body)
        }
    }
}
