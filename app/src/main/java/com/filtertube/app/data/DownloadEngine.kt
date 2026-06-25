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
    )

    private val queue = ArrayDeque<Pair<DownloadTask, Spec>>()
    private var running = 0
    private var maxConcurrent = 3

    @Synchronized
    fun enqueue(context: Context, video: Video, url: String, isAudio: Boolean, userAgent: String?) {
        val ctx = context.applicationContext
        val settings = SettingsStore(ctx)
        maxConcurrent = settings.concurrentDownloads
        val task = DownloadTask(video, isAudio)
        active.add(0, task)
        while (active.size > 60) active.removeAt(active.lastIndex)
        queue.addLast(task to Spec(url, fileName(video.title, isAudio), userAgent, settings.connectionsPerDownload, isAudio, ctx))
        pump()
    }

    /** מחלץ את הזרם של [video] (זרם משולב עם קול) ומוסיף אותו לתור. */
    suspend fun enqueueByVideo(context: Context, video: Video, isAudio: Boolean): Boolean {
        val data = runCatching { StreamRepository.getStream(video.id) }.getOrNull() ?: return false
        val url = if (isAudio) (data.bestAudioUrl ?: data.bestVideoUrl) else data.bestVideoUrl
        val v = video.copy(
            title = data.title.ifBlank { video.title },
            channelName = data.uploaderName.ifBlank { video.channelName },
            thumbnailUrl = data.thumbnailUrl ?: video.thumbnailUrl,
        )
        enqueue(context, v, url, isAudio, data.streamUserAgent)
        return true
    }

    @Synchronized
    private fun pump() {
        while (running < maxConcurrent && queue.isNotEmpty()) {
            val (task, spec) = queue.removeFirst()
            running++
            scope.launch {
                runCatching {
                    task.status = "מוריד"
                    downloadFile(spec, task)
                    task.progress = 100; task.status = "הושלם"
                }.onFailure { task.status = "נכשל" }
                synchronized(this@DownloadEngine) { running--; pump() }
            }
        }
    }

    private fun fileName(title: String, isAudio: Boolean): String {
        val safe = title.replace(Regex("[^\\p{L}\\p{N} _-]"), "").trim().take(60).ifEmpty { "filtertube" }
        return "$safe.${if (isAudio) "m4a" else "mp4"}"
    }

    private suspend fun downloadFile(spec: Spec, task: DownloadTask) {
        val tmp = File(spec.context.cacheDir, "ft_dl_${System.nanoTime()}.tmp")
        try {
            // בדיקה: גודל הקובץ + תמיכה ב-Range
            val probe = Request.Builder().url(spec.url).head()
                .apply { spec.ua?.let { header("User-Agent", it) } }.build()
            var len = -1L; var ranges = false
            runCatching {
                http.newCall(probe).execute().use { r ->
                    len = r.header("Content-Length")?.toLongOrNull() ?: -1L
                    ranges = r.header("Accept-Ranges")?.contains("bytes", true) == true
                }
            }
            if (len > 0 && ranges && spec.connections > 1) multiConn(spec, tmp, len, task)
            else single(spec, tmp, len, task)
            publish(spec, tmp)
        } finally {
            runCatching { tmp.delete() }
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
                        val body = resp.body ?: return@use
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
        val req = Request.Builder().url(spec.url)
            .apply { spec.ua?.let { header("User-Agent", it) } }.build()
        http.newCall(req).execute().use { resp ->
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

    /** שמירה ל"הורדות" הציבוריות — דרך MediaStore ב-Android 10+, אחרת ישירות לתיקייה. */
    private fun publish(spec: Spec, tmp: File) {
        val resolver = spec.context.contentResolver
        val mime = if (spec.isAudio) "audio/mp4" else "video/mp4"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, spec.fileName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("MediaStore נכשל")
            resolver.openOutputStream(uri).use { out -> tmp.inputStream().use { it.copyTo(out!!) } }
            values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            var out = File(dir, spec.fileName)
            if (out.exists()) out = File(dir, spec.fileName.substringBeforeLast('.') + "_" + System.currentTimeMillis() + "." + spec.fileName.substringAfterLast('.'))
            tmp.copyTo(out, overwrite = true)
        }
    }
}
