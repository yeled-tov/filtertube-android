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

/** Client for the manual, email-based Premium approval workflow. */
object ManualPremiumRequests {
    const val SUPPORT_EMAIL = "ywldyld@gmail.com"
    private const val BASE_API =
        "https://europe-west1-filter-tube-52d8e.cloudfunctions.net"
    private const val SUBMIT_API = "$BASE_API/submitPremiumRequest"
    private const val LIST_API = "$BASE_API/listPremiumRequests"
    private const val RESOLVE_API = "$BASE_API/resolvePremiumRequest"

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class SubmitResult(val ok: Boolean, val message: String)

    data class RequestItem(
        val id: String,
        val version: String,
        val accountEmail: String,
        val contactEmail: String,
        val name: String,
        val phone: String,
        val plan: String,
        val priceUsd: String,
        val requestedAt: String,
        val status: String = "pending",
    )

    suspend fun submit(
        name: String,
        phone: String,
        contactEmail: String,
        plan: String,
    ): SubmitResult = withContext(Dispatchers.IO) {
        val session = verifiedSession(forceRefresh = false)
            ?: return@withContext SubmitResult(false, "יש לאמת את כתובת המייל לפני שליחת בקשה")
        val payload = JSONObject().apply {
            put("name", name.trim())
            put("phone", phone.trim())
            put("contactEmail", contactEmail.trim())
            put("plan", plan)
        }.toString()
        val request = Request.Builder()
            .url(SUBMIT_API)
            .header("Authorization", "Bearer ${session.token}")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        runCatching {
            http.newCall(request).execute().use { response ->
                val json = JSONObject(response.body?.string().orEmpty())
                if (!sameUser(session.uid)) {
                    return@use SubmitResult(false, "החשבון השתנה במהלך שליחת הבקשה")
                }
                if (!response.isSuccessful || !json.optBoolean("ok", false)) {
                    SubmitResult(false, json.optString("message", "לא ניתן לשלוח את הבקשה"))
                } else {
                    SubmitResult(true, "הבקשה נשמרה")
                }
            }
        }.getOrElse { SubmitResult(false, "לא ניתן להתחבר לשרת כרגע") }
    }

    suspend fun list(history: Boolean = false): List<RequestItem> = withContext(Dispatchers.IO) {
        val session = verifiedSession(forceRefresh = true)
            ?: throw IOException("נדרשת התחברות לחשבון מנהל מאומת")
        val request = Request.Builder()
            .url(if (history) "$LIST_API?history=1" else LIST_API)
            .header("Authorization", "Bearer ${session.token}")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!sameUser(session.uid)) throw IOException("החשבון השתנה בזמן הבקשה")
            val json = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful || !json.optBoolean("ok", false)) {
                throw IOException(json.optString("message", "טעינת בקשות Premium נכשלה"))
            }
            val items = json.optJSONArray("requests") ?: return@use emptyList()
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    val version = item.optString("version")
                    if (id.isBlank() || version.isBlank()) continue
                    add(
                        RequestItem(
                            id = id,
                            version = version,
                            accountEmail = item.optString("accountEmail"),
                            contactEmail = item.optString("contactEmail"),
                            name = item.optString("name"),
                            phone = item.optString("phone"),
                            plan = item.optString("plan", "month"),
                            priceUsd = item.optString("priceUsd"),
                            requestedAt = item.optString("requestedAt"),
                            status = item.optString("status", "pending"),
                        ),
                    )
                }
            }
        }
    }

    suspend fun resolve(id: String, version: String, resolution: String): Boolean =
        withContext(Dispatchers.IO) {
            if (resolution !in setOf("approved", "rejected")) return@withContext false
            val session = verifiedSession(forceRefresh = true) ?: return@withContext false
            val payload = JSONObject().apply {
                put("id", id)
                put("version", version)
                put("resolution", resolution)
            }.toString()
            val request = Request.Builder()
                .url(RESOLVE_API)
                .header("Authorization", "Bearer ${session.token}")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            runCatching {
                http.newCall(request).execute().use { response ->
                    if (!sameUser(session.uid) || !response.isSuccessful) return@use false
                    JSONObject(response.body?.string().orEmpty()).optBoolean("ok", false)
                }
            }.getOrDefault(false)
        }

    private data class Session(val uid: String, val token: String)

    private suspend fun verifiedSession(forceRefresh: Boolean): Session? {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: return null
        runCatching { user.reload().await() }
        val refreshedUser = auth.currentUser?.takeIf { it.isEmailVerified } ?: return null
        val token = runCatching { refreshedUser.getIdToken(forceRefresh).await().token }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return if (auth.currentUser?.uid == refreshedUser.uid) Session(refreshedUser.uid, token) else null
    }

    private fun sameUser(expectedUid: String): Boolean =
        FirebaseAuth.getInstance().currentUser?.uid == expectedUid
}
