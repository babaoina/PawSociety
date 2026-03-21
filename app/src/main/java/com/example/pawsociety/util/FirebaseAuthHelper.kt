package com.example.pawsociety.util

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
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val user = authResult.user ?: return Result.failure(Exception("User is null"))
            Result.success(user)
        } catch (e: Exception) {
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
            // Force a token refresh to check with the server
            user.getIdToken(true).await()
            true // Token refresh successful, user is valid
        } catch (e: Exception) {
            // Failed to refresh token. User is likely deleted/disabled
            println("⚠️ User token refresh failed: ${e.message}")
            // Sign out locally to clear bad cache
            signOut()
            false
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
}