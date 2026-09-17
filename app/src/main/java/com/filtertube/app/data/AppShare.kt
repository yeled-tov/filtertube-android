package com.filtertube.app.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.FileProvider
import com.filtertube.app.BuildConfig
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * שיתוף האפליקציה — שלוש דרכים, לפי מה שבאמת קורה מול האדם שמולך.
 *
 * ## למה הקישור הוא של אתר שלנו ולא של GitHub
 * ה-APK יושב ב-GitHub Releases, ומתחשק לשתף קישור ישיר לשם. אבל קישור
 * ששותף פעם אחת חי לנצח בוואטסאפ של מישהו — ואם המאגר יהפוך לפרטי, כל
 * הקישורים שכבר יצאו יפסיקו לעבוד בבת אחת. לכן הקישור שיוצא החוצה הוא
 * תמיד [LINK], דף שאנחנו שולטים בו: מה שהוא מפנה אליו אפשר לשנות בכל רגע,
 * בלי לגעת באפליקציות שכבר הותקנו ובלי לרדוף אחרי הודעות ישנות.
 *
 * [RemoteConfig] יכול לדרוס אותו (`share.url`) — כלומר גם אם הכתובת עצמה
 * תשתנה יום אחד, לא צריך בנייה חדשה כדי שהטלפונים ידעו על כך.
 *
 * ## למה גם קובץ ההתקנה עצמו
 * בלוטות' לא יודע לפתוח קישור. מי שיושב לידך בלי אינטרנט צריך את ה-APK
 * עצמו, והוא כבר נמצא על המכשיר — זו בדיוק האפליקציה שרצה. מעתיקים אותו
 * למטמון ומשתפים משם, כי את הנתיב הפנימי של החבילה אי אפשר למסור החוצה.
 *
 * ## למה גם QR
 * שיתוף לאדם שעומד מולך: הוא מצלם ומוריד, בלי לחפש אותך ברשימת אנשי הקשר
 * ובלי להקליד כתובת.
 */
object AppShare {

    /** דף ההורדה. Firebase Hosting — נשאר ציבורי גם אם המאגר יהפוך לפרטי. */
    const val LINK = "https://filter-tube-52d8e.web.app"

    /** כתובת השיתוף בפועל — עם אפשרות לדריסה מהענן. */
    fun link(): String = RemoteConfig.shareUrl(LINK)

    /** הטקסט שנשלח יחד עם הקישור. */
    fun message(): String = buildString {
        append("FilterTube — יוטיוב עם ערוצים מאושרים בלבד.\n")
        append("כל ערוץ נבדק ואושר ידנית על ידי אדם.\n\n")
        append(link())
    }

    /** שיתוף הקישור: וואטסאפ, SMS, מייל — כל מה שמותקן במכשיר. */
    fun shareLink(context: Context) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "FilterTube")
            putExtra(Intent.EXTRA_TEXT, message())
        }
        context.startActivity(
            Intent.createChooser(intent, "שתף את FilterTube")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun copyLink(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("FilterTube", link()))
    }

    /**
     * שיתוף קובץ ההתקנה עצמו — בלוטות', שיתוף מהיר, או כל אפליקציה שמקבלת קבצים.
     *
     * ה-APK מועתק מהחבילה המותקנת אל תיקיית המטמון, כי `sourceDir` יושב
     * בתיקייה של המערכת שאפליקציה אחרת לא יכולה לקרוא ממנה גם עם הרשאה.
     * שם הקובץ כולל את מספר הבנייה, כדי שמי שמקבל יידע מה הוא מתקין.
     *
     * מחזיר false אם לא הצלחנו להכין את הקובץ — המסך מציג הודעה במקום
     * לפתוח חלון שיתוף ריק.
     */
    suspend fun shareApk(context: Context): Boolean = withContext(Dispatchers.IO) {
        val uri = runCatching {
            val source = File(context.applicationInfo.sourceDir)
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            // מנקים גרסאות קודמות: בלעדיו המטמון צובר APK בכל עדכון.
            dir.listFiles()?.forEach { if (it.name.endsWith(".apk")) it.delete() }
            val target = File(dir, "FilterTube-${BuildConfig.VERSION_CODE}.apk")
            source.copyTo(target, overwrite = true)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        }.getOrNull() ?: return@withContext false

        withContext(Dispatchers.Main) {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "FilterTube")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intent, "שלח את קובץ ההתקנה")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        true
    }

    /**
     * קוד QR של קישור ההורדה.
     *
     * הציור ידני ולא דרך ספריית אנדרואיד של zxing: המקודד מחזיר מטריצת
     * ביטים, וזה כל מה שצריך כדי לבנות Bitmap. כך נכנסת רק ספריית ה-Java
     * הטהורה, בלי המודול שגורר איתו מצלמה ורכיבי מסך שלא נשתמש בהם.
     *
     * תיקון שגיאות ברמה H: קוד שמצלמים מהמסך של מישהו אחר סובל מהשתקפות
     * ומזווית, ורמה גבוהה היא ההבדל בין קוד שנקרא בשנייה לקוד שמנסים שוב ושוב.
     */
    fun qrBitmap(size: Int = 640, content: String = link()): Bitmap? = runCatching {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) {
            val row = y * matrix.width
            for (x in 0 until matrix.width) {
                pixels[row + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        bitmap.setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
        bitmap
    }.getOrNull()
}
