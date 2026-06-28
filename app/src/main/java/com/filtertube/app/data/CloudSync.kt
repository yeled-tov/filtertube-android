package com.filtertube.app.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * סנכרון פרופיל בסיסי ל-Firebase Auth + Firestore.
 * אם המשתמש מחובר, הפרופיל נשמר בענן ומקושר למשתמש המקומי.
 */
object CloudSync {
    private const val TAG = "CloudSync"
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    val isSignedIn: Boolean get() = auth.currentUser != null
    val currentEmail: String get() = auth.currentUser?.email.orEmpty()

    suspend fun signInOrRegister(email: String, password: String, settings: SettingsStore): Boolean = withContext(Dispatchers.IO) {
        val normalized = email.trim()
        if (normalized.isBlank() || password.length < 6) return@withContext false
        try {
            val result = if (auth.currentUser != null) {
                auth.signInWithEmailAndPassword(normalized, password).await()
            } else {
                auth.createUserWithEmailAndPassword(normalized, password).await()
            }
            val uid = result.user?.uid ?: return@withContext false
            settings.cloudUid = uid
            settings.cloudEmail = result.user?.email.orEmpty()
            syncUserProfile(settings)
            true
        } catch (e: Exception) {
            Log.e(TAG, "signInOrRegister failed", e)
            false
        }
    }

    suspend fun signOut(settings: SettingsStore) = withContext(Dispatchers.IO) {
        auth.signOut()
        settings.cloudUid = ""
        settings.cloudEmail = ""
    }

    suspend fun syncUserProfile(settings: SettingsStore): Boolean = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext false
        val data = hashMapOf(
            "name" to settings.userName,
            "email" to settings.userEmail,
            "gender" to settings.userGender,
            "filterLevel" to settings.filterLevel,
            "updatedAt" to System.currentTimeMillis(),
        )
        try {
            firestore.collection("users").document(uid).set(data, SetOptions.merge()).await()
            settings.cloudUid = uid
            settings.cloudEmail = auth.currentUser?.email.orEmpty()
            true
        } catch (e: Exception) {
            Log.e(TAG, "syncUserProfile failed", e)
            false
        }
    }
}
