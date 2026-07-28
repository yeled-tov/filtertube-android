package com.filtertube.app.data

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object FirebaseAccount {
    private const val TAG = "FirebaseAccount"

    data class Result(
        val ok: Boolean,
        val message: String,
        val created: Boolean = false,
        val verificationPending: Boolean = false,
        val email: String? = null,
    )

    /**
     * Creates a new account only. Registration and sign-in are deliberately
     * separate so the UI never surprises a user by doing the other operation.
     */
    suspend fun register(email: String, password: String, settings: SettingsStore): Result {
        val normalized = email.trim().lowercase()
        validateCredentials(normalized, password)?.let { return it }
        val auth = FirebaseAuth.getInstance()
        return try {
            val user = auth.createUserWithEmailAndPassword(
                normalized,
                firebasePassword(normalized, password),
            ).await().user ?: return Result(false, "לא ניתן ליצור את החשבון כרגע")
            saveParentPasscode(settings, password)
            if (!user.isEmailVerified) {
                val message = try {
                    user.sendEmailVerification().await()
                    "החשבון נוצר. שלחנו קישור אימות ל־$normalized."
                } catch (error: CancellationException) {
                    invalidateAndSignOutAuthentication()
                    throw error
                } catch (error: Exception) {
                    verificationErrorMessage(error)
                }
                return Result(
                    ok = false,
                    message = message,
                    created = true,
                    verificationPending = true,
                    email = normalized,
                )
            }
            initializeVerifiedAccount(user, normalized, settings, created = true)
        } catch (error: FirebaseAuthUserCollisionException) {
            invalidateAndSignOutAuthentication()
            Result(
                ok = false,
                message = "כבר קיים חשבון עם המייל הזה. בחר „כניסה לחשבון”.",
                email = normalized,
            )
        } catch (error: CancellationException) {
            invalidateAndSignOutAuthentication()
            throw error
        } catch (error: Exception) {
            invalidateAndSignOutAuthentication()
            Log.e(TAG, "account registration failed", error)
            Result(false, registrationErrorMessage(error), email = normalized)
        }
    }

    /** Signs in to an existing account without ever creating a new one. */
    suspend fun signIn(email: String, password: String, settings: SettingsStore): Result {
        val normalized = email.trim().lowercase()
        validateCredentials(normalized, password)?.let { return it }
        val auth = FirebaseAuth.getInstance()
        return try {
            val firebasePassword = firebasePassword(normalized, password)
            val user = try {
                auth.signInWithEmailAndPassword(normalized, firebasePassword).await().user
            } catch (derivedCredentialError: FirebaseAuthInvalidCredentialsException) {
                // Accounts from older versions used the user-entered Firebase
                // password directly. Migrate it once after a successful login.
                if (password.length < LEGACY_FIREBASE_PASSWORD_MIN_LENGTH) {
                    throw derivedCredentialError
                }
                val legacyUser = auth.signInWithEmailAndPassword(normalized, password).await().user
                    ?: throw derivedCredentialError
                legacyUser.updatePassword(firebasePassword).await()
                legacyUser
            } ?: return Result(false, "לא ניתן להתחבר לחשבון כרגע")
            saveParentPasscode(settings, password)
            if (!user.isEmailVerified) {
                return Result(
                    ok = false,
                    message = "החשבון קיים, אך המייל עדיין לא אומת.",
                    verificationPending = true,
                    email = normalized,
                )
            }
            initializeVerifiedAccount(user, normalized, settings, created = false)
        } catch (error: CancellationException) {
            invalidateAndSignOutAuthentication()
            throw error
        } catch (error: Exception) {
            invalidateAndSignOutAuthentication()
            Log.e(TAG, "account sign-in failed", error)
            Result(false, signInErrorMessage(error), email = normalized)
        }
    }

    suspend fun signInOrRegister(email: String, password: String, settings: SettingsStore): Result {
        val normalized = email.trim().lowercase()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(normalized).matches()) {
            return Result(false, "כתובת אימייל לא תקינה")
        }
        if (password.length < PARENT_PASSCODE_MIN_LENGTH) {
            return Result(false, "הקוד חייב להכיל לפחות 4 תווים")
        }
        val auth = FirebaseAuth.getInstance()
        return try {
            val firebasePassword = firebasePassword(normalized, password)
            var created = true
            val user = try {
                auth.createUserWithEmailAndPassword(normalized, firebasePassword).await().user
            } catch (_: FirebaseAuthUserCollisionException) {
                created = false
                try {
                    auth.signInWithEmailAndPassword(normalized, firebasePassword).await().user
                } catch (derivedCredentialError: FirebaseAuthInvalidCredentialsException) {
                    // Accounts created by earlier builds used the user-entered
                    // Firebase password directly. Let an existing 6+ character
                    // password sign in once, then convert it to the unified
                    // parent/account passcode format.
                    if (password.length < LEGACY_FIREBASE_PASSWORD_MIN_LENGTH) {
                        throw derivedCredentialError
                    }
                    val legacyUser = auth.signInWithEmailAndPassword(normalized, password).await().user
                        ?: throw derivedCredentialError
                    legacyUser.updatePassword(firebasePassword).await()
                    legacyUser
                }
            } ?: return Result(false, "לא ניתן להשלים את הכניסה כרגע")
            saveParentPasscode(settings, password)
            if (!user.isEmailVerified) {
                val message = if (created) {
                    try {
                        user.sendEmailVerification().await()
                        "שלחנו קישור אימות ל־$normalized. פתח אותו ואז חזור לאפליקציה."
                    } catch (error: CancellationException) {
                        invalidateAndSignOutAuthentication()
                        throw error
                    } catch (error: Exception) {
                        verificationErrorMessage(error)
                    }
                } else {
                    "כתובת המייל עדיין לא אומתה. פתח את קישור האימות או שלח קישור חדש."
                }
                return Result(
                    ok = false,
                    message = message,
                    created = created,
                    verificationPending = true,
                    email = normalized,
                )
            }
            initializeVerifiedAccount(user, normalized, settings, created)
        } catch (error: CancellationException) {
            invalidateAndSignOutAuthentication()
            throw error
        } catch (error: Exception) {
            // Authentication is only considered ready after the account document
            // was initialized successfully.
            invalidateAndSignOutAuthentication()
            Log.e(TAG, "account sign-in failed", error)
            Result(false, signInErrorMessage(error))
        }
    }

    suspend fun checkEmailVerification(
        settings: SettingsStore,
        parentPasscode: String? = null,
    ): Result {
        val auth = FirebaseAuth.getInstance()
        val current = auth.currentUser
            ?: return Result(false, "יש להתחבר מחדש כדי לבדוק את האימות")
        val expectedUid = current.uid
        val email = current.email.orEmpty()
        return try {
            current.reload().await()
            val refreshed = auth.currentUser
                ?: return Result(false, "יש להתחבר מחדש כדי לבדוק את האימות")
            if (refreshed.uid != expectedUid) {
                return Result(false, "החשבון השתנה במהלך בדיקת האימות. נסה שוב.")
            }
            if (!refreshed.isEmailVerified) {
                return Result(
                    ok = false,
                    message = "האימות עדיין לא נקלט. ודא שפתחת את הקישור שנשלח למייל.",
                    verificationPending = true,
                    email = email,
                )
            }
            val passcode = parentPasscode
            val accountEmail = refreshed.email?.trim()
                ?: return Result(false, "לחשבון זה אין כתובת מייל תקינה")
            if (passcode != null) {
                if (passcode.length < PARENT_PASSCODE_MIN_LENGTH) {
                    return Result(false, "הקוד חייב להכיל לפחות 4 תווים")
                }

                // A verified session is allowed to finish the one-time migration
                // from the old, separate parent PIN to one passcode. On a new
                // device we additionally prove that the entered code belongs to
                // the account before storing it locally.
                val hasLocalPasscode = settings.hasFilterPassword
                if (hasLocalPasscode && !isStoredParentPasscode(settings, passcode)) {
                    return Result(false, "הקוד אינו תואם לקוד שהוגדר במכשיר")
                }
                if (!hasLocalPasscode) {
                    reauthenticateWithPasscode(refreshed, accountEmail, passcode)
                }
                refreshed.updatePassword(firebasePassword(accountEmail, passcode)).await()
                if (!isCurrentVerifiedUser(refreshed.uid)) return accountChangedResult()
            }

            val result = initializeVerifiedAccount(
                refreshed,
                accountEmail,
                settings,
                created = false,
            )
            if (result.ok && passcode != null) {
                saveParentPasscode(settings, passcode)
            }
            result
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "email verification check failed", error)
            Result(
                ok = false,
                message = verificationCheckErrorMessage(error),
                verificationPending = true,
                email = email,
            )
        }
    }

    suspend fun resendVerificationEmail(): Result {
        val user = FirebaseAuth.getInstance().currentUser
            ?: return Result(false, "יש להתחבר מחדש כדי לשלוח קישור אימות")
        val email = user.email.orEmpty()
        if (user.isEmailVerified) {
            return Result(
                ok = true,
                message = "המייל כבר אומת. לחץ על „בדקתי ואפשר להמשיך”.",
                verificationPending = true,
                email = email,
            )
        }
        return try {
            user.sendEmailVerification().await()
            Result(
                ok = true,
                message = "קישור אימות חדש נשלח ל־$email.",
                verificationPending = true,
                email = email,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "verification email resend failed", error)
            Result(
                ok = false,
                message = verificationErrorMessage(error),
                verificationPending = true,
                email = email,
            )
        }
    }

    suspend fun sendPasswordReset(email: String): Result {
        val normalized = email.trim().lowercase()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(normalized).matches()) {
            return Result(false, "הזן כתובת אימייל תקינה")
        }
        return try {
            FirebaseAuth.getInstance().sendPasswordResetEmail(normalized).await()
            Result(true, passwordResetSentMessage(normalized))
        } catch (error: CancellationException) {
            throw error
        } catch (_: FirebaseAuthInvalidUserException) {
            // Keep the response identical so the UI does not reveal whether an account exists.
            Result(true, passwordResetSentMessage(normalized))
        } catch (error: Exception) {
            Log.e(TAG, "password reset request failed", error)
            Result(false, passwordResetErrorMessage(error))
        }
    }

    /** Leaves an unverified attempt without deleting the device's previous local data. */
    fun abandonPendingAuthentication() {
        invalidateAndSignOutAuthentication()
    }

    private fun invalidateAndSignOutAuthentication() {
        AccountDataGuard.invalidate()
        FirebaseAuth.getInstance().signOut()
    }

    private suspend fun initializeVerifiedAccount(
        user: FirebaseUser,
        normalizedEmail: String,
        settings: SettingsStore,
        created: Boolean,
    ): Result {
        if (!user.isEmailVerified) {
            return Result(
                ok = false,
                message = "יש לאמת את כתובת המייל לפני הכניסה.",
                created = created,
                verificationPending = true,
                email = normalizedEmail,
            )
        }
        if (!isCurrentVerifiedUser(user.uid)) return accountChangedResult()
        // Refreshing the token is required for server/rules checks that rely on
        // the email_verified claim after the user opened the verification link.
        user.getIdToken(true).await()
        if (!isCurrentVerifiedUser(user.uid)) return accountChangedResult()
        val accountEmail = user.email?.trim()
            ?: return Result(false, "לחשבון זה אין כתובת מייל תקינה")
        val userReference = FirebaseFirestore.getInstance().collection("users").document(user.uid)
        val profileExists = userReference.collection("profile").document("main").get().await().exists()
        if (!isCurrentVerifiedUser(user.uid)) return accountChangedResult()
        userReference
            .set(
                mapOf(
                    "email" to accountEmail,
                    "updatedAt" to System.currentTimeMillis(),
                ),
        )
            .await()
        if (!isCurrentVerifiedUser(user.uid)) return accountChangedResult()
        settings.bindAccountDataOwner(user.uid, accountEmail)
        val generation = AccountDataGuard.generation()
        val savedLocally = AccountDataGuard.withLock {
            if (
                !isCurrentVerifiedUser(user.uid) ||
                AccountDataGuard.generation() != generation
            ) {
                false
            } else {
                settings.cloudUid = user.uid
                settings.cloudEmail = accountEmail
                settings.userEmail = accountEmail
                true
            }
        }
        if (!savedLocally) return accountChangedResult()
        return Result(
            ok = true,
            message = "המייל אומת והחשבון מוכן",
            created = created || !profileExists,
            email = accountEmail,
        )
    }

    private fun isCurrentVerifiedUser(expectedUid: String): Boolean {
        val current = FirebaseAuth.getInstance().currentUser
        return current?.uid == expectedUid && current.isEmailVerified
    }

    private fun accountChangedResult() =
        Result(false, "החשבון השתנה במהלך הפעולה. נסה שוב.")

    private fun validateCredentials(normalizedEmail: String, password: String): Result? {
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(normalizedEmail).matches()) {
            return Result(false, "כתובת אימייל לא תקינה")
        }
        if (password.length < PARENT_PASSCODE_MIN_LENGTH) {
            return Result(false, "הקוד חייב להכיל לפחות 4 תווים")
        }
        return null
    }

    /** Updates the single parent/account passcode both locally and in Firebase. */
    suspend fun updatePassword(
        currentPassword: String,
        newPassword: String,
        settings: SettingsStore,
    ): Result {
        if (newPassword.length < PARENT_PASSCODE_MIN_LENGTH) {
            return Result(false, "הקוד החדש חייב להכיל לפחות 4 תווים")
        }
        val user = FirebaseAuth.getInstance().currentUser
            ?: return Result(false, "יש להתחבר לחשבון לפני שינוי הקוד")
        if (!user.isEmailVerified) {
            return Result(false, "יש לאמת את כתובת המייל לפני שינוי הקוד")
        }
        val email = user.email ?: return Result(false, "לחשבון זה אין אימייל שאפשר לאמת")
        return try {
            reauthenticateWithPasscode(user, email, currentPassword)
            user.updatePassword(firebasePassword(email, newPassword)).await()
            if (FirebaseAuth.getInstance().currentUser?.uid != user.uid) {
                return Result(false, "החשבון השתנה במהלך עדכון הקוד")
            }
            saveParentPasscode(settings, newPassword)
            Result(true, "הקוד לחשבון ולהורים עודכן")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "password update failed", error)
            Result(false, passwordUpdateErrorMessage(error))
        }
    }

    /** Restores the local parent gate from the same account passcode on a new device. */
    suspend fun verifyPasswordAndSetParentGate(password: String, settings: SettingsStore): Result {
        if (password.length < PARENT_PASSCODE_MIN_LENGTH) {
            return Result(false, "הקוד חייב להכיל לפחות 4 תווים")
        }
        val user = FirebaseAuth.getInstance().currentUser
            ?: return Result(false, "יש להתחבר לחשבון לפני אימות הקוד")
        if (!user.isEmailVerified) {
            return Result(false, "יש לאמת את כתובת המייל לפני אימות הקוד")
        }
        val email = user.email ?: return Result(false, "לחשבון זה אין אימייל שאפשר לאמת")
        return try {
            val usedLegacyCredential = reauthenticateWithPasscode(user, email, password)
            if (usedLegacyCredential) {
                user.updatePassword(firebasePassword(email, password)).await()
            }
            if (FirebaseAuth.getInstance().currentUser?.uid != user.uid) {
                return Result(false, "החשבון השתנה במהלך אימות הקוד")
            }
            saveParentPasscode(settings, password)
            Result(true, "הקוד אומת")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "parent gate verification failed", error)
            Result(false, reauthenticationErrorMessage(error))
        }
    }

    private fun signInErrorMessage(error: Exception): String = when (error) {
        is FirebaseNetworkException -> "אין חיבור לשרת. בדוק את האינטרנט ונסה שוב."
        is FirebaseTooManyRequestsException -> "בוצעו יותר מדי ניסיונות. המתן מעט ונסה שוב."
        is FirebaseAuthWeakPasswordException -> "לא ניתן לשמור את הקוד כרגע. נסה שוב בעוד רגע."
        is FirebaseAuthInvalidCredentialsException,
        is FirebaseAuthInvalidUserException -> "לא ניתן להתחבר. בדוק את האימייל והקוד ונסה שוב."
        is FirebaseFirestoreException -> firestoreErrorMessage(error)
        else -> "לא ניתן להשלים את הכניסה כרגע. נסה שוב בעוד רגע."
    }

    private fun registrationErrorMessage(error: Exception): String = when (error) {
        is FirebaseNetworkException -> "אין חיבור לשרת. בדוק את האינטרנט ונסה שוב."
        is FirebaseTooManyRequestsException -> "בוצעו יותר מדי ניסיונות. המתן מעט ונסה שוב."
        is FirebaseAuthWeakPasswordException -> "לא ניתן לשמור את הקוד כרגע. נסה קוד אחר."
        is FirebaseAuthInvalidCredentialsException -> "כתובת האימייל אינה תקינה."
        is FirebaseFirestoreException -> firestoreErrorMessage(error)
        else -> "לא ניתן ליצור את החשבון כרגע. נסה שוב בעוד רגע."
    }

    private fun verificationErrorMessage(error: Exception): String = when (error) {
        is FirebaseNetworkException ->
            "אין חיבור לשליחת האימות. בדוק את האינטרנט ולחץ „שלח שוב”."
        is FirebaseTooManyRequestsException ->
            "נשלחו יותר מדי בקשות אימות. המתן מעט לפני שליחה נוספת."
        else -> "לא ניתן לשלוח כרגע את קישור האימות. נסה שוב בעוד רגע."
    }

    private fun verificationCheckErrorMessage(error: Exception): String = when (error) {
        is FirebaseNetworkException -> "לא ניתן לבדוק את האימות בלי חיבור לאינטרנט."
        is FirebaseTooManyRequestsException -> "בוצעו יותר מדי בדיקות. המתן מעט ונסה שוב."
        is FirebaseAuthRecentLoginRequiredException ->
            "מטעמי אבטחה יש לצאת מהחשבון ולהתחבר שוב, ואז להשלים את האימות."
        is FirebaseAuthInvalidCredentialsException -> "קוד החשבון אינו מתאים לחשבון הזה."
        is FirebaseFirestoreException -> firestoreErrorMessage(error)
        else -> "לא ניתן לבדוק כרגע אם המייל אומת. נסה שוב בעוד רגע."
    }

    private fun firestoreErrorMessage(error: FirebaseFirestoreException): String = when (error.code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "החשבון אומת, אך השרת דחה את שמירת הפרופיל. נסה שוב בעוד רגע."
        FirebaseFirestoreException.Code.UNAVAILABLE,
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            "החשבון אומת, אך שרת הסנכרון אינו זמין כרגע. נסה שוב."
        else -> "החשבון אומת, אך לא ניתן להכין כרגע את הפרופיל בענן."
    }

    private fun passwordResetErrorMessage(error: Exception): String = when (error) {
        is FirebaseNetworkException -> "אין חיבור לשרת. בדוק את האינטרנט ונסה שוב."
        is FirebaseTooManyRequestsException -> "נשלחו יותר מדי בקשות. המתן מעט ונסה שוב."
        is FirebaseAuthInvalidCredentialsException -> "הזן כתובת אימייל תקינה."
        else -> "לא ניתן לשלוח כרגע הודעת איפוס. נסה שוב בעוד רגע."
    }

    private fun passwordUpdateErrorMessage(error: Exception): String = when (error) {
        is FirebaseNetworkException -> "אין חיבור לשרת. בדוק את האינטרנט ונסה שוב."
        is FirebaseTooManyRequestsException -> "בוצעו יותר מדי ניסיונות. המתן מעט ונסה שוב."
        is FirebaseAuthWeakPasswordException -> "לא ניתן לשמור את הקוד החדש כרגע."
        is FirebaseAuthInvalidCredentialsException -> "הקוד הנוכחי שגוי."
        is FirebaseAuthRecentLoginRequiredException -> "נדרש אימות מחדש. סגור את החלון ונסה שוב."
        else -> "לא ניתן לעדכן את הקוד כרגע."
    }

    private fun reauthenticationErrorMessage(error: Exception): String = when (error) {
        is FirebaseNetworkException -> "אין חיבור לשרת. בדוק את האינטרנט ונסה שוב."
        is FirebaseTooManyRequestsException -> "בוצעו יותר מדי ניסיונות. המתן מעט ונסה שוב."
        is FirebaseAuthInvalidCredentialsException -> "קוד החשבון שגוי."
        else -> "לא ניתן לאמת את הקוד כרגע."
    }

    private fun passwordResetSentMessage(email: String) =
        "אם קיים חשבון עבור $email, נשלחה אליו הודעת איפוס. במסך האיפוס של Google ייתכן שתתבקש סיסמה זמנית של 6 תווים; זה זמני בלבד. לאחר הכניסה אפשר לשנות שוב לקוד האחיד שלך, של 4 תווים ומעלה."

    private suspend fun reauthenticateWithPasscode(
        user: FirebaseUser,
        email: String,
        passcode: String,
    ): Boolean {
        val normalizedEmail = email.trim().lowercase(Locale.ROOT)
        val derivedCredential = EmailAuthProvider.getCredential(
            normalizedEmail,
            firebasePassword(normalizedEmail, passcode),
        )
        return try {
            user.reauthenticate(derivedCredential).await()
            false
        } catch (derivedCredentialError: FirebaseAuthInvalidCredentialsException) {
            if (passcode.length < LEGACY_FIREBASE_PASSWORD_MIN_LENGTH) {
                throw derivedCredentialError
            }
            user.reauthenticate(EmailAuthProvider.getCredential(normalizedEmail, passcode)).await()
            true
        }
    }

    private suspend fun saveParentPasscode(settings: SettingsStore, passcode: String) {
        withContext(Dispatchers.Default) { settings.setFilterPassword(passcode) }
    }

    private suspend fun isStoredParentPasscode(settings: SettingsStore, passcode: String): Boolean =
        withContext(Dispatchers.Default) { settings.checkFilterPassword(passcode) }

    /**
     * Firebase requires six characters, while the user-facing parent/account
     * passcode is allowed to be 4+. A deterministic, domain-separated PBKDF2
     * value satisfies Firebase without storing the passcode or a second secret.
     */
    private suspend fun firebasePassword(email: String, passcode: String): String =
        withContext(Dispatchers.Default) {
            val normalizedEmail = email.trim().lowercase(Locale.ROOT)
            val salt = "FilterTube Firebase credential v1:$normalizedEmail".toByteArray(Charsets.UTF_8)
            val algorithm = preferredFirebasePasswordKdf()
            val spec = PBEKeySpec(
                passcode.toCharArray(),
                salt,
                FIREBASE_PASSWORD_KDF_ITERATIONS,
                FIREBASE_PASSWORD_HASH_BITS,
            )
            try {
                Base64.encodeToString(
                    SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded,
                    Base64.NO_WRAP,
                )
            } finally {
                spec.clearPassword()
            }
        }

    private fun preferredFirebasePasswordKdf(): String =
        if (runCatching { SecretKeyFactory.getInstance(PASSWORD_KDF_SHA256) }.isSuccess) {
            PASSWORD_KDF_SHA256
        } else {
            PASSWORD_KDF_SHA1
        }

    fun signOut(context: Context, settings: SettingsStore) {
        AccountDataGuard.withLock {
            // Invalidate in-flight snapshots before revoking Firebase access
            // and clearing the account-owned local store.
            AccountDataGuard.invalidate()
            FirebaseAuth.getInstance().signOut()
            AccountStore(context).logout()
            GoogleAuth.signOut(context)
            LibraryStore(context).clearAccountData()
            settings.clearAccountScopedData()
        }
    }

    private const val PARENT_PASSCODE_MIN_LENGTH = 4
    private const val LEGACY_FIREBASE_PASSWORD_MIN_LENGTH = 6
    private const val PASSWORD_KDF_SHA256 = "PBKDF2WithHmacSHA256"
    private const val PASSWORD_KDF_SHA1 = "PBKDF2WithHmacSHA1"
    private const val FIREBASE_PASSWORD_KDF_ITERATIONS = 120_000
    private const val FIREBASE_PASSWORD_HASH_BITS = 256
}
