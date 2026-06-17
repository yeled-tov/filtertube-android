package com.filtertube.app.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import com.filtertube.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * בדיקת עדכונים מול GitHub Releases. כל build ב-CI יוצר Release עם תג build-N,
 * וה-versionCode של ה-APK שווה ל-N. כך אפשר להשוות גרסה מותקנת מול האחרונה.
 */
object UpdateChecker {

    private const val LATEST_URL = "https://api.github.com/repos/yeled-tov/filtertube-android/releases/latest"

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class Update(
        val build: Int,
        val name: String,
        val changelog: String,
        val apkUrl: String?,
    ) {
        val isNewer: Boolean get() = build > BuildConfig.VERSION_CODE
    }

    suspend fun check(): Update? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "FilterTube")
            .build()
        runCatching {
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val json = JSONObject(resp.body?.string() ?: return@use null)
                val tag = json.optString("tag_name")                 // build-N
                val build = tag.substringAfterLast("build-", "").toIntOrNull() ?: return@use null
                var apkUrl: String? = null
                json.optJSONArray("assets")?.let { assets ->
                    for (i in 0 until assets.length()) {
                        val a = assets.optJSONObject(i) ?: continue
                        if (a.optString("name").endsWith(".apk", true)) {
                            apkUrl = a.optString("browser_download_url"); break
                        }
                    }
                }
                Update(
                    build = build,
                    name = json.optString("name", tag),
                    changelog = json.optString("body", "").trim(),
                    apkUrl = apkUrl,
                )
            }
        }.getOrNull()
    }

    /** מוריד את ה-APK; בסיום ההתראה במערכת מאפשרת להתקין. */
    fun downloadApk(context: Context, url: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("עדכון FilterTube")
                setDescription("מוריד את הגרסה החדשה")
                setMimeType("application/vnd.android.package-archive")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "FilterTube-update.apk")
            }
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(context, "ההורדה התחילה — לחץ על ההתראה כדי להתקין", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, "שגיאה בהורדה: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
