package com.filtertube.app.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Registers this device for server push notifications after verified login. */
object NotificationRegistration {
    private const val TAG = "NotificationRegistration"
    private const val API =
        "https://europe-west1-filter-tube-52d8e.cloudfunctions.net/registerNotificationToken"
    private val http = OkHttpClient()

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
}
