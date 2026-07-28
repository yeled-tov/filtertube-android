package com.filtertube.app.data

import android.content.Context
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object FirebaseAccount {
    private const val TAG = "FirebaseAccount"

    data class Result(
        val ok: Boolean,
        val message: String,
        val created: Boolean = false,
        val verificationPending: Boolean = false,
        val email: String? = null,
    )

    suspend fun signInOrRegister(email: String, password: String, settings: SettingsStore): Result {
        val normalized = email.trim().lowercase()
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(normalized).matches()) {
            return Result(false, "כתובת אימייל לא תקינה")
        }
        if (password.length < 6) return Result(false, "הסיסמה חייבת להכיל לפחות 6 תווים")
        val auth = FirebaseAuth.getInstance()
        return try {
            var created = true
            val user = try {
                auth.createUserWithEmailAndPassword(normalized, password).await().user
            } catch (_: FirebaseAuthUserCollisionException) {
                created = false
                auth.signInWithEmailAndPassword(normalized, password).await().user
            } ?: return Result(false, "לא ניתן להשלים את הכניסה כרגע")
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
            val result = initializeVerifiedAccount(user, normalized, settings, created)
            if (result.ok) {
                withContext(Dispatchers.Default) { settings.setFilterPassword(password) }
            }
            result
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

    suspend fun checkEmailVerification(settings: SettingsStore): Result {
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
            initializeVerifiedAccount(refreshed, refreshed.email ?: email, settings, created = false)
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

    /** Keeps the Firebase account password and the local parent gate in sync. */
    suspend fun updatePassword(currentPassword: String, newPassword: String, settings: SettingsStore): Result {
        if (newPassword.length < 6) return Result(false, "הסיסמה החדשה חייבת להכיל לפחות 6 תווים")
        val user = FirebaseAuth.getInstance().currentUser
            ?: return Result(false, "יש להתחבר לחשבון לפני שינוי הסיסמה")
        if (!user.isEmailVerified) {
            return Result(false, "יש לאמת את כתובת המייל לפני שינוי הסיסמה")
        }
        val email = user.email ?: return Result(false, "לחשבון זה אין אימייל שאפשר לאמת")
        return try {
            val credential = EmailAuthProvider.getCredential(email, currentPassword)
            user.reauthenticate(credential).await()
            user.updatePassword(newPassword).await()
            if (FirebaseAuth.getInstance().currentUser?.uid != user.uid) {
                return Result(false, "החשבון השתנה במהלך עדכון הסיסמה")
            }
            withContext(Dispatchers.Default) { settings.setFilterPassword(newPassword) }
            Result(true, "הסיסמה עודכנה")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "password update failed", error)
            Result(false, passwordUpdateErrorMessage(error))
        }
    }

    /** Restores the local parent gate on a new device without storing a password in Firestore. */
    suspend fun verifyPasswordAndSetParentGate(password: String, settings: SettingsStore): Result {
        val user = FirebaseAuth.getInstance().currentUser
            ?: return Result(false, "יש להתחבר לחשבון לפני אימות הסיסמה")
        if (!user.isEmailVerified) {
            return Result(false, "יש לאמת את כתובת המייל לפני אימות הסיסמה")
        }
        val email = user.email ?: return Result(false, "לחשבון זה אין אימייל שאפשר לאמת")
        return try {
            user.reauthenticate(EmailAuthProvider.getCredential(email, password)).await()
            if (FirebaseAuth.getInstance().currentUser?.uid != user.uid) {
                return Result(false, "החשבון השתנה במהלך אימות הסיסמה")
            }
            withContext(Dispatchers.Default) { settings.setFilterPassword(password) }
            Result(true, "הסיסמה אומתה")
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
        is FirebaseAuthWeakPasswordException -> "הסיסמה חלשה מדי. בחר לפחות 6 תווים שקשה לנחש."
        is FirebaseAuthInvalidCredentialsException,
        is FirebaseAuthInvalidUserException -> "לא ניתן להתחבר. בדוק את האימייל והסיסמה ונסה שוב."
        else -> "לא ניתן להשלים את הכניסה כרגע. נסה שוב בעוד רגע."
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
        else -> "לא ניתן לבדוק כרגע אם המייל אומת. נסה שוב בעוד רגע."
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
        is FirebaseAuthWeakPasswordException -> "הסיסמה החדשה חלשה מדי."
        is FirebaseAuthInvalidCredentialsException -> "הסיסמה הנוכחית שגויה."
        is FirebaseAuthRecentLoginRequiredException -> "נדרש אימות מחדש. סגור את החלון ונסה שוב."
        else -> "לא ניתן לעדכן את הסיסמה כרגע."
    }

    private fun reauthenticationErrorMessage(error: Exception): String = when (error) {
        is FirebaseNetworkException -> "אין חיבור לשרת. בדוק את האינטרנט ונסה שוב."
        is FirebaseTooManyRequestsException -> "בוצעו יותר מדי ניסיונות. המתן מעט ונסה שוב."
        is FirebaseAuthInvalidCredentialsException -> "סיסמת החשבון שגויה."
        else -> "לא ניתן לאמת את הסיסמה כרגע."
    }

    private fun passwordResetSentMessage(email: String) =
        "אם קיים חשבון עבור $email, נשלחה אליו הודעה לאיפוס הסיסמה."

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
}
