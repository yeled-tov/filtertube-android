package com.filtertube.app.data

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Sends a diagnostic or crash report to the authenticated Firebase backend.
 *
 * Reports are stored in a server-only Firestore collection. No GitHub token or
 * other privileged credential is ever embedded in the Android application.
 */
object BugReport {

    private const val API =
        "https://europe-west1-filter-tube-52d8e.cloudfunctions.net/submitBugReport"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun submit(report: String, note: String = ""): Boolean = withContext(Dispatchers.IO) {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: return@withContext false
        if (!user.isEmailVerified) return@withContext false

        val expectedUid = user.uid
        val idToken = runCatching { user.getIdToken(true).await().token }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return@withContext false
        if (auth.currentUser?.uid != expectedUid) return@withContext false

        val payload = JSONObject().apply {
            put("report", report)
            put("note", note)
        }.toString()
        val request = Request.Builder()
            .url(API)
            .header("Authorization", "Bearer $idToken")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        runCatching {
            http.newCall(request).execute().use { response ->
                if (auth.currentUser?.uid != expectedUid) return@use false
                if (!response.isSuccessful) {
                    android.util.Log.w("BugReport", "submit failed: HTTP ${response.code}")
                    return@use false
                }
                val body = response.body?.string().orEmpty()
                JSONObject(body).optBoolean("ok", false)
            }
        }.getOrDefault(false)
    }
}
