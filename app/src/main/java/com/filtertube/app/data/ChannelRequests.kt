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
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Sends channel requests to the authenticated Firebase backend and exposes the
 * administrator-only review endpoints. Firestore remains inaccessible directly
 * from the Android application.
 */
object ChannelRequests {

    private const val BASE_API =
        "https://europe-west1-filter-tube-52d8e.cloudfunctions.net"
    private const val SUBMIT_API = "$BASE_API/submitChannelRequest"
    private const val LIST_API = "$BASE_API/listChannelRequests"
    private const val RESOLVE_API = "$BASE_API/resolveChannelRequest"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class Req(
        val id: String,
        val version: String,
        val name: String,
        val url: String,
        val category: String,
        val gender: String,
        val description: String,
        val requestedAt: String,
    )

    suspend fun submit(
        name: String,
        url: String,
        category: String,
        gender: String,
        description: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val session = verifiedSession(forceRefresh = false)
            ?: return@withContext false
        val payload = JSONObject().apply {
            put("name", name.trim())
            put("url", url.trim())
            put("category", category)
            put("gender", gender)
            put("description", description.trim())
        }.toString()
        val request = Request.Builder()
            .url(SUBMIT_API)
            .header("Authorization", "Bearer ${session.token}")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        runCatching {
            http.newCall(request).execute().use { response ->
                if (!sameUser(session.uid) || !response.isSuccessful) {
                    return@use false
                }
                JSONObject(response.body?.string().orEmpty())
                    .optBoolean("ok", false)
            }
        }.getOrDefault(false)
    }

    suspend fun list(): List<Req> = withContext(Dispatchers.IO) {
        val session = verifiedSession(forceRefresh = true)
            ?: throw IOException("נדרשת התחברות לחשבון מנהל מאומת")
        val request = Request.Builder()
            .url(LIST_API)
            .header("Authorization", "Bearer ${session.token}")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!sameUser(session.uid)) throw IOException("החשבון השתנה בזמן הבקשה")
            if (!response.isSuccessful) {
                throw IOException("טעינת בקשות הערוצים נכשלה (${response.code})")
            }
            val root = JSONObject(response.body?.string().orEmpty())
            if (!root.optBoolean("ok", false)) {
                throw IOException("טעינת בקשות הערוצים נכשלה")
            }
            val items = root.optJSONArray("requests") ?: return@use emptyList()
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    val version = item.optString("version")
                    if (id.isBlank() || version.isBlank()) continue
                    add(
                        Req(
                            id = id,
                            version = version,
                            name = item.optString("name"),
                            url = item.optString("url"),
                            category = item.optString("category", "general"),
                            gender = item.optString("gender", "all"),
                            description = item.optString("description"),
                            requestedAt = item.optString("requestedAt"),
                        ),
                    )
                }
            }
        }
    }

    suspend fun resolve(
        requestId: String,
        requestVersion: String,
        resolution: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            if (resolution !in setOf("approved", "rejected")) {
                return@withContext false
            }
            val session = verifiedSession(forceRefresh = true)
                ?: return@withContext false
            val payload = JSONObject().apply {
                put("id", requestId)
                put("version", requestVersion)
                put("resolution", resolution)
            }.toString()
            val request = Request.Builder()
                .url(RESOLVE_API)
                .header("Authorization", "Bearer ${session.token}")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            runCatching {
                http.newCall(request).execute().use { response ->
                    if (!sameUser(session.uid) || !response.isSuccessful) {
                        return@use false
                    }
                    JSONObject(response.body?.string().orEmpty())
                        .optBoolean("ok", false)
                }
            }.getOrDefault(false)
        }

    private data class Session(val uid: String, val token: String)

    private suspend fun verifiedSession(forceRefresh: Boolean): Session? {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
            ?.takeIf { it.isEmailVerified }
            ?: return null
        val token = runCatching { user.getIdToken(forceRefresh).await().token }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return if (auth.currentUser?.uid == user.uid) {
            Session(user.uid, token)
        } else {
            null
        }
    }

    private fun sameUser(expectedUid: String): Boolean =
        FirebaseAuth.getInstance().currentUser?.uid == expectedUid
}
