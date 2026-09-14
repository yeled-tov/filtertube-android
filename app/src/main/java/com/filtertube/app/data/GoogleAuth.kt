package com.filtertube.app.data

import android.accounts.Account
import android.content.Context
import java.security.MessageDigest
import android.os.Build
import android.content.pm.PackageManager
import com.filtertube.app.R
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * התחברות לחשבון Google לצורך קריאת נתוני יוטיוב (סרטונים שאהבת).
 * משתמש ב-OAuth client מסוג Android (מזוהה לפי package + SHA-1, ללא secret בקוד).
 */
object GoogleAuth {

    // force-ssl כולל קריאה (לייקים/מנויים) וגם כתיבה (videos.rate — סימון לייק חזרה ליוטיוב)
    const val YT_SCOPE = "https://www.googleapis.com/auth/youtube.force-ssl"
    private const val PREFS = "filtertube_google_auth_scope"
    private const val KEY_OWNER_UID = "firebase_owner_uid"

    data class Session internal constructor(
        val uid: String,
        val generation: Long,
    )

    /**
     * מזהה לקוח ה-Web של פרויקט Firebase, אם הוא קיים.
     *
     * נקרא בזמן ריצה ולא כקבוע מהודר בכוונה: תוסף google-services מייצר את
     * המשאב default_web_client_id רק כאשר google-services.json מכיל
     * oauth_client מסוג web (client_type 3). בקובץ הנוכחי אין אחד כזה, ולכן
     * הפניה מהודרת ל-R.string הייתה מפילה את הבנייה. כך הקוד נבנה תמיד,
     * ומרגע שהקובץ יתעדכן ההתחברות המאוחדת מתחילה לעבוד בלי שינוי קוד.
     *
     * כדי לייצר אותו: Firebase Console ← Authentication ← Sign-in method ←
     * הפעלת Google, ואז הורדה מחדש של google-services.json.
     */
    fun webClientId(context: Context): String? {
        // עדיפות ראשונה: המשאב שתוסף google-services מייצר, אם הקובץ עודכן.
        val generated = context.resources
            .getIdentifier("default_web_client_id", "string", context.packageName)
            .takeIf { it != 0 }
            ?.let { context.getString(it) }
            ?.takeIf { it.isNotBlank() }
        if (generated != null) return generated

        // ונפילה למחרוזת שאפשר להדביק ידנית ב-strings.xml. הורדת קובץ
        // google-services.json מהטלפון היא מכשול אמיתי; העתקת מזהה אחד לא.
        return runCatching { context.getString(R.string.filtertube_web_client_id) }
            .getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** true כשאפשר להשתמש בהתחברות מאוחדת (גוגל מאמת גם את חשבון FilterTube). */
    fun unifiedSignInAvailable(context: Context): Boolean = webClientId(context) != null

    /**
     * טביעת האצבע (SHA-1) של המפתח שבו האפליקציה **הזו** חתומה בפועל.
     *
     * ## למה זה נחוץ
     * שגיאה 10 (DEVELOPER_ERROR) אומרת רק "ההגדרה בצד גוגל לא תואמת את
     * האפליקציה". היא לא אומרת במה. הרוב המכריע של המקרים הוא טביעת אצבע
     * שלא נרשמה, נרשמה לאפליקציה אחרת, או נרשמה מתוך מפתח אחר מזה שחתם
     * על ה-APK שמותקן.
     *
     * לנחש איזה מהשלושה זה בזבוז זמן. האפליקציה יכולה פשוט להסתכל על
     * החתימה של עצמה ולומר אותה — ואז ההשוואה מול מה שרשום ב-Firebase היא
     * מיידית וודאית.
     */
    fun signatureSha1(context: Context): String? = runCatching {
        val pm = context.packageManager
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
        }
        val first = signatures?.firstOrNull() ?: return@runCatching null
        MessageDigest.getInstance("SHA-1")
            .digest(first.toByteArray())
            .joinToString(":") { "%02X".format(it) }
    }.getOrNull()

    /** שורת אבחון אחת שאומרת כל מה שצריך כדי לפתור שגיאה 10. */
    fun logSignInConfig(context: Context) {
        val id = webClientId(context)
        Diagnostics.log(
            "AUTH הגדרה: חבילה=${context.packageName} · " +
                "SHA1=${signatureSha1(context) ?: "לא ידוע"} · " +
                "מזהה לקוח Web=${if (id.isNullOrBlank()) "חסר" else "…" + id.takeLast(28)}",
        )
    }

    /**
     * התחברות "רזה": מייל ו-idToken בלבד, בלי הרשאת יוטיוב.
     *
     * ## למה בנפרד
     * youtube.force-ssl היא הרשאה רגישה. בקשה שלה *יחד* עם idToken באותה
     * קריאה מחייבת שגם מסך ההסכמה של הפרויקט יהיה מוגדר ומאושר לאותה
     * הרשאה, ולא רק שטביעת האצבע תהיה רשומה — כלומר שני תנאים במקום אחד,
     * ושניהם מחזירים בדיוק את אותה שגיאה 10 חסרת הפרטים.
     *
     * יצירת החשבון לא צריכה את יוטיוב בכלל. היא משתמשת במסלול הסטנדרטי
     * והיציב, וההרשאה ליוטיוב מתבקשת בנפרד — רק כשבאמת מושכים נתונים.
     * זה גם עדיף למשתמש: לא מבקשים גישה לחשבון היוטיוב שלו לפני שהיא
     * נחוצה.
     */
    fun basicClient(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .apply { webClientId(context)?.let { requestIdToken(it) } }
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    /**
     * הלקוח למשיכת נתונים מיוטיוב — מייל והרשאת יוטיוב, **בלי idToken**.
     *
     * ## למה בלי, ולמה זה חשוב
     * זו הייתה רגרסיה שלי. הוספתי כאן requestIdToken כדי לאחד את ההתחברות,
     * ובכך הפכתי כפתור שעבד מצוין לכפתור שנכשל תמיד.
     *
     * הסיבה: בקשת idToken מחייבת שיהיה בפרויקט לקוח OAuth מסוג Android
     * לצמד (חבילה, טביעת אצבע). בלעדיו Play Services מחזיר DEVELOPER_ERROR
     * — ולא רק לחלק המאוחד, אלא לכל הבקשה. בלי idToken אין דרישה כזו
     * בכלל, ולכן משיכת הלייקים והמנויים עבדה קודם ותעבוד שוב.
     *
     * ההתחברות המאוחדת (יצירת חשבון דרך גוגל) עברה ל-[basicClient] הנפרד.
     * כך תקלה בהגדרת OAuth פוגעת רק בה, ולא גוררת איתה תכונה שעובדת.
     */
    fun client(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(YT_SCOPE))
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    /**
     * מזדהה מול Firebase עם אותו חשבון גוגל שהמשתמש בחר.
     *
     * זה מה שמאחד את שתי ההתחברויות: לחיצה אחת יוצרת (או מאתרת) את חשבון
     * FilterTube, והמייל מגיע מאומת מגוגל — כלומר בלי שלב אימות מייל בכלל —
     * ובאותה בחירה מתקבלת גם ההרשאה למשוך את הלייקים והמנויים מיוטיוב.
     *
     * הסיסמה ההורית אינה מושפעת: היא נשמרת מקומית עם salt ו-hash משלה
     * ומגנה על רמות הסינון ועל Shorts, לא על החשבון.
     */
    suspend fun signInToFirebase(account: GoogleSignInAccount): Result<Unit> =
        withContext(Dispatchers.IO) {
            val idToken = account.idToken
                ?: return@withContext Result.failure(
                    IllegalStateException(
                        "החיבור המאוחד לא מוגדר בפרויקט — צריך להפעיל Google " +
                            "ב-Firebase Authentication ולהוריד מחדש את google-services.json",
                    ),
                )
            runCatching {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                FirebaseAuth.getInstance().signInWithCredential(credential).await()
                Diagnostics.log("AUTH: התחברות מאוחדת דרך גוגל הצליחה")
                Unit
            }.onFailure { Diagnostics.log("AUTH: התחברות מאוחדת נכשלה — ${it.message}") }
        }

    fun bindToCurrentFirebaseAccount(
        context: Context,
        account: GoogleSignInAccount,
    ): Session? {
        if (account.account == null) return null
        return AccountDataGuard.withLock {
            val user = FirebaseAuth.getInstance().currentUser
                ?.takeIf { it.isEmailVerified }
                ?: return@withLock null
            val generation = AccountDataGuard.generation()
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_OWNER_UID, user.uid)
                .apply()
            Session(user.uid, generation)
        }
    }

    fun lastAccount(context: Context): GoogleSignInAccount? {
        val user = FirebaseAuth.getInstance().currentUser
            ?.takeIf { it.isEmailVerified }
            ?: run {
                signOut(context)
                return null
            }
        val ownerUid = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_OWNER_UID, "")
            .orEmpty()
        if (ownerUid != user.uid) {
            signOut(context)
            return null
        }
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    fun session(
        context: Context,
        account: GoogleSignInAccount,
    ): Session? {
        val currentAccount = lastAccount(context) ?: return null
        if (currentAccount.id != account.id || currentAccount.email != account.email) {
            return null
        }
        val user = FirebaseAuth.getInstance().currentUser
            ?.takeIf { it.isEmailVerified }
            ?: return null
        return Session(user.uid, AccountDataGuard.generation())
            .takeIf { isSessionCurrent(context, it) }
    }

    fun isSessionCurrent(context: Context, session: Session): Boolean {
        val user = FirebaseAuth.getInstance().currentUser
        val ownerUid = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_OWNER_UID, "")
            .orEmpty()
        return user?.uid == session.uid &&
            user.isEmailVerified &&
            ownerUid == session.uid &&
            AccountDataGuard.generation() == session.generation
    }

    fun signOut(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        client(context.applicationContext).signOut()
    }

    /** מחזיר OAuth access token לקריאת YouTube Data API. רץ ב-IO. */
    suspend fun accessToken(
        context: Context,
        account: Account,
        session: Session,
    ): String = withContext(Dispatchers.IO) {
        check(isSessionCurrent(context, session)) { "Firebase account changed" }
        val token = GoogleAuthUtil.getToken(context, account, "oauth2:$YT_SCOPE")
        check(isSessionCurrent(context, session)) { "Firebase account changed" }
        token
    }
}
