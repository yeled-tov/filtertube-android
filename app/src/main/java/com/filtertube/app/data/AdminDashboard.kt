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

object AdminDashboard {
    private const val BASE = "https://europe-west1-filter-tube-52d8e.cloudfunctions.net"
    private const val API = "$BASE/adminDashboard"
    private const val MANAGE_API = "$BASE/manageClient"
    private val http = Http.newBuilder().readTimeout(30, TimeUnit.SECONDS).build()

    data class Summary(
        val totalAccounts: Int,
        val verifiedAccounts: Int,
        val connectedAccounts: Int,
        val premiumAccounts: Int,
        val trialAccounts: Int,
        val disabledAccounts: Int = 0,
        val pendingRequests: Int = 0,
        val level1: Int = 0,
        val level2: Int = 0,
        val level3: Int = 0,
        val levelUnset: Int = 0,
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
        val displayName: String = "",
        val gender: String = "",
        val filterLevel: Int = 0,
        val onboardingDone: Boolean = false,
        val channelRequests: Int = 0,
        val pendingChannelRequests: Int = 0,
        val premiumRequestPending: Boolean = false,
    ) {
        /** "רמה 3 · דתי לייט" — מה הלקוח באמת רואה באפליקציה. */
        val filterLevelHe: String
            get() = when (filterLevel) {
                1 -> "רמה 1 · מחמיר"
                2 -> "רמה 2 · רגיל"
                3 -> "רמה 3 · דתי לייט"
                else -> "לא נבחרה רמה"
            }

        val genderHe: String
            get() = when (gender.trim().lowercase()) {
                "male" -> "בן"
                "female" -> "בת"
                else -> ""
            }

        /** התווית שמסכמת את מצב החשבון בשתי מילים. */
        val stateHe: String
            get() = when {
                disabled -> "מושבת"
                premium && manualPremium -> "פרימיום ידני"
                premium -> "פרימיום"
                trialActive -> "תקופת ניסיון"
                else -> "ללא מנוי"
            }
    }

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
            val levels = s.optJSONObject("byFilterLevel") ?: JSONObject()
            val summary = Summary(
                totalAccounts = s.optInt("totalAccounts"),
                verifiedAccounts = s.optInt("verifiedAccounts"),
                connectedAccounts = s.optInt("connectedAccounts"),
                premiumAccounts = s.optInt("premiumAccounts"),
                trialAccounts = s.optInt("trialAccounts"),
                disabledAccounts = s.optInt("disabledAccounts"),
                pendingRequests = s.optInt("pendingRequests"),
                level1 = levels.optInt("level1"),
                level2 = levels.optInt("level2"),
                level3 = levels.optInt("level3"),
                levelUnset = levels.optInt("unset"),
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
                        displayName = c.optString("displayName"),
                        gender = c.optString("gender"),
                        filterLevel = c.optInt("filterLevel"),
                        onboardingDone = c.optBoolean("onboardingDone"),
                        channelRequests = c.optInt("channelRequests"),
                        pendingChannelRequests = c.optInt("pendingChannelRequests"),
                        premiumRequestPending = c.optBoolean("premiumRequestPending"),
                    ))
                }
            }
            Snapshot(summary, clients)
        }
    }

    /** תוצאה של פעולת ניהול — תמיד עם הסבר, גם בכישלון. */
    data class ActionResult(val ok: Boolean, val message: String)

    /**
     * פעולה על לקוח בודד.
     *
     * [action] — grantPremium / revokePremium / disable / enable / delete.
     * השרת הוא שאוכף מי רשאי ומה מותר; כאן רק שולחים ומדווחים בחזרה.
     */
    suspend fun manage(
        uid: String,
        action: String,
        plan: String? = null,
        days: Int? = null,
    ): ActionResult = withContext(Dispatchers.IO) {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser?.takeIf { it.isEmailVerified }
            ?: return@withContext ActionResult(false, "נדרשת התחברות לחשבון מנהל מאומת")
        val token = user.getIdToken(true).await().token
            ?: return@withContext ActionResult(false, "לא ניתן לאמת את המנהל")
        val payload = JSONObject().apply {
            put("uid", uid)
            put("action", action)
            plan?.let { put("plan", it) }
            days?.let { put("days", it) }
        }.toString()
        val request = Request.Builder()
            .url(MANAGE_API)
            .header("Authorization", "Bearer $token")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        runCatching {
            http.newCall(request).execute().use { response ->
                val root = JSONObject(response.body?.string().orEmpty())
                ActionResult(
                    ok = response.isSuccessful && root.optBoolean("ok", false),
                    message = root.optString("message").ifBlank {
                        if (response.isSuccessful) "בוצע" else "הפעולה נכשלה (${response.code})"
                    },
                )
            }
        }.getOrElse { ActionResult(false, "לא ניתן להתחבר לשרת כרגע") }
    }
}
