package com.filtertube.app.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Billing client for the Firebase Functions Creem integration.
 *
 * No Creem secret is present here. The app sends a Firebase ID token to the
 * function, and only the server talks to Creem.
 */
object FirebaseBilling {
    private const val TAG = "FirebaseBilling"
    private const val CREATE_CHECKOUT =
        "https://europe-west1-filter-tube-52d8e.cloudfunctions.net/createCheckout"
    private const val CREATE_CUSTOMER_PORTAL =
        "https://europe-west1-filter-tube-52d8e.cloudfunctions.net/createCustomerPortal"
    private const val BILLING_STATUS =
        "https://europe-west1-filter-tube-52d8e.cloudfunctions.net/billingStatus"
    private const val TRIAL_STATUS =
        "https://europe-west1-filter-tube-52d8e.cloudfunctions.net/trialStatus"
    private val client = OkHttpClient()

    data class Result(val ok: Boolean, val message: String, val url: String? = null)
    private data class UserToken(val uid: String, val token: String)

    private suspend fun userToken(): UserToken? {
        return try {
            val user = FirebaseAuth.getInstance().currentUser
                ?.takeIf { it.isEmailVerified }
                ?: return null
            val token = user.getIdToken(false).await().token ?: return null
            val current = FirebaseAuth.getInstance().currentUser
            if (current?.uid != user.uid || !current.isEmailVerified) return null
            UserToken(user.uid, token)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Firebase ID token retrieval failed", error)
            null
        }
    }

    suspend fun createCheckout(plan: String): Result = withContext(Dispatchers.IO) {
        val auth = userToken() ?: return@withContext Result(false, "יש להתחבר לחשבון לפני רכישה")
        val body = JSONObject().put("plan", plan).toString()
            .toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder().url(CREATE_CHECKOUT)
            .header("Authorization", "Bearer ${auth.token}")
            .post(body).build()
        runCatching {
            client.newCall(request).execute().use { response ->
                val json = JSONObject(response.body?.string().orEmpty())
                if (FirebaseAuth.getInstance().currentUser?.uid != auth.uid) {
                    return@withContext Result(false, "החשבון השתנה במהלך פתיחת התשלום")
                }
                if (!response.isSuccessful || !json.optBoolean("ok", false)) {
                    return@withContext Result(false, json.optString("message", "לא ניתן להתחיל תשלום"))
                }
                Result(true, "דף התשלום נפתח בדפדפן", json.optString("checkoutUrl").takeIf { it.isNotBlank() })
            }
        }.getOrElse {
            Log.e(TAG, "createCheckout failed", it)
            Result(false, "לא ניתן להתחבר לשירות התשלומים כרגע")
        }
    }

    suspend fun createCustomerPortal(): Result = withContext(Dispatchers.IO) {
        val auth = userToken() ?: return@withContext Result(false, "יש להתחבר לחשבון לפני ניהול המנוי")
        val request = Request.Builder().url(CREATE_CUSTOMER_PORTAL)
            .header("Authorization", "Bearer ${auth.token}")
            .post("{}".toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                val json = JSONObject(response.body?.string().orEmpty())
                if (FirebaseAuth.getInstance().currentUser?.uid != auth.uid) {
                    return@withContext Result(false, "החשבון השתנה במהלך פתיחת ניהול המנוי")
                }
                if (!response.isSuccessful || !json.optBoolean("ok", false)) {
                    return@withContext Result(false, json.optString("message", "לא ניתן לפתוח את ניהול המנוי"))
                }
                Result(true, "ניהול המנוי נפתח בדפדפן", json.optString("portalUrl").takeIf { it.isNotBlank() })
            }
        }.getOrElse {
            Log.e(TAG, "createCustomerPortal failed", it)
            Result(false, "לא ניתן להתחבר לשירות ניהול המנוי כרגע")
        }
    }

    suspend fun refresh(settings: SettingsStore): Result = withContext(Dispatchers.IO) {
        val auth = userToken() ?: return@withContext Result(false, "יש להתחבר לחשבון כדי לבדוק זכאות")
        if (settings.cloudUid != auth.uid) {
            return@withContext Result(false, "יש להשלים את טעינת החשבון לפני בדיקת הזכאות")
        }
        val reconciledBilling = runCatching {
            fetchBillingSnapshot(BILLING_STATUS, auth)
        }.onFailure {
            Log.e(TAG, "billing reconciliation failed", it)
        }.getOrNull()
        val usedTrialFallback = reconciledBilling == null
        val billing = reconciledBilling ?: runCatching {
            // Trial initialization is independent from Creem. It also returns
            // the last server-owned paid snapshot without contacting Creem.
            fetchBillingSnapshot(TRIAL_STATUS, auth)
        }.onFailure {
            Log.e(TAG, "trial entitlement refresh failed", it)
        }.getOrNull()

        val current = FirebaseAuth.getInstance().currentUser
        if (
            current?.uid != auth.uid ||
            !current.isEmailVerified ||
            settings.cloudUid != auth.uid
        ) {
            return@withContext Result(false, "החשבון השתנה במהלך בדיקת הזכאות")
        }
        if (billing == null) {
            return@withContext Result(false, "לא ניתן לבדוק את מצב המנוי כרגע")
        }
        val cached = settings.updatePremiumEntitlement(
            expectedUid = auth.uid,
            active = billing.optBoolean("active", false),
            status = billing.optString("status", ""),
            canManage = billing.optBoolean("canManage", false),
            currentPeriodEndEpochSeconds = billing.optLong("currentPeriodEnd", 0L),
            trialActive = billing.optBoolean("trialActive", false),
            trialEndsAtEpochSeconds = billing.optLong("trialEndsAt", 0L),
            serverNowEpochSeconds = billing.optLong("serverNow", 0L),
        )
        if (!cached) {
            return@withContext Result(false, "השרת החזיר זמן זכאות לא תקין")
        }
        Result(
            ok = true,
            message = when {
                settings.premiumServerActive -> "מנוי Premium פעיל ✓"
                settings.trialDaysLeft > 0 && usedTrialFallback ->
                    "תקופת הניסיון פעילה — נותרו ${settings.trialDaysLeft} ימים"
                settings.trialDaysLeft > 0 ->
                    "תקופת הניסיון פעילה — נותרו ${settings.trialDaysLeft} ימים"
                else -> "לא נמצא מנוי פעיל ותקופת הניסיון הסתיימה"
            },
        )
    }

    private fun fetchBillingSnapshot(url: String, auth: UserToken): JSONObject? {
        val request = Request.Builder().url(url)
            .header("Authorization", "Bearer ${auth.token}")
            .get()
            .build()
        return client.newCall(request).execute().use { response ->
            val json = runCatching {
                JSONObject(response.body?.string().orEmpty())
            }.getOrNull() ?: return@use null
            if (!response.isSuccessful || !json.optBoolean("ok", false)) {
                return@use null
            }
            json.optJSONObject("billing")
        }
    }

    fun openBrowser(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
