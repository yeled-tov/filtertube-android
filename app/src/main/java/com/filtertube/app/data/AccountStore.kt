package com.filtertube.app.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebStorage
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores the YouTube WebView session used by InnerTube.
 *
 * Cookie values are encrypted with a non-exportable Android Keystore key. The
 * session is local to the current Firebase account and is cleared on account
 * switch or sign-out.
 */
class AccountStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "filtertube_account",
        Context.MODE_PRIVATE,
    )

    var cookies: String
        get() {
            val ciphertext = prefs.getString(KEY_COOKIES_CIPHERTEXT, "").orEmpty()
            val iv = prefs.getString(KEY_COOKIES_IV, "").orEmpty()
            if (ciphertext.isNotBlank() && iv.isNotBlank()) {
                return decrypt(ciphertext, iv) ?: run {
                    clearStoredSession()
                    ""
                }
            }

            // One-time migration from builds that persisted plaintext cookies.
            val legacy = prefs.getString(KEY_COOKIES_LEGACY, "").orEmpty()
            if (legacy.isBlank()) return ""
            return if (saveEncrypted(legacy)) {
                prefs.edit().remove(KEY_COOKIES_LEGACY).apply()
                legacy
            } else {
                clearStoredSession()
                ""
            }
        }
        set(value) {
            if (value.isBlank()) {
                clearStoredSession()
            } else if (!saveEncrypted(value)) {
                clearStoredSession()
            }
        }

    var authUser: Int
        get() = prefs.getInt(KEY_AUTHUSER, 0)
        set(value) = prefs.edit().putInt(KEY_AUTHUSER, value).apply()

    val isLoggedIn: Boolean
        get() = cookies.let {
            it.contains("SAPISID") || it.contains("__Secure-3PAPISID")
        }

    fun logout(onWebSessionCleared: (() -> Unit)? = null) {
        clearStoredSession()
        clearBrowserSession(onWebSessionCleared)
    }

    private fun saveEncrypted(value: String): Boolean {
        if (encryptAndSave(value)) return true
        deleteKey()
        return encryptAndSave(value)
    }

    private fun encryptAndSave(value: String): Boolean = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(
                KEY_COOKIES_CIPHERTEXT,
                Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            )
            .putString(
                KEY_COOKIES_IV,
                Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            )
            .remove(KEY_COOKIES_LEGACY)
            .apply()
        true
    }.getOrDefault(false)

    private fun decrypt(ciphertext: String, iv: String): String? {
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(
                    GCM_TAG_LENGTH_BITS,
                    Base64.decode(iv, Base64.NO_WRAP),
                ),
            )
            val plaintext = cipher.doFinal(
                Base64.decode(ciphertext, Base64.NO_WRAP),
            )
            plaintext.toString(Charsets.UTF_8)
        }.getOrElse {
            deleteKey()
            null
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private fun deleteKey() {
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply {
                load(null)
                if (containsAlias(KEY_ALIAS)) deleteEntry(KEY_ALIAS)
            }
        }
    }

    private fun clearStoredSession() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_COOKIES_LEGACY = "cookies"
        private const val KEY_COOKIES_CIPHERTEXT = "cookies_ciphertext_v2"
        private const val KEY_COOKIES_IV = "cookies_iv_v2"
        private const val KEY_AUTHUSER = "authuser"
        private const val KEY_ALIAS = "filtertube_youtube_cookie_key_v2"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128

        fun clearBrowserSession(onCleared: (() -> Unit)? = null) {
            Handler(Looper.getMainLooper()).post {
                val cookieManager = CookieManager.getInstance()
                cookieManager.removeAllCookies {
                    cookieManager.flush()
                    WebStorage.getInstance().deleteAllData()
                    onCleared?.invoke()
                }
            }
        }
    }
}
