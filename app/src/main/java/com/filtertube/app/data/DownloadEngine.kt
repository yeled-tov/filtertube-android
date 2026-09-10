package com.filtertube.app.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/** פריט בתור ההורדות — מצב והתקדמות נצפים ע"י מסך מנהל ההורדות. */
class DownloadTask(val video: Video, val isAudio: Boolean) {
    var progress by mutableStateOf(0)        // 0..100
    var status by mutableStateOf("ממתין")    // ממתין / מוריד / הושלם / נכשל
}

/**
 * מנוע הורדות מהיר: מוריד כל קובץ ב-**מספר חיבורים מקבילים** (Range requests) כדי לעקוף את
 * חניקת ה-CDN של יוטיוב, ומריץ כמה קבצים במקביל. נשמר ל"הורדות" הציבוריות.
 */
object DownloadEngine {

    /** שם התיקייה בזיכרון הראשי שאליה נשמרות כל ההורדות. */
    const val FOLDER = "FilterTube"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** רשימת ההורדות הפעילות/האחרונות — נצפית במסך מנהל ההורדות (Compose). */
    val active = mutableStateListOf<DownloadTask>()

    private data class Spec(
        val url: String, val fileName: String, val ua: String?,
        val connections: Int, val isAudio: Boolean, val context: Context,
        /**
         * זרם האודיו הנפרד, כשמורידים וידאו בפורמט DASH.
         *
         * יוטיוב כמעט לא מגיש היום זרמים משולבים, ולכן זה המסלול הרגיל
         * לווידאו: מורידים שני קבצים וממזגים אותם ל-MP4 אחד ב-Mp4Muxer.
         */
        val audioUrl: String? = null,
    )

    private val queue = ArrayDeque<Pair<DownloadTask, Spec>>()
    private var running = 0
    private var maxConcurrent = 3

    /**
     * [audioUrl] — זרם אודיו נפרד, כשמורידים וידאו בפורמט DASH. כשהוא לא
     * null, שני הזרמים יורדים וממוזגים לקובץ MP4 אחד.
     */
    @Synchronized
    fun enqueue(
        context: Context,
        video: Video,
        url: String,
        isAudio: Boolean,
        userAgent: String?,
        audioUrl: String? = null,
    ) {
        val ctx = context.applicationContext
        val settings = SettingsStore(ctx)
        maxConcurrent = settings.concurrentDownloads
        val task = DownloadTask(video, isAudio)
        active.add(0, task)
        while (active.size > 60) active.removeAt(active.lastIndex)
        queue.addLast(
            task to Spec(
                url = url,
                fileName = fileName(video.title, isAudio),
                ua = userAgent,
                connections = settings.connectionsPerDownload,
                isAudio = isAudio,
                context = ctx,
                audioUrl = audioUrl,
            ),
        )
        pump()
    }

    /**
     * מחלץ את הזרם של [video] (זרם משולב עם קול) ומוסיף אותו לתור.
     *
     * במצב אודיו בלבד הבקשה מומרת לאודיו גם אם המבקש ביקש וידאו. אחרת המצב
     * לא היה שווה כלום: אפשר היה להאזין בלבד, אבל להוריד את הווידאו המלא
     * ולצפות בו מגלריית המכשיר.
     */
    suspend fun enqueueByVideo(context: Context, video: Video, isAudio: Boolean): Boolean {
        @Suppress("NAME_SHADOWING")
        val isAudio = isAudio || SettingsStore(context).audioOnlyMode
        val data = runCatching { StreamRepository.getStream(video.id) }.getOrNull() ?: return false
        val v = video.copy(
            title = data.title.ifBlank { video.title },
            channelName = data.uploaderName.ifBlank { video.channelName },
            thumbnailUrl = data.thumbnailUrl ?: video.thumbnailUrl,
        )
        if (isAudio) {
            enqueue(context, v, data.bestAudioUrl ?: data.bestVideoUrl, true, data.streamUserAgent)
            return true
        }
        val track = data.bestDownloadableVideo() ?: return false
        enqueue(context, v, track.videoUrl, false, data.streamUserAgent, track.audioUrl)
        return true
    }

    @Synchronized
    private fun pump() {
        while (running < maxConcurrent && queue.isNotEmpty()) {
            val (task, spec) = queue.removeFirst()
            running++
            scope.launch {
                suspend fun attempt(s: Spec): String {
                    task.status = "מוריד"
                    val uri = downloadFile(s, task)
                    // רק עכשיו הסרטון באמת זמין לניגון מקומי.
                    LibraryStore(s.context).setDownloadLocalUri(task.video.id, uri)
                    task.progress = 100; task.status = "הושלם"
                    Diagnostics.log("DOWNLOAD ${task.video.id}: נשמר ב-$uri")
                    return uri
                }
                runCatching { attempt(spec) }.onFailure { first ->
                    // ── ניסיון שני עם כתובת טרייה ────────────────────────
                    // כתובות googlevideo חתומות ופגות. הורדה שממתינה בתור
                    // מאחורי שתי הורדות אחרות יכולה לצאת לדרך אחרי שהכתובת
                    // שנשמרה כבר מתה — וזה נראה למשתמש כמו "ההורדה נכשלה",
                    // בלי שום דבר שבור באמת. חילוץ מחדש פותר את זה.
                    Diagnostics.log("DOWNLOAD ${task.video.id}: ${first.message} — מחלץ כתובת טרייה ומנסה שוב")
                    task.status = "מנסה שוב"
                    val retried = runCatching {
                        val fresh = StreamRepository.resolveFresh(task.video.id, null)
                        val refreshed = if (spec.isAudio) {
                            spec.copy(url = fresh.bestAudioUrl ?: fresh.bestVideoUrl, ua = fresh.streamUserAgent, audioUrl = null)
                        } else {
                            val track = fresh.bestDownloadableVideo()
                                ?: throw IllegalStateException("אין איכות וידאו שניתן להוריד")
                            spec.copy(url = track.videoUrl, ua = fresh.streamUserAgent, audioUrl = track.audioUrl)
                        }
                        attempt(refreshed)
                    }
                    if (retried.isFailure) {
                        task.status = "נכשל"
                        Diagnostics.log(
                            "DOWNLOAD ${task.video.id}: נכשל — ${retried.exceptionOrNull()?.message}",
                        )
                    }
                }
                synchronized(this@DownloadEngine) { running--; pump() }
            }
        }
    }

    private fun fileName(title: String, isAudio: Boolean): String {
        val safe = title.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().take(60).ifEmpty { "filtertube" }
        return "$safe.${if (isAudio) "m4a" else "mp4"}"
    }

    private suspend fun downloadFile(spec: Spec, task: DownloadTask): String {
        val tmp = File(spec.context.cacheDir, "ft_dl_${System.nanoTime()}.tmp")
        val audioTmp = File(spec.context.cacheDir, "ft_dl_${System.nanoTime()}_a.tmp")
        try {
            fetch(spec, spec.url, tmp, task)

            // ── וידאו בפורמט DASH ──────────────────────────────────────────
            // הזרם שהורדנו הוא וידאו בלבד. מורידים גם את האודיו וממזגים,
            // אחרת מתקבל קובץ אילם.
            val audio = spec.audioUrl
            if (audio != null) {
                task.status = "מוריד קול"
                fetch(spec.copy(connections = 1), audio, audioTmp, task)
                task.status = "ממזג"
                val merged = File(spec.context.cacheDir, "ft_dl_${System.nanoTime()}_m.mp4")
                try {
                    Mp4Muxer.combine(tmp, audioTmp, merged)
                    return publish(spec, merged)
                } finally {
                    runCatching { merged.delete() }
                }
            }

            return publish(spec, tmp)
        } finally {
            runCatching { tmp.delete() }
            runCatching { audioTmp.delete() }
        }
    }

    /**
     * מוריד כתובת אחת אל [dest], עם ריבוי חיבורים אם השרת תומך.
     *
     * ## הבדיקה המקדימה היא GET עם Range, לא HEAD
     * שרתי googlevideo מחזירים 403 לבקשות HEAD. הבדיקה נכשלה תמיד, len נשאר
     * ‎-1, וההורדה נפלה למסלול חיבור בודד בלי לדעת את הגודל — ואז גם
     * ההורדה עצמה קיבלה 403. בקשת `Range: bytes=0-0` מוחזרת 206 עם הכותרת
     * Content-Range, וממנה מקבלים גם את הגודל המלא וגם אישור שהשרת תומך
     * ב-Range — בבקשה אחת שהשרת באמת עונה לה.
     */
    private suspend fun fetch(spec: Spec, url: String, dest: File, task: DownloadTask) {
        val probe = Request.Builder().url(url)
            .apply { spec.ua?.let { header("User-Agent", it) } }
            .header("Range", "bytes=0-0").build()
        var len = -1L; var ranges = false
        runCatching {
            http.newCall(probe).execute().use { r ->
                // "bytes 0-0/12345678" — האורך המלא הוא מה שאחרי הלוכסן.
                val contentRange = r.header("Content-Range")
                if (r.code == 206 && contentRange != null) {
                    len = contentRange.substringAfterLast('/').toLongOrNull() ?: -1L
                    ranges = true
                } else if (r.isSuccessful) {
                    len = r.header("Content-Length")?.toLongOrNull() ?: -1L
                    ranges = r.header("Accept-Ranges")?.contains("bytes", true) == true
                }
            }
        }
        val urlSpec = spec.copy(url = url)
        if (len > 0 && ranges && spec.connections > 1) multiConn(urlSpec, dest, len, task)
        else single(urlSpec, dest, len, task)

        // קובץ ריק פירושו הורדה שנכשלה בשקט. עדיף להיכשל בקול מאשר לשמור
        // במכשיר קובץ שאי אפשר לנגן ולסמן אותו כ"הושלם".
        if (!dest.exists() || dest.length() == 0L) {
            throw IllegalStateException("ההורדה הסתיימה בקובץ ריק")
        }
    }

    private suspend fun multiConn(spec: Spec, tmp: File, len: Long, task: DownloadTask) {
        RandomAccessFile(tmp, "rw").use { it.setLength(len) }
        val n = spec.connections
        val chunk = len / n
        val done = AtomicLong(0)
        coroutineScope {
            (0 until n).map { idx ->
                async(Dispatchers.IO) {
                    val start = idx * chunk
                    val end = if (idx == n - 1) len - 1 else start + chunk - 1
                    val req = Request.Builder().url(spec.url)
                        .apply { spec.ua?.let { header("User-Agent", it) } }
                        .header("Range", "bytes=$start-$end").build()
                    http.newCall(req).execute().use { resp ->
                        // 206 = Partial Content, מה שבקשת Range אמורה להחזיר.
                        // בלי הבדיקה הזו נתח כושל היה מותיר אזור אפסים בקובץ
                        // שכבר הוקצה מראש ב-setLength — קובץ בגודל הנכון,
                        // בלי תוכן.
                        if (!resp.isSuccessful) {
                            throw IllegalStateException("נתח ${idx + 1} החזיר ${resp.code}")
                        }
                        val body = resp.body ?: throw IllegalStateException("נתח ${idx + 1} ריק")
                        RandomAccessFile(tmp, "rw").use { raf ->
                            raf.seek(start)
                            val buf = ByteArray(128 * 1024)
                            body.byteStream().use { ins ->
                                while (true) {
                                    val read = ins.read(buf); if (read <= 0) break
                                    raf.write(buf, 0, read)
                                    task.progress = ((done.addAndGet(read.toLong()) * 100) / len).toInt().coerceIn(0, 100)
                                }
                            }
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private fun single(spec: Spec, tmp: File, len: Long, task: DownloadTask) {
        // Range פתוח ("מהבייט 0 ועד הסוף"). googlevideo מגיש בקשות כאלה
        // באופן רגיל, ובלעדיו הוא נוטה להחזיר 403 על אותה כתובת בדיוק.
        val req = Request.Builder().url(spec.url)
            .apply { spec.ua?.let { header("User-Agent", it) } }
            .header("Range", "bytes=0-").build()
        http.newCall(req).execute().use { resp ->
            // בדיקת סטטוס. בלעדיה תגובת 403 — שקורית כשכתובת הזרם פגה או
            // נקשרה ל-User-Agent אחר — נכתבה לקובץ כאילו הייתה מדיה, וההורדה
            // דווחה כ"הושלם". זה בדיוק הקובץ הריק שנשמר במכשיר.
            if (!resp.isSuccessful) throw IllegalStateException("השרת החזיר ${resp.code}")
            val body = resp.body ?: throw IllegalStateException("גוף ריק")
            var got = 0L
            body.byteStream().use { ins ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(128 * 1024)
                    while (true) {
                        val read = ins.read(buf); if (read <= 0) break
                        out.write(buf, 0, read); got += read
                        if (len > 0) task.progress = ((got * 100) / len).toInt().coerceIn(0, 100)
                    }
                }
            }
        }
    }

    /**
     * שמירה ל"הורדות" הציבוריות — דרך MediaStore ב-Android 10+, אחרת ישירות
     * לתיקייה. מחזיר את מיקום הקובץ שנוצר.
     *
     * הערך המוחזר הוא העיקר: בלעדיו האפליקציה שמרה קובץ ומיד שכחה איפה הוא,
     * ולכן לא ידעה לנגן את מה שהיא עצמה הורידה.
     */
    private fun publish(spec: Spec, tmp: File): String {
        val resolver = spec.context.contentResolver
        val mime = if (spec.isAudio) "audio/mp4" else "video/mp4"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, spec.fileName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                // תיקייה משלנו בזיכרון הראשי, לא ערימה אחת בתוך "הורדות".
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore נכשל")
            resolver.openOutputStream(uri).use { out -> tmp.inputStream().use { it.copyTo(out!!) } }
            values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri.toString()
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                FOLDER,
            )
            if (!dir.exists()) dir.mkdirs()
            var out = File(dir, spec.fileName)
            if (out.exists()) out = File(dir, spec.fileName.substringBeforeLast('.') + "_" + System.currentTimeMillis() + "." + spec.fileName.substringAfterLast('.'))
            tmp.copyTo(out, overwrite = true)
            return android.net.Uri.fromFile(out).toString()
        }
    }
}
