package com.filtertube.app.data

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * הגדרות מקומיות + היסטוריית חיפוש — דרך SharedPreferences.
 */
class SettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences("filtertube_settings", Context.MODE_PRIVATE)

    init {
        // Keep the parent's existing device PIN during the one-time security
        // migration. Earlier builds stored it as plaintext; it must be turned
        // into a salted verifier before removing the legacy value.
        val legacyParentPassword = prefs.getString(KEY_FILTER_PW_LEGACY, null)
        val parentPasswordMigrated = if (
            !legacyParentPassword.isNullOrEmpty() && !hasFilterPassword
        ) {
            runCatching { setFilterPassword(legacyParentPassword) }.isSuccess
        } else {
            true
        }

        // Other retired credentials must never remain on the device. The old
        // parent PIN is removed only after it was safely migrated.
        prefs.edit()
            .remove(KEY_GH_TOKEN_LEGACY)
            .remove(KEY_ADMIN_UNLOCKED_LEGACY)
            .remove(KEY_CLOUD_TOKEN_LEGACY)
            .remove(KEY_SERVER_API_KEY_LEGACY)
            .remove(KEY_SERVER_BASE_URL_LEGACY)
            .apply()
        if (parentPasswordMigrated) {
            prefs.edit().remove(KEY_FILTER_PW_LEGACY).apply()
        }
    }

    var shortsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SHORTS, true)
        set(value) = prefs.edit().putBoolean(KEY_SHORTS, value).apply()

    /**
     * רמת סינון:
     *  1 = מחמיר (מוזיקה מתנגנת אודיו בלבד; ערוצי "דתי לייט" מוסתרים)
     *  2 = רגיל (הכל וידאו; ערוצי "דתי לייט" מוסתרים)
     *  3 = דתי לייט (ערוצי "דתי לייט" מוצגים ומתנגנים אודיו בלבד)
     */
    var filterLevel: Int
        get() = prefs.getInt(KEY_LEVEL, 2)
        set(value) = prefs.edit().putInt(KEY_LEVEL, value).apply()

    /**
     * The parent password itself is never persisted. Only a salted PBKDF2
     * verifier is stored locally.
     */
    val hasFilterPassword: Boolean
        get() = !prefs.getString(KEY_FILTER_PW_VERIFIER, "").isNullOrEmpty()

    fun setFilterPassword(password: String) {
        if (password.isEmpty()) {
            clearFilterPassword()
            return
        }
        val salt = ByteArray(PASSWORD_SALT_BYTES).also(SecureRandom()::nextBytes)
        val algorithm = preferredPasswordKdf()
        val verifier = passwordVerifier(password, salt, algorithm)
        prefs.edit()
            .putString(KEY_FILTER_PW_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_FILTER_PW_VERIFIER, Base64.encodeToString(verifier, Base64.NO_WRAP))
            .putString(KEY_FILTER_PW_KDF, algorithm)
            .remove(KEY_FILTER_PW_LEGACY)
            .apply()
    }

    fun checkFilterPassword(input: String): Boolean {
        val encodedVerifier = prefs.getString(KEY_FILTER_PW_VERIFIER, "").orEmpty()
        val encodedSalt = prefs.getString(KEY_FILTER_PW_SALT, "").orEmpty()
        if (encodedVerifier.isNotEmpty() && encodedSalt.isNotEmpty()) {
            return runCatching {
                val expected = Base64.decode(encodedVerifier, Base64.NO_WRAP)
                val salt = Base64.decode(encodedSalt, Base64.NO_WRAP)
                val algorithm = prefs.getString(KEY_FILTER_PW_KDF, PASSWORD_KDF_SHA1)
                    .orEmpty().ifBlank { PASSWORD_KDF_SHA1 }
                MessageDigest.isEqual(expected, passwordVerifier(input, salt, algorithm))
            }.getOrDefault(false)
        }

        return false
    }

    fun clearFilterPassword() {
        prefs.edit()
            .remove(KEY_FILTER_PW_LEGACY)
            .remove(KEY_FILTER_PW_SALT)
            .remove(KEY_FILTER_PW_VERIFIER)
            .remove(KEY_FILTER_PW_KDF)
            .apply()
    }

    /** קצב רענון גבוה (120 הרץ) — תצוגה חלקה במכשירים שתומכים. */
    var highRefreshRate: Boolean
        get() = prefs.getBoolean(KEY_HIGH_HZ, true)
        set(value) = prefs.edit().putBoolean(KEY_HIGH_HZ, value).apply()

    /** הצבע הראשי של האפליקציה (ARGB int). ברירת מחדל #FF2D43 (אמבר). */
    var accentColor: Int
        get() = prefs.getInt(KEY_ACCENT, 0xFFFF2D43.toInt())
        set(value) = prefs.edit().putInt(KEY_ACCENT, value).apply()

    /** הצבע המשני לגרדיאנט (ARGB int). ברירת מחדל #FF6A5C. */
    var accent2Color: Int
        get() = prefs.getInt(KEY_ACCENT2, 0xFFFF6A5C.toInt())
        set(value) = prefs.edit().putInt(KEY_ACCENT2, value).apply()

    /** איכות ניגון מועדפת לפי גובה (px). 0 = אוטומטי (עד 720). */
    var preferredQuality: Int
        get() = prefs.getInt(KEY_QUALITY, 0)
        set(value) = prefs.edit().putInt(KEY_QUALITY, value).apply()

    /** עיצוב הנגן: 1 = "מתנגן עכשיו" (נוכחי), 2 = בקרים על הוידאו + הבא בתור מתחת. */
    var playerStyle: Int
        get() = prefs.getInt(KEY_PLAYER_STYLE, 1)
        set(value) = prefs.edit().putInt(KEY_PLAYER_STYLE, value).apply()

    /** מצב נושא: 0 = לפי המערכת, 1 = כהה, 2 = בהיר. */
    var themeMode: Int
        get() = prefs.getInt(KEY_THEME_MODE, 1)
        set(value) = prefs.edit().putInt(KEY_THEME_MODE, value).apply()

    /** התראות על סרטון חדש בערוץ מאושר (בדיקת רקע תקופתית). */
    var newVideoNotifications: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFY, value).apply()

    /** כמה הורדות לרוץ במקביל (1–4). */
    var concurrentDownloads: Int
        get() = prefs.getInt(KEY_DL_CONCURRENT, 3).coerceIn(1, 4)
        set(value) = prefs.edit().putInt(KEY_DL_CONCURRENT, value.coerceIn(1, 4)).apply()

    /** כמה חיבורים מקביליים לכל קובץ (1–8) — מאיץ הורדה שנחנקת ע"י ה-CDN. */
    var connectionsPerDownload: Int
        get() = prefs.getInt(KEY_DL_CONNECTIONS, 4).coerceIn(1, 8)
        set(value) = prefs.edit().putInt(KEY_DL_CONNECTIONS, value.coerceIn(1, 8)).apply()

    /** הורדה אוטומטית של כל סרטון ש"אהבתי". */
    var autoDownloadLikes: Boolean
        get() = prefs.getBoolean(KEY_DL_AUTO_LIKES, false)
        set(value) = prefs.edit().putBoolean(KEY_DL_AUTO_LIKES, value).apply()

    /** המשך ניגון כשהאפליקציה ברקע. כבוי = עצירה ביציאה מהאפליקציה. */
    var backgroundPlay: Boolean
        get() = prefs.getBoolean(KEY_BG_PLAY, true)
        set(value) = prefs.edit().putBoolean(KEY_BG_PLAY, value).apply()

    // ── הרשמה / פרופיל ──────────────────────────────────────────────────
    /** האם המשתמש סיים את מסך ההרשמה הראשוני. */
    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(value) = prefs.edit().putBoolean(KEY_ONBOARDED, value).apply()

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var userEmail: String
        get() = prefs.getString(KEY_USER_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()

    /** "male" / "female". */
    var userGender: String
        get() = prefs.getString(KEY_USER_GENDER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_GENDER, value).apply()

    var cloudEmail: String
        get() = prefs.getString(KEY_CLOUD_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CLOUD_EMAIL, value).apply()

    var cloudUid: String
        get() = prefs.getString(KEY_CLOUD_UID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CLOUD_UID, value).apply()

    /** Paid entitlement reported by the server and bounded by a monotonic cache. */
    val premiumServerActive: Boolean
        get() {
            if (!hasVerifiedAccount()) return false
            if (!prefs.getBoolean(KEY_PREMIUM_SERVER_ACTIVE, false)) return false
            val serverNow = cachedServerNowEpochSeconds() ?: return false
            val periodEnd = prefs.getLong(KEY_PREMIUM_PERIOD_END_SECONDS, 0L)
            return periodEnd <= 0L || serverNow < periodEnd
        }

    val premiumStatus: String
        get() = if (hasVerifiedAccount() && hasValidEntitlementCache()) {
            prefs.getString(KEY_PREMIUM_STATUS, "").orEmpty()
        } else {
            ""
        }

    val premiumCanManage: Boolean
        get() = hasVerifiedAccount() &&
            hasValidEntitlementCache() &&
            prefs.getBoolean(KEY_PREMIUM_CAN_MANAGE, false)

    fun updatePremiumEntitlement(
        expectedUid: String,
        active: Boolean,
        status: String,
        canManage: Boolean,
        currentPeriodEndEpochSeconds: Long,
        trialActive: Boolean,
        trialEndsAtEpochSeconds: Long,
        serverNowEpochSeconds: Long,
    ): Boolean {
        if (!hasVerifiedAccount(expectedUid)) return false
        val bootCount = currentBootCount()
        val elapsedRealtime = SystemClock.elapsedRealtime()
        val validServerClock =
            serverNowEpochSeconds in 1L..MAX_SERVER_EPOCH_SECONDS &&
                trialEndsAtEpochSeconds in 1L..MAX_SERVER_EPOCH_SECONDS &&
                currentPeriodEndEpochSeconds in 0L..MAX_SERVER_EPOCH_SECONDS &&
                trialActive == (serverNowEpochSeconds < trialEndsAtEpochSeconds) &&
                bootCount >= 0 &&
                elapsedRealtime >= 0L
        if (!validServerClock) {
            clearPremiumEntitlement()
            return false
        }
        prefs.edit()
            .putBoolean(KEY_PREMIUM_SERVER_ACTIVE, active)
            .putString(KEY_PREMIUM_STATUS, status)
            .putBoolean(KEY_PREMIUM_CAN_MANAGE, canManage)
            .putLong(KEY_PREMIUM_PERIOD_END_SECONDS, currentPeriodEndEpochSeconds)
            .putBoolean(KEY_TRIAL_SERVER_ACTIVE, trialActive)
            .putLong(KEY_TRIAL_END_SECONDS, trialEndsAtEpochSeconds)
            .putLong(KEY_ENTITLEMENT_SERVER_NOW_SECONDS, serverNowEpochSeconds)
            .putLong(KEY_ENTITLEMENT_ELAPSED_REALTIME_MILLIS, elapsedRealtime)
            .putInt(KEY_ENTITLEMENT_BOOT_COUNT, bootCount)
            .apply()
        return true
    }

    fun clearPremiumEntitlement() {
        prefs.edit()
            .remove(KEY_PREMIUM_SERVER_ACTIVE)
            .remove(KEY_PREMIUM_STATUS)
            .remove(KEY_PREMIUM_CAN_MANAGE)
            .remove(KEY_PREMIUM_VERIFIED_AT_LEGACY)
            .remove(KEY_PREMIUM_PERIOD_END_LEGACY)
            .remove(KEY_PREMIUM_PERIOD_END_SECONDS)
            .remove(KEY_TRIAL_SERVER_ACTIVE)
            .remove(KEY_TRIAL_END_SECONDS)
            .remove(KEY_ENTITLEMENT_SERVER_NOW_SECONDS)
            .remove(KEY_ENTITLEMENT_ELAPSED_REALTIME_MILLIS)
            .remove(KEY_ENTITLEMENT_BOOT_COUNT)
            .apply()
    }

    /**
     * Claims the local account-scoped data for [uid]. Anonymous local data can
     * migrate to the first Firebase account. Data owned by the retired server
     * may migrate only when its email exactly matches the verified Firebase
     * email; otherwise it is cleared before any cloud upload.
     */
    fun bindAccountDataOwner(uid: String, verifiedEmail: String) {
        val normalizedEmail = verifiedEmail.trim().lowercase(Locale.ROOT)
        if (uid.isBlank() || normalizedEmail.isBlank()) return
        AccountDataGuard.withLock {
            val previousUid = prefs.getString(KEY_ACCOUNT_DATA_OWNER_UID, "").orEmpty()
            val legacyUid = prefs.getString(KEY_CLOUD_UID, "").orEmpty()
            val legacyEmail = prefs.getString(KEY_CLOUD_EMAIL, "")
                .orEmpty()
                .trim()
                .lowercase(Locale.ROOT)
            val accountChanged = previousUid.isNotBlank() && previousUid != uid
            val unsafeLegacyMigration =
                previousUid.isBlank() &&
                    legacyUid.isNotBlank() &&
                    legacyEmail != normalizedEmail
            if (previousUid != uid) AccountDataGuard.invalidate()
            if (accountChanged || unsafeLegacyMigration) {
                val staleUid = previousUid.ifBlank { legacyUid }
                CloudSync.cancelUploads(appContext, staleUid)
                AccountStore(appContext).logout()
                GoogleAuth.signOut(appContext)
                LibraryStore(appContext).clearAccountData()
                clearAccountScopedData()
            }
            prefs.edit().putString(KEY_ACCOUNT_DATA_OWNER_UID, uid).apply()
        }
    }

    /** Clears data that must never cross from one Firebase account to another. */
    fun clearAccountScopedData() {
        AccountDataGuard.withLock {
            AccountDataGuard.invalidate()
            prefs.edit()
                .remove(KEY_USER_NAME)
                .remove(KEY_USER_EMAIL)
                .remove(KEY_USER_GENDER)
                .remove(KEY_CLOUD_EMAIL)
                .remove(KEY_CLOUD_UID)
                .remove(KEY_TRIAL_START_LEGACY)
                .remove(KEY_HISTORY)
                .remove(KEY_LEVEL)
                .remove(KEY_GH_TOKEN_LEGACY)
                .remove(KEY_ADMIN_UNLOCKED_LEGACY)
                .remove(KEY_CLOUD_TOKEN_LEGACY)
                .remove(KEY_SERVER_API_KEY_LEGACY)
                .remove(KEY_SERVER_BASE_URL_LEGACY)
                .remove(KEY_PREMIUM_SERVER_ACTIVE)
                .remove(KEY_PREMIUM_STATUS)
                .remove(KEY_PREMIUM_CAN_MANAGE)
                .remove(KEY_PREMIUM_VERIFIED_AT_LEGACY)
                .remove(KEY_PREMIUM_PERIOD_END_LEGACY)
                .remove(KEY_PREMIUM_PERIOD_END_SECONDS)
                .remove(KEY_TRIAL_SERVER_ACTIVE)
                .remove(KEY_TRIAL_END_SECONDS)
                .remove(KEY_ENTITLEMENT_SERVER_NOW_SECONDS)
                .remove(KEY_ENTITLEMENT_ELAPSED_REALTIME_MILLIS)
                .remove(KEY_ENTITLEMENT_BOOT_COUNT)
                .remove(KEY_ACCOUNT_DATA_OWNER_UID)
                .apply()
        }
    }

    /** Premium is active only from a fresh server-paid or server-trial cache. */
    val premiumActive: Boolean
        get() {
            if (!hasVerifiedAccount()) return false
            return premiumServerActive || serverTrialActive
        }

    /** Remaining trial days, capped by the current client policy. */
    val trialDaysLeft: Int
        get() {
            if (!hasVerifiedAccount()) return 0
            val serverNow = cachedServerNowEpochSeconds() ?: return 0
            if (!prefs.getBoolean(KEY_TRIAL_SERVER_ACTIVE, false)) return 0
            val trialEndsAt = effectiveTrialEndsAtEpochSeconds()
            val remainingSeconds = trialEndsAt - serverNow
            if (remainingSeconds <= 0L) return 0
            return ((remainingSeconds + SECONDS_PER_DAY - 1L) / SECONDS_PER_DAY)
                .coerceIn(0L, TRIAL_DURATION_DAYS)
                .toInt()
        }

    private val serverTrialActive: Boolean
        get() {
            if (!prefs.getBoolean(KEY_TRIAL_SERVER_ACTIVE, false)) return false
            val serverNow = cachedServerNowEpochSeconds() ?: return false
            return serverNow < effectiveTrialEndsAtEpochSeconds()
        }

    /**
     * Never let an older server response extend a trial beyond the policy in
     * this build. Server time is still used for the comparison, so changing
     * the device clock cannot extend the entitlement.
     */
    private fun effectiveTrialEndsAtEpochSeconds(): Long {
        val serverTrialEnd = prefs.getLong(KEY_TRIAL_END_SECONDS, 0L)
        if (serverTrialEnd <= 0L) return 0L
        val creationMillis = FirebaseAuth.getInstance()
            .currentUser
            ?.metadata
            ?.creationTimestamp
            ?: return serverTrialEnd
        if (creationMillis <= 0L) return serverTrialEnd
        val policyTrialEnd = creationMillis / 1000L +
            TRIAL_DURATION_DAYS * SECONDS_PER_DAY
        return minOf(serverTrialEnd, policyTrialEnd)
    }

    private fun hasVerifiedAccount(expectedUid: String? = null): Boolean {
        val user = FirebaseAuth.getInstance().currentUser
        return user != null &&
            user.isEmailVerified &&
            cloudUid == user.uid &&
            (expectedUid == null || user.uid == expectedUid)
    }

    private fun hasValidEntitlementCache() = cachedServerNowEpochSeconds() != null

    private fun cachedServerNowEpochSeconds(): Long? {
        val currentBootCount = currentBootCount()
        val storedBootCount = prefs.getInt(KEY_ENTITLEMENT_BOOT_COUNT, -1)
        if (currentBootCount < 0 || storedBootCount != currentBootCount) return null
        val anchorElapsedRealtime =
            prefs.getLong(KEY_ENTITLEMENT_ELAPSED_REALTIME_MILLIS, -1L)
        val nowElapsedRealtime = SystemClock.elapsedRealtime()
        val cacheAgeMillis = nowElapsedRealtime - anchorElapsedRealtime
        if (
            anchorElapsedRealtime < 0L ||
            cacheAgeMillis < 0L ||
            cacheAgeMillis > PREMIUM_CACHE_TTL_MILLIS
        ) {
            return null
        }
        val serverNowAtAnchor = prefs.getLong(KEY_ENTITLEMENT_SERVER_NOW_SECONDS, 0L)
        if (serverNowAtAnchor !in 1L..MAX_SERVER_EPOCH_SECONDS) return null
        return runCatching {
            Math.addExact(serverNowAtAnchor, cacheAgeMillis / 1_000L)
        }.getOrNull()
    }

    private fun currentBootCount(): Int = runCatching {
        AndroidSettings.Global.getInt(
            appContext.contentResolver,
            AndroidSettings.Global.BOOT_COUNT,
            -1,
        )
    }.getOrDefault(-1)

    /** צורת פס ההתקדמות בנגן: 0 = ישר, 1 = גלי, 2 = זיגזג. */
    var seekBarShape: Int
        get() = prefs.getInt(KEY_SEEK_SHAPE, 1)
        set(value) = prefs.edit().putInt(KEY_SEEK_SHAPE, value).apply()

    /** עובי פס ההתקדמות (1–6 dp לערך). */
    var seekBarThickness: Int
        get() = prefs.getInt(KEY_SEEK_THICK, 3).coerceIn(1, 6)
        set(value) = prefs.edit().putInt(KEY_SEEK_THICK, value.coerceIn(1, 6)).apply()

    /** זוהר (glow) סביב פס ההתקדמות. */
    var seekBarGlow: Boolean
        get() = prefs.getBoolean(KEY_SEEK_GLOW, true)
        set(value) = prefs.edit().putBoolean(KEY_SEEK_GLOW, value).apply()

    // ── היסטוריית חיפוש ──────────────────────────────────────────────────
    fun getSearchHistory(): List<String> {
        val raw = prefs.getString(KEY_HISTORY, "") ?: ""
        return raw.split("\n").filter { it.isNotBlank() }
    }

    fun addSearchQuery(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val current = getSearchHistory().toMutableList()
        current.remove(q)              // הסר כפילות
        current.add(0, q)              // הוסף בראש
        val trimmed = current.take(20) // שמור עד 20 אחרונים
        prefs.edit().putString(KEY_HISTORY, trimmed.joinToString("\n")).apply()
        CloudSync.enqueueUpload(appContext)
    }

    fun removeSearchQuery(query: String) {
        val current = getSearchHistory().toMutableList()
        current.remove(query)
        prefs.edit().putString(KEY_HISTORY, current.joinToString("\n")).apply()
        CloudSync.enqueueUpload(appContext)
    }

    fun clearSearchHistory() {
        prefs.edit().remove(KEY_HISTORY).apply()
        CloudSync.enqueueUpload(appContext)
    }

    /** Used by a cloud restore; do not schedule a redundant upload. */
    fun replaceSearchHistory(queries: List<String>) {
        prefs.edit().putString(
            KEY_HISTORY,
            queries.asSequence().map { it.trim() }.filter { it.isNotBlank() }.distinct().take(20).joinToString("\n"),
        ).apply()
    }

    private fun preferredPasswordKdf(): String =
        if (runCatching { SecretKeyFactory.getInstance(PASSWORD_KDF_SHA256) }.isSuccess) {
            PASSWORD_KDF_SHA256
        } else {
            PASSWORD_KDF_SHA1
        }

    private fun passwordVerifier(password: String, salt: ByteArray, algorithm: String): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, PASSWORD_KDF_ITERATIONS, PASSWORD_HASH_BITS)
        return try {
            SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    companion object {
        private const val KEY_SHORTS = "shorts_enabled"
        private const val KEY_HISTORY = "search_history"
        private const val KEY_LEVEL = "filter_level"
        private const val KEY_GH_TOKEN_LEGACY = "github_token"
        private const val KEY_FILTER_PW_LEGACY = "filter_password"
        private const val KEY_FILTER_PW_SALT = "filter_password_salt"
        private const val KEY_FILTER_PW_VERIFIER = "filter_password_verifier"
        private const val KEY_FILTER_PW_KDF = "filter_password_kdf"
        private const val KEY_HIGH_HZ = "high_refresh_rate"
        private const val KEY_ACCENT = "accent_color"
        private const val KEY_ACCENT2 = "accent2_color"
        private const val KEY_QUALITY = "preferred_quality"
        private const val KEY_PLAYER_STYLE = "player_style"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_ADMIN_UNLOCKED_LEGACY = "admin_unlocked"
        private const val KEY_NOTIFY = "new_video_notifications"
        private const val KEY_DL_CONCURRENT = "dl_concurrent"
        private const val KEY_DL_CONNECTIONS = "dl_connections"
        private const val KEY_DL_AUTO_LIKES = "dl_auto_likes"
        private const val KEY_BG_PLAY = "background_play"
        private const val KEY_ONBOARDED = "onboarding_done"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_GENDER = "user_gender"
        private const val KEY_CLOUD_EMAIL = "cloud_email"
        private const val KEY_CLOUD_UID = "cloud_uid"
        private const val KEY_CLOUD_TOKEN_LEGACY = "cloud_token"
        private const val KEY_SERVER_API_KEY_LEGACY = "server_api_key"
        private const val KEY_SERVER_BASE_URL_LEGACY = "server_base_url"
        private const val KEY_TRIAL_START_LEGACY = "trial_start_millis"
        private const val KEY_PREMIUM_SERVER_ACTIVE = "premium_server_active"
        private const val KEY_PREMIUM_STATUS = "premium_server_status"
        private const val KEY_PREMIUM_CAN_MANAGE = "premium_can_manage"
        private const val KEY_PREMIUM_VERIFIED_AT_LEGACY = "premium_verified_at"
        private const val KEY_PREMIUM_PERIOD_END_LEGACY = "premium_period_end"
        private const val KEY_PREMIUM_PERIOD_END_SECONDS = "premium_period_end_seconds_v2"
        private const val KEY_TRIAL_SERVER_ACTIVE = "trial_server_active_v2"
        private const val KEY_TRIAL_END_SECONDS = "trial_end_seconds_v2"
        private const val KEY_ENTITLEMENT_SERVER_NOW_SECONDS = "entitlement_server_now_seconds_v2"
        private const val KEY_ENTITLEMENT_ELAPSED_REALTIME_MILLIS =
            "entitlement_elapsed_realtime_millis_v2"
        private const val KEY_ENTITLEMENT_BOOT_COUNT = "entitlement_boot_count_v2"
        private const val KEY_ACCOUNT_DATA_OWNER_UID = "account_data_owner_uid"
        private const val KEY_SEEK_SHAPE = "seek_bar_shape"
        private const val KEY_SEEK_THICK = "seek_bar_thickness"
        private const val KEY_SEEK_GLOW = "seek_bar_glow"

        private const val PASSWORD_KDF_SHA256 = "PBKDF2WithHmacSHA256"
        private const val PASSWORD_KDF_SHA1 = "PBKDF2WithHmacSHA1"
        private const val PASSWORD_KDF_ITERATIONS = 120_000
        private const val PASSWORD_HASH_BITS = 256
        private const val PASSWORD_SALT_BYTES = 16
        private const val PREMIUM_CACHE_TTL_MILLIS = 24L * 60L * 60L * 1000L
        private const val MAX_SERVER_EPOCH_SECONDS = 10_000_000_000L
        private const val SECONDS_PER_DAY = 24L * 60L * 60L
        private const val TRIAL_DURATION_DAYS = 30L
    }
}
