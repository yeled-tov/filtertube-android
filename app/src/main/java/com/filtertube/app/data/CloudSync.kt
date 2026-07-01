package com.filtertube.app.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * סנכרון ענן דרך שרת משלי — לא משתמש ב-Google Sign-In.
 * כל לקוח יוצר/מתחבר עם אימייל וסיסמה, והשרת שומר:
 * - היסטוריית חיפושים
 * - היסטוריית האזנה
 * - אהובים
 * - הורדות
 * - הגדרות בסיסיות
 */
object CloudSync {
    private const val TAG = "CloudSync"
    private val client = OkHttpClient()

    private fun baseUrl(settings: SettingsStore): String = settings.serverBaseUrl.trim().removeSuffix("/")

    suspend fun signInOrRegister(email: String, password: String, settings: SettingsStore): Boolean = withContext(Dispatchers.IO) {
        val normalized = email.trim()
        if (normalized.isBlank() || password.length < 6) return@withContext false
        val url = baseUrl(settings)
        if (url.isBlank()) return@withContext false

        val payload = JSONObject().apply {
            put("email", normalized)
            put("password", password)
            put("app", "filtertube")
            put("device", "android")
            put("apiKey", settings.serverApiKey)
        }

        val request = Request.Builder()
            .url("$url/api/cloud/auth")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.e(TAG, "auth failed: ${response.code} $body")
                    return@withContext false
                }

                val json = JSONObject(body)
                val token = json.optString("token", "").takeIf { it.isNotBlank() } ?: return@withContext false
                settings.cloudUid = json.optString("userId", "")
                settings.cloudEmail = normalized
                settings.cloudToken = token
                syncUserProfile(settings)
                true
            }
        }.getOrElse {
            Log.e(TAG, "signInOrRegister failed", it)
            false
        }
    }

    suspend fun signOut(settings: SettingsStore) = withContext(Dispatchers.IO) {
        settings.cloudUid = ""
        settings.cloudEmail = ""
        settings.cloudToken = ""
    }

    suspend fun syncUserProfile(settings: SettingsStore): Boolean = withContext(Dispatchers.IO) {
        val url = baseUrl(settings)
        if (url.isBlank() || settings.cloudToken.isBlank()) return@withContext false

        val payload = JSONObject().apply {
            put("token", settings.cloudToken)
            put("name", settings.userName)
            put("email", settings.cloudEmail)
            put("gender", settings.userGender)
            put("filterLevel", settings.filterLevel)
            put("searchHistory", settings.getSearchHistory())
            put("history", settings.getHistoryItems())
            put("likedVideos", settings.getLikedVideos())
            put("downloads", settings.getDownloads())
            put("updatedAt", System.currentTimeMillis())
        }

        val request = Request.Builder()
            .url("$url/api/cloud/profile")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.e(TAG, "profile sync failed: ${response.code} $body")
                    return@withContext false
                }
                true
            }
        }.getOrElse {
            Log.e(TAG, "syncUserProfile failed", it)
            false
        }
    }

    suspend fun pullCloudData(settings: SettingsStore): Boolean = withContext(Dispatchers.IO) {
        val url = baseUrl(settings)
        if (url.isBlank() || settings.cloudToken.isBlank()) return@withContext false

        val request = Request.Builder()
            .url("$url/api/cloud/profile")
            .header("Authorization", "Bearer ${settings.cloudToken}")
            .get()
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.e(TAG, "pull failed: ${response.code} $body")
                    return@withContext false
                }

                val json = JSONObject(body)
                settings.applyCloudProfile(json)
                true
            }
        }.getOrElse {
            Log.e(TAG, "pullCloudData failed", it)
            false
        }
    }
}
