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
import java.util.concurrent.TimeUnit

/**
 * בדיקת עדכונים מול GitHub Releases.
 *
 * ## מספור הגרסאות
 * `versionCode` של כל APK שווה למספר הריצה של ה-workflow, וה-workflow אחד
 * לבניות יציבות ולבניות טסט גם יחד. לכן המספר תמיד עולה, ומי שנבנה מאוחר
 * יותר תמיד מקבל מספר גבוה יותר — כלומר גרסת טסט חדשה לעולם לא תיראה "ישנה"
 * מול גרסה יציבה. (בעבר היו שני workflows עם מונים נפרדים: טסט קיבל 6 בזמן
 * שהיציב היה 140, ולכן ערוץ הבדיקות הציע "עדכון" אחורה, שוב ושוב.)
 */
object UpdateChecker {

    // רשימת ה-Releases (לא /latest) — כי ב-repo יש גם Releases של גרסת Flutter,
    // ואנחנו צריכים את האחרון מסוג build-N / test-N דווקא.
    private const val LIST_URL =
        "https://api.github.com/repos/yeled-tov/filtertube-android/releases?per_page=100"

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class Update(
        /** מספר הבנייה — זהה ל-versionCode של ה-APK. */
        val build: Int,
        /** הגרסה השיווקית, למשל "1.1.0". */
        val versionName: String,
        /** שורות "מה השתנה", כבר מנוקות מסימוני Markdown. */
        val changes: List<String>,
        val apkUrl: String?,
        val isTestBuild: Boolean = false,
    ) {
        val isNewer: Boolean get() = build > BuildConfig.VERSION_CODE

        /** "גרסה 1.1.0 (בנייה 142)" — מוכן להצגה. */
        val displayName: String
            get() = buildString {
                append("גרסה ").append(versionName)
                append(" (בנייה ").append(build).append(')')
                if (isTestBuild) append(" · בדיקה")
            }
    }

    /**
     * מחפש את הגרסה האחרונה.
     *
     * [includeTestBuilds] = false (ברירת המחדל, וכל הלקוחות): רק Release יציב.
     * Pre-release נדחה, כדי שגרסאות בדיקה לא יגיעו בטעות ללקוחות.
     */
    suspend fun check(includeTestBuilds: Boolean = false): Update? = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(LIST_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "FilterTube")
            .build()
        runCatching {
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val arr = org.json.JSONArray(resp.body?.string() ?: return@use null)
                var latest: Update? = null
                for (i in 0 until arr.length()) {
                    val json = arr.optJSONObject(i) ?: continue
                    if (json.optBoolean("draft")) continue
                    val prerelease = json.optBoolean("prerelease")
                    if (prerelease && !includeTestBuilds) continue
                    val tag = json.optString("tag_name")
                    val prefix = when {
                        tag.startsWith("build-") -> "build-"
                        tag.startsWith("test-") && includeTestBuilds -> "test-"
                        else -> continue                    // מתעלמים מגרסת Flutter ומכל השאר
                    }
                    val build = tag.removePrefix(prefix).toIntOrNull() ?: continue
                    if (build <= (latest?.build ?: -1)) continue

                    val assetName = if (prefix == "test-") "FilterTube-test.apk" else "FilterTube.apk"
                    var apkUrl: String? = null
                    json.optJSONArray("assets")?.let { assets ->
                        for (j in 0 until assets.length()) {
                            val a = assets.optJSONObject(j) ?: continue
                            if (a.optString("name") == assetName) {
                                apkUrl = a.optString("browser_download_url"); break
                            }
                        }
                    }
                    val title = json.optString("name", tag)
                    val body = json.optString("body", "").trim()
                    latest = Update(
                        build = build,
                        versionName = extractVersionName(title, body),
                        changes = parseChanges(body),
                        apkUrl = apkUrl,
                        isTestBuild = prefix == "test-",
                    )
                }
                latest
            }
        }.getOrNull()
    }

    /** "גרסה 1.1.0 (בנייה 142)" → "1.1.0". אם אין — נופלים למספר הבנייה בלבד. */
    private fun extractVersionName(title: String, body: String): String =
        Regex("""\d+\.\d+(\.\d+)?""").find(title)?.value
            ?: Regex("""\d+\.\d+(\.\d+)?""").find(body)?.value
            ?: ""

    /**
     * מוציא מגוף ה-Release את שורות "מה השתנה" בלבד.
     *
     * נעצר בקו המפריד (`---`), כי אחריו מגיעות הוראות ההתקנה — שהמשתמש כבר
     * נמצא בתוך האפליקציה ולא צריך לקרוא אותן בחלון העדכון.
     */
    private fun parseChanges(body: String): List<String> =
        body.lineSequence()
            .takeWhile { !it.trimStart().startsWith("---") }
            .mapNotNull { line ->
                val t = line.trim()
                if (t.startsWith("- ") || t.startsWith("* ")) t.drop(2).trim() else null
            }
            .map { it.replace("`", "").replace("**", "").replace("__", "").trim() }
            .filter { it.isNotBlank() }
            .take(15)
            .toList()

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
