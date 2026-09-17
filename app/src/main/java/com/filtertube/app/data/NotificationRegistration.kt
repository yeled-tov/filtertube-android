package com.filtertube.app.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Registers this device for server push notifications after verified login. */
object NotificationRegistration {
    private const val TAG = "NotificationRegistration"
    private const val API =
        "https://europe-west1-filter-tube-52d8e.cloudfunctions.net/registerNotificationToken"
    // נגזר מהלקוח המשותף ולא מופע חדש: מופע נפרד מחזיק מאגר חיבורים
    // ומאגר תהליכונים משלו, ופותח חיבור TLS חדש גם כשכבר יש אחד פתוח
    // לאותו מארח.
    private val http = Http.newBuilder().build()

    suspend fun registerIfPossible() = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser?.takeIf { it.isEmailVerified } ?: return@withContext
        val token = runCatching { FirebaseMessaging.getInstance().token.await() }
            .onFailure { Log.w(TAG, "unable to retrieve push token", it) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return@withContext
        val idToken = runCatching { user.getIdToken(false).await().token }
            .onFailure { Log.w(TAG, "unable to retrieve Firebase ID token", it) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return@withContext
        runCatching {
            val request = Request.Builder().url(API)
                .header("Authorization", "Bearer $idToken")
                .post(JSONObject().put("token", token).toString().toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) Log.w(TAG, "token registration failed: ${response.code}")
            }
        }.onFailure { Log.w(TAG, "unable to register push token", it) }
    }

    /**
     * משחרר את אסימון ההתראות של המכשיר בהתנתקות.
     *
     * ## למה זה חייב לקרות
     * אסימון FCM מזהה **התקנה**, לא חשבון, והוא לא משתנה כשמתחלף המשתמש.
     * מכשיר שהאדמין התחבר בו פעם אחת נרשם אצלו בשרת, ונשאר רשום שם גם אחרי
     * שהמכשיר עבר ללקוח — ולכן אותו לקוח המשיך לקבל "בקשת ערוץ חדשה".
     *
     * מחיקת האסימון עצמו היא הפתרון הנקי: היא מבטלת אותו אצל FCM, כך שכל
     * שליחה אליו נכשלת מיד (והשרת מנקה את הרישום), והמכשיר מקבל אסימון חדש
     * לגמרי בהתחברות הבאה. זה לא דורש קריאת רשת מאומתת בדיוק ברגע שבו
     * ההרשאה נלקחת.
     */
    fun releaseOnSignOut() {
        FirebaseMessaging.getInstance().deleteToken()
            .addOnFailureListener { Log.w(TAG, "unable to release push token", it) }
    }
}
