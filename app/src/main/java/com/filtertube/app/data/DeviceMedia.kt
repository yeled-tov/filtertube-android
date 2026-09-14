package com.filtertube.app.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * המדיה שנמצאת על המכשיר עצמו — וידאו ואודיו — דרך MediaStore.
 *
 * ## למה זה כאן
 * ההורדות של FilterTube נשמרות לתיקייה בזיכרון הראשי, אבל על הטלפון יש עוד
 * הרבה מדיה: שירים שהועברו מהמחשב, הקלטות, סרטונים שהתקבלו בוואטסאפ. אין שום
 * סיבה שהאפליקציה תדע לנגן רק את מה שהיא עצמה הורידה.
 *
 * ## הרשאות
 * מאנדרואיד 13 יש הרשאות נפרדות לכל סוג מדיה (READ_MEDIA_AUDIO,
 * READ_MEDIA_VIDEO); לפני כן זו READ_EXTERNAL_STORAGE אחת. [requiredPermissions]
 * מחזיר את מה שצריך לבקש בפועל לפי גרסת המערכת.
 *
 * ## סינון
 * הסינון לרשימה הלבנה לא חל כאן, וזה מכוון: אלה קבצים שהמשתמש כבר החזיק
 * במכשיר שלו. FilterTube מסננת מה *נמשך מיוטיוב*, לא מה שכבר נמצא בטלפון.
 */
object DeviceMedia {

    /** קובץ מדיה מקומי אחד. */
    data class Item(
        val id: Long,
        val title: String,
        val artist: String,
        val uri: String,
        val durationSec: Long,
        val isVideo: Boolean,
        val folder: String,
    )

    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                android.Manifest.permission.READ_MEDIA_AUDIO,
                android.Manifest.permission.READ_MEDIA_VIDEO,
            )
        } else {
            arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    fun hasPermission(context: Context): Boolean = requiredPermissions().any { permission ->
        androidx.core.content.ContextCompat.checkSelfPermission(context, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    /**
     * כל האודיו והווידאו על המכשיר, החדשים ראשונים.
     *
     * [onlyFilterTube] מצמצם לתיקיית ההורדות של האפליקציה — זה מה שמוצג במצב
     * אופליין, כשהמשתמש רוצה את מה שהוא הוריד ולא את כל הטלפון.
     */
    suspend fun scan(context: Context, onlyFilterTube: Boolean = false): List<Item> =
        withContext(Dispatchers.IO) {
            if (!hasPermission(context)) return@withContext emptyList()
            val out = ArrayList<Item>()
            out += query(context, video = false, onlyFilterTube = onlyFilterTube)
            out += query(context, video = true, onlyFilterTube = onlyFilterTube)
            Diagnostics.log(
                "DEVICE MEDIA: ${out.count { !it.isVideo }} שירים, ${out.count { it.isVideo }} סרטונים" +
                    if (onlyFilterTube) " (תיקיית ${DownloadEngine.FOLDER} בלבד)" else "",
            )
            out.sortedByDescending { it.id }
        }

    private fun query(context: Context, video: Boolean, onlyFilterTube: Boolean): List<Item> {
        val collection = if (video) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        // BUCKET_DISPLAY_NAME (שם התיקייה) קיים רק מאנדרואיד 10. בגרסאות
        // ישנות יותר העמודה פשוט לא קיימת בטבלה, ובקשה שלה מפילה את *כל*
        // השאילתה — לא רק את השדה הזה. לכן היא נוספת מותנית.
        val hasBucket = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val extra = if (video) MediaStore.Video.Media.TITLE else MediaStore.Audio.Media.ARTIST
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.DURATION)
            add(extra)
            if (hasBucket) add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        }.toTypedArray()
        // מסננים קבצים זעירים: צלילי מערכת והתראות מציפים את הרשימה בלי סיבה.
        val selection = "${MediaStore.MediaColumns.DURATION} >= 20000"

        val items = ArrayList<Item>()
        runCatching {
            context.contentResolver.query(
                collection, projection, selection, null,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
                val extraCol = cursor.getColumnIndex(extra)
                val bucketCol = if (hasBucket) cursor.getColumnIndex("bucket_display_name") else -1
                while (cursor.moveToNext()) {
                    val folder = if (bucketCol >= 0) cursor.getString(bucketCol).orEmpty() else ""
                    if (onlyFilterTube && !folder.equals(DownloadEngine.FOLDER, ignoreCase = true)) continue
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol).orEmpty()
                    items += Item(
                        id = id,
                        title = name.substringBeforeLast('.').ifBlank { name },
                        artist = if (extraCol >= 0) cursor.getString(extraCol).orEmpty() else "",
                        uri = ContentUris.withAppendedId(collection, id).toString(),
                        durationSec = cursor.getLong(durCol) / 1000,
                        isVideo = video,
                        folder = folder,
                    )
                }
            }
        }.onFailure { Diagnostics.log("DEVICE MEDIA: סריקה נכשלה — ${it.message}") }
        return items
    }

}

/**
 * ממיר לפריט ניגון רגיל, כדי שהנגן הקיים ינגן אותו בלי מסלול נפרד.
 *
 * מוגדר ברמת הקובץ ולא כחבר של [DeviceMedia]: פונקציית הרחבה שמוגדרת בתוך
 * object אי אפשר לקרוא מחוץ ל-object הזה.
 */
fun DeviceMedia.Item.toVideo(): Video = Video(
    id = "local:$id",
    title = title,
    channelName = artist.ifBlank { folder },
    channelId = "",
    thumbnailUrl = "",
    publishedAt = 0L,
    durationSec = durationSec,
    localUri = uri,
)
