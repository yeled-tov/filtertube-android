package com.filtertube.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object ServerBilling {
    private val client = OkHttpClient()

    suspend fun createCheckout(settings: SettingsStore, plan: String, method: String): CheckoutResult = withContext(Dispatchers.IO) {
        val baseUrl = settings.serverBaseUrl.trim().removeSuffix("/")
        if (baseUrl.isBlank()) {
            return@withContext CheckoutResult(false, null, "לא הוגדרה כתובת שרת תשלומים. הוסף כתובת שרת בהגדרות כדי להתחיל תהליך תשלום.")
        }

        val payload = JSONObject().apply {
            put("plan", plan)
            put("method", method)
            put("app", "filtertube")
            put("device", "android")
        }
        val request = Request.Builder()
            .url("$baseUrl/api/premium/checkout")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext CheckoutResult(false, null, "השרת החזיר שגיאה ${response.code}: $body")
                }
                val json = JSONObject(body)
                CheckoutResult(
                    ok = json.optBoolean("ok", false),
                    checkoutUrl = json.optString("checkoutUrl", "").takeIf { it.isNotBlank() },
                    message = json.optString("message", "התחל תהליך תשלום מאובטח")
                )
            }
        }.getOrElse {
            CheckoutResult(false, null, "לא ניתן להתחבר לשרת התשלומים כרגע. בדוק את ה-URL וה-API key.")
        }
    }

    suspend fun verifyPurchase(settings: SettingsStore, plan: String, method: String): VerifyResult = withContext(Dispatchers.IO) {
        val baseUrl = settings.serverBaseUrl.trim().removeSuffix("/")
        if (baseUrl.isBlank()) {
            return@withContext VerifyResult(false, "לא הוגדרה כתובת שרת תשלומים.")
        }

        val payload = JSONObject().apply {
            put("plan", plan)
            put("method", method)
            put("app", "filtertube")
            put("device", "android")
            put("apiKey", settings.serverApiKey)
        }
        val request = Request.Builder()
            .url("$baseUrl/api/premium/verify")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext VerifyResult(false, "השרת החזיר שגיאה ${response.code}: $body")
                }
                val json = JSONObject(body)
                VerifyResult(
                    ok = json.optBoolean("ok", false),
                    message = json.optString("message", "אימות התשלום הושלם")
                )
            }
        }.getOrElse {
            VerifyResult(false, "לא ניתן לאמת את התשלום בשרת כרגע.")
        }
    }

    suspend fun ping(settings: SettingsStore): String = withContext(Dispatchers.IO) {
        val baseUrl = settings.serverBaseUrl.trim().removeSuffix("/")
        if (baseUrl.isBlank()) return@withContext "לא הוגדרה כתובת שרת"

        runCatching {
            val request = Request.Builder().url("$baseUrl/api/health").get().build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> body.ifBlank { "השרת מחובר ✅" }
                    else -> "שגיאה ${response.code}: $body"
                }
            }
        }.getOrElse { "לא ניתן להתחבר לשרת" }
    }

    data class CheckoutResult(
        val ok: Boolean,
        val checkoutUrl: String?,
        val message: String,
    )

    data class VerifyResult(
        val ok: Boolean,
        val message: String,
    )
}
