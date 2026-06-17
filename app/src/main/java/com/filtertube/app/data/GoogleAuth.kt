package com.filtertube.app.data

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * התחברות לחשבון Google לצורך קריאת נתוני יוטיוב (סרטונים שאהבת).
 * משתמש ב-OAuth client מסוג Android (מזוהה לפי package + SHA-1, ללא secret בקוד).
 */
object GoogleAuth {

    // force-ssl כולל קריאה (לייקים/מנויים) וגם כתיבה (videos.rate — סימון לייק חזרה ליוטיוב)
    const val YT_SCOPE = "https://www.googleapis.com/auth/youtube.force-ssl"

    fun client(context: Context): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(YT_SCOPE))
            .build()
        return GoogleSignIn.getClient(context, gso)
    }

    fun lastAccount(context: Context): GoogleSignInAccount? = GoogleSignIn.getLastSignedInAccount(context)

    /** מחזיר OAuth access token לקריאת YouTube Data API. רץ ב-IO. */
    suspend fun accessToken(context: Context, account: Account): String = withContext(Dispatchers.IO) {
        GoogleAuthUtil.getToken(context, account, "oauth2:$YT_SCOPE")
    }
}
