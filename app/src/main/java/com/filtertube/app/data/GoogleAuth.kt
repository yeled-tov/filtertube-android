package com.filtertube.app.data

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * התחברות לחשבון Google לצורך קריאת נתוני יוטיוב (סרטונים שאהבת).
 * משתמש ב-OAuth client מסוג Android (מזוהה לפי package + SHA-1, ללא secret בקוד).
 */
object GoogleAuth {

    // force-ssl כולל קריאה (לייקים/מנויים) וגם כתיבה (videos.rate — סימון לייק חזרה ליוטיוב)
    const val YT_SCOPE = "https://www.googleapis.com/auth/youtube.force-ssl"
    private const val PREFS = "filtertube_google_auth_scope"
    private const val KEY_OWNER_UID = "firebase_owner_uid"

    data class Session internal constructor(
        val uid: String,
        val generation: Long,
    )

    fun client(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(YT_SCOPE))
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun bindToCurrentFirebaseAccount(
        context: Context,
        account: GoogleSignInAccount,
    ): Session? {
        if (account.account == null) return null
        return AccountDataGuard.withLock {
            val user = FirebaseAuth.getInstance().currentUser
                ?.takeIf { it.isEmailVerified }
                ?: return@withLock null
            val generation = AccountDataGuard.generation()
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_OWNER_UID, user.uid)
                .apply()
            Session(user.uid, generation)
        }
    }

    fun lastAccount(context: Context): GoogleSignInAccount? {
        val user = FirebaseAuth.getInstance().currentUser
            ?.takeIf { it.isEmailVerified }
            ?: run {
                signOut(context)
                return null
            }
        val ownerUid = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_OWNER_UID, "")
            .orEmpty()
        if (ownerUid != user.uid) {
            signOut(context)
            return null
        }
        return GoogleSignIn.getLastSignedInAccount(context)
    }

    fun session(
        context: Context,
        account: GoogleSignInAccount,
    ): Session? {
        val currentAccount = lastAccount(context) ?: return null
        if (currentAccount.id != account.id || currentAccount.email != account.email) {
            return null
        }
        val user = FirebaseAuth.getInstance().currentUser
            ?.takeIf { it.isEmailVerified }
            ?: return null
        return Session(user.uid, AccountDataGuard.generation())
            .takeIf { isSessionCurrent(context, it) }
    }

    fun isSessionCurrent(context: Context, session: Session): Boolean {
        val user = FirebaseAuth.getInstance().currentUser
        val ownerUid = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_OWNER_UID, "")
            .orEmpty()
        return user?.uid == session.uid &&
            user.isEmailVerified &&
            ownerUid == session.uid &&
            AccountDataGuard.generation() == session.generation
    }

    fun signOut(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        client(context.applicationContext).signOut()
    }

    /** מחזיר OAuth access token לקריאת YouTube Data API. רץ ב-IO. */
    suspend fun accessToken(
        context: Context,
        account: Account,
        session: Session,
    ): String = withContext(Dispatchers.IO) {
        check(isSessionCurrent(context, session)) { "Firebase account changed" }
        val token = GoogleAuthUtil.getToken(context, account, "oauth2:$YT_SCOPE")
        check(isSessionCurrent(context, session)) { "Firebase account changed" }
        token
    }
}
