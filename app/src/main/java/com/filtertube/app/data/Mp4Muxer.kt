package com.filtertube.app.data

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * ממזג זרם וידאו-בלבד וזרם אודיו לקובץ MP4 אחד.
 *
 * ## למה זה נחוץ
 * יוטיוב כבר כמעט לא מגיש זרמים משולבים (muxed). מנוע ה-iOS, שהוא המהיר
 * ביותר, מחזיר אך ורק DASH — וידאו ואודיו בנפרד. עד עכשיו מסך ההורדה הציג
 * רק זרמים משולבים, כלומר "לא זמין להורדה עם קול" כמעט תמיד, ובפועל אפשר
 * היה להוריד רק אודיו.
 *
 * ## למה MediaMuxer ולא FFmpeg
 * MediaMuxer הוא חלק ממערכת ההפעלה: אפס תלויות חדשות, אפס מגה-בייטים
 * נוספים ב-APK, ואפס שאלות רישוי. המחיר הוא שהוא יודע לארוז MP4 עם H.264
 * ו-AAC בלבד — ולכן [com.filtertube.app.data.StreamTrack.muxableToMp4]
 * בודק את ה-mimeType מראש, ומורידים רק זרמים שאפשר למזג.
 *
 * המיזוג הוא העתקת דגימות בלבד — בלי פענוח וקידוד מחדש. הוא מהיר, לא פוגע
 * באיכות, ולא מחמם את המכשיר.
 */
object Mp4Muxer {

    private const val BUFFER_SIZE = 1 shl 20

    /**
     * כותב את מסלול הווידאו מ-[videoFile] ואת מסלול האודיו מ-[audioFile]
     * לקובץ [out]. זורק אם אחד המסלולים חסר או שהפורמט לא נתמך.
     */
    fun combine(videoFile: File, audioFile: File, out: File) {
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val video = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
        val audio = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }
        try {
            val videoTrack = selectTrack(video, "video/")
                ?: throw IllegalStateException("לא נמצא מסלול וידאו בקובץ שהורד")
            val audioTrack = selectTrack(audio, "audio/")
                ?: throw IllegalStateException("לא נמצא מסלול אודיו בקובץ שהורד")

            val outVideo = muxer.addTrack(video.getTrackFormat(videoTrack))
            val outAudio = muxer.addTrack(audio.getTrackFormat(audioTrack))
            muxer.start()

            copy(video, videoTrack, muxer, outVideo)
            copy(audio, audioTrack, muxer, outAudio)

            muxer.stop()
        } finally {
            runCatching { muxer.release() }
            runCatching { video.release() }
            runCatching { audio.release() }
        }
    }

    private fun selectTrack(extractor: MediaExtractor, prefix: String): Int? {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(prefix)) {
                extractor.selectTrack(i)
                return i
            }
        }
        return null
    }

    private fun copy(extractor: MediaExtractor, track: Int, muxer: MediaMuxer, outIndex: Int) {
        val buffer = ByteBuffer.allocate(BUFFER_SIZE)
        val info = MediaCodec.BufferInfo()
        extractor.selectTrack(track)
        while (true) {
            val size = extractor.readSampleData(buffer, 0)
            if (size < 0) break
            info.offset = 0
            info.size = size
            info.presentationTimeUs = extractor.sampleTime
            info.flags = extractor.sampleFlags
            muxer.writeSampleData(outIndex, buffer, info)
            extractor.advance()
        }
        extractor.unselectTrack(track)
    }
}
