package com.example.pawsociety.util

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

object FirebaseAuthHelper {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val isSignedIn: Boolean
        get() = currentUser != null

    /**
     * Register with email and password
     */
    suspend fun registerWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val user = authResult.user ?: return Result.failure(Exception("User is null"))
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Login with email and password
     */
    suspend fun loginWithEmail(email: String, password: String): Result<FirebaseUser> {
        return try {
            Log.d("FirebaseAuthHelper", "🔐 Attempting login for: $email")
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val user = authResult.user
            if (user != null) {
                Log.d("FirebaseAuthHelper", "✅ Login successful for: ${user.email}")
                Result.success(user)
            } else {
                Log.e("FirebaseAuthHelper", "❌ Login succeeded but user is null")
                Result.failure(Exception("User is null after login"))
            }
        } catch (e: Exception) {
            Log.e("FirebaseAuthHelper", "❌ Login failed for: $email - Error: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Sign in with credential (for Google Sign In)
     */
    suspend fun signInWithCredential(credential: com.google.firebase.auth.AuthCredential): Result<FirebaseUser> {
        return try {
            val authResult = auth.signInWithCredential(credential).await()
            val user = authResult.user ?: return Result.failure(Exception("User is null"))
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Send email verification
     */
    suspend fun sendEmailVerification(): Result<Unit> {
        return try {
            currentUser?.sendEmailVerification()?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Send password reset email
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get Firebase ID token
     */
    suspend fun getIdToken(forceRefresh: Boolean = false): Result<String> {
        return try {
            val token = currentUser?.getIdToken(forceRefresh)?.await()?.token
            if (token != null) {
                Result.success(token)
            } else {
                Result.failure(Exception("Failed to get ID token"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if the current user is still valid on the server
     * This will force a token refresh and throw an exception if the user is deleted/disabled
     */
    suspend fun isUserValid(): Boolean {
        val user = currentUser ?: return false
        return try {
            // Reload is less aggressive than forcing a token refresh and avoids
            // false "account unavailable" logouts on fresh sessions.
            user.reload().await()
            true
        } catch (e: Exception) {
            val message = e.message?.lowercase().orEmpty()
            val isDefinitelyInvalid = listOf(
                "user token has expired",
                "user disabled",
                "invalid user token",
                "no user record",
                "user-not-found",
                "requires recent login"
            ).any { message.contains(it) }

            println("User validation check failed: ${e.message}")

            if (isDefinitelyInvalid) {
                signOut()
                false
            } else {
                // Treat transient Firebase/network issues as inconclusive so we
                // do not incorrectly kick out valid users.
                true
            }
        }
    }

    /**
     * Check if email is verified
     */
    val isEmailVerified: Boolean
        get() = currentUser?.isEmailVerified ?: false

    /**
     * Get current user UID
     */
    fun getCurrentUserUid(): String? {
        return currentUser?.uid
    }

    /**
     * Sign out
     */
    fun signOut() {
        auth.signOut()
    }

    /**
     * Get current user email
     */
    fun getCurrentUserEmail(): String? {
        return currentUser?.email
    }

    /**
     * Get current user display name
     */
    fun getCurrentUserDisplayName(): String? {
        return currentUser?.displayName
    }

    /**
     * Get current user photo URL
     */
    fun getCurrentUserPhotoUrl(): String? {
        return currentUser?.photoUrl?.toString()
    }

    /**
     * Reload current user data from Firebase
     */
    suspend fun reloadUser(): Result<Unit> {
        return try {
            currentUser?.reload()?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete currently signed-in Firebase user.
     * Used as rollback if registration only partially succeeds.
     */
    suspend fun deleteCurrentUser(): Result<Unit> {
        return try {
            val user = currentUser ?: return Result.success(Unit)
            user.delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update current user profile
     */
    suspend fun updateUserProfile(
        displayName: String? = null,
        photoUri: String? = null
    ): Result<Unit> {
        return try {
            val user = currentUser ?: return Result.failure(Exception("No user logged in"))

            val profileUpdates = mutableMapOf<String, Any>()
            displayName?.let { profileUpdates["displayName"] = it }
            photoUri?.let { profileUpdates["photoUrl"] = it }

            user.updateProfile(com.google.firebase.auth.UserProfileChangeRequest.Builder()
                .apply {
                    displayName?.let { setDisplayName(it) }
                    photoUri?.let { setPhotoUri(android.net.Uri.parse(it)) }
                }
                .build()
            ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 🔥 NEW: Check if current user's email is verified
     */
    suspend fun isEmailVerified(): Boolean {
        return try {
            // Reload user to get latest email verification status
            currentUser?.reload()?.await()
            currentUser?.isEmailVerified ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 🔥 NEW: Confirm password reset with oobCode from email link
     * This is used when user clicks the reset link in their email
     */
    suspend fun confirmPasswordReset(oobCode: String, newPassword: String): Result<Unit> {
        return try {
            Log.d("FirebaseAuthHelper", "🔐 Confirming password reset with oobCode: ${oobCode.substring(0, 10)}...")
            auth.confirmPasswordReset(oobCode, newPassword).await()
            Log.d("FirebaseAuthHelper", "✅ Password reset confirmed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseAuthHelper", "❌ Password reset confirmation failed: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
