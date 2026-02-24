package com.example.pawsociety.util

import android.content.Context
import android.content.SharedPreferences
import com.example.pawsociety.api.ApiUser
import com.google.gson.Gson

class SessionManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val editor: SharedPreferences.Editor = prefs.edit()
    private val gson = Gson()
    
    companion object {
        private const val PREFS_NAME = "pawsociety_session"
        private const val KEY_USER = "current_user"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }
    
    /**
     * Save user session
     */
    fun saveUserSession(user: ApiUser) {
        println("💾 SessionManager: Saving user session")
        println("   - firebaseUid: ${user.firebaseUid}")
        println("   - username: ${user.username}")
        println("   - email: ${user.email}")
        println("   - profileImageUrl: ${user.profileImageUrl}")
        
        val userJson = gson.toJson(user)
        editor.putString(KEY_USER, userJson)
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.apply()
        
        println("✅ Session saved successfully")
    }
    
    /**
     * Get current logged in user
     */
    fun getCurrentUser(): ApiUser? {
        val userJson = prefs.getString(KEY_USER, null)
        return if (userJson != null) {
            try {
                gson.fromJson(userJson, ApiUser::class.java)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
    
    /**
     * Check if user is logged in
     */
    fun isLoggedIn(): Boolean {
        return prefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }
    
    /**
     * Clear session (logout)
     */
    fun clearSession() {
        editor.clear()
        editor.apply()
    }
    
    /**
     * Get Firebase UID
     */
    fun getFirebaseUid(): String? {
        return getCurrentUser()?.firebaseUid
    }
    
    /**
     * Get user email
     */
    fun getUserEmail(): String? {
        return getCurrentUser()?.email
    }
    
    /**
     * Get username
     */
    fun getUsername(): String? {
        return getCurrentUser()?.username
    }
}
