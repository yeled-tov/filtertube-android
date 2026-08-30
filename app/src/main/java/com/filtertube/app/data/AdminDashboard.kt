package com.filtertube.app.data

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object AdminDashboard {
    private const val API = "https://europe-west1-filter-tube-52d8e.cloudfunctions.net/adminDashboard"
    private val http = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()

    data class Summary(
        val totalAccounts: Int,
        val verifiedAccounts: Int,
        val connectedAccounts: Int,
        val premiumAccounts: Int,
        val trialAccounts: Int,
    )

    data class Client(
        val uid: String,
        val email: String,
        val verified: Boolean,
        val disabled: Boolean,
        val createdAt: String,
        val lastSignInAt: String,
        val premium: Boolean,
        val manualPremium: Boolean,
        val trialActive: Boolean,
        val plan: String?,
        val subscriptionStartedAt: String,
        val subscriptionEndsAt: String,
    )

    data class Snapshot(val summary: Summary, val clients: List<Client>)

    suspend fun load(): Snapshot = withContext(Dispatchers.IO) {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser?.takeIf { it.isEmailVerified }
            ?: throw IOException("נדרשת התחברות לחשבון מנהל מאומת")
        val token = user.getIdToken(true).await().token ?: throw IOException("לא ניתן לאמת את המנהל")
        val request = Request.Builder().url(API).header("Authorization", "Bearer $token").get().build()
        http.newCall(request).execute().use { response ->
            val root = JSONObject(response.body?.string().orEmpty())
            if (!response.isSuccessful || !root.optBoolean("ok", false)) {
                throw IOException(root.optString("message", "טעינת דשבורד הלקוחות נכשלה"))
            }
            val s = root.optJSONObject("summary") ?: JSONObject()
            val summary = Summary(
                s.optInt("totalAccounts"), s.optInt("verifiedAccounts"),
                s.optInt("connectedAccounts"), s.optInt("premiumAccounts"), s.optInt("trialAccounts"),
            )
            val array = root.optJSONArray("clients") ?: return@use Snapshot(summary, emptyList())
            val clients = buildList {
                for (i in 0 until array.length()) {
                    val c = array.optJSONObject(i) ?: continue
                    add(Client(
                        uid = c.optString("uid"), email = c.optString("email"),
                        verified = c.optBoolean("emailVerified"), disabled = c.optBoolean("disabled"),
                        createdAt = c.optString("createdAt"), lastSignInAt = c.optString("lastSignInAt"),
                        premium = c.optBoolean("premium"), manualPremium = c.optBoolean("manualPremium"),
                        trialActive = c.optBoolean("trialActive"), plan = c.optString("plan").ifBlank { null },
                        subscriptionStartedAt = c.optString("subscriptionStartedAt"),
                        subscriptionEndsAt = c.optString("subscriptionEndsAt"),
                    ))
                }
            }
            Snapshot(summary, clients)
        }
    }
}
