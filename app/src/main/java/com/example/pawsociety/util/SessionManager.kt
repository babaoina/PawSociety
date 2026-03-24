package com.example.pawsociety.util

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast
import com.example.pawsociety.LoginActivity
import com.example.pawsociety.api.ApiClient
import com.example.pawsociety.api.ApiUser
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val editor: SharedPreferences.Editor = prefs.edit()
    private val gson = Gson()
    private val appContext = context.applicationContext
    private var isRunning = false

    companion object {
        private const val PREFS_NAME = "pawsociety_session"
        private const val KEY_USER = "current_user"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_TEMP_FB_UID = "temp_firebase_uid"
        private const val KEY_TEMP_EMAIL = "temp_email"
        private const val KEY_TEMP_USERNAME = "temp_username"
        private const val KEY_TEMP_FULLNAME = "temp_full_name"
        private const val KEY_TEMP_PASSWORD = "temp_password"
    }

    fun saveUserSession(user: ApiUser) {
        println("💾 SessionManager: Saving user session")
        println("   - firebaseUid: ${user.firebaseUid}")
        println("   - username: ${user.username}")
        println("   - email: ${user.email}")

        if (user.firebaseUid.isNullOrEmpty()) {
            println("❌ CRITICAL: Attempted to save user with null/empty firebaseUid!")
            return
        }

        val userJson = gson.toJson(user)
        editor.putString(KEY_USER, userJson)
        editor.putBoolean(KEY_IS_LOGGED_IN, true)
        editor.apply()

        println("✅ Session saved successfully")

        // Start checking user status periodically
        startStatusChecker()
    }

    fun getCurrentUser(): ApiUser? {
        val userJson = prefs.getString(KEY_USER, null)
        return if (userJson != null) {
            try {
                val user = gson.fromJson(userJson, ApiUser::class.java)
                println("📤 SessionManager: Retrieved user - ${user.username}, UID: ${user.firebaseUid}")

                if (user.firebaseUid.isNullOrEmpty()) {
                    println("❌ SessionManager: Retrieved user has null UID! Clearing session.")
                    clearSession()
                    return null
                }

                user
            } catch (e: Exception) {
                println("❌ SessionManager: Error parsing user: ${e.message}")
                clearSession()
                null
            }
        } else {
            println("📤 SessionManager: No user in session")
            null
        }
    }

    fun isLoggedIn(): Boolean {
        val isLoggedIn = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        if (isLoggedIn) {
            val user = getCurrentUser()
            if (user == null || user.firebaseUid.isNullOrEmpty()) {
                println("⚠️ SessionManager: isLoggedIn true but user invalid, clearing")
                clearSession()
                return false
            }
        }
        return isLoggedIn
    }

    fun clearSession() {
        editor.clear()
        editor.apply()
        stopStatusChecker()
        FirebaseAuthHelper.signOut()
        println("🧹 SessionManager: Session cleared")
    }

    fun getFirebaseUid(): String? {
        return getCurrentUser()?.firebaseUid
    }

    fun getUserEmail(): String? {
        return getCurrentUser()?.email
    }

    fun getUsername(): String? {
        return getCurrentUser()?.username
    }

    fun saveTempRegistrationData(firebaseUid: String, email: String, username: String? = null, fullName: String? = null, password: String) {
        println("💾 SessionManager: Saving temporary registration data")
        editor.putString(KEY_TEMP_FB_UID, firebaseUid)
        editor.putString(KEY_TEMP_EMAIL, email)
        editor.putString(KEY_TEMP_USERNAME, username ?: "")
        editor.putString(KEY_TEMP_FULLNAME, fullName ?: "")
        editor.putString(KEY_TEMP_PASSWORD, password)
        editor.apply()
        println("✅ Temporary registration data saved")
    }

    fun getTempRegistrationData(): Map<String, String>? {
        val fbUid = prefs.getString(KEY_TEMP_FB_UID, null) ?: return null
        val email = prefs.getString(KEY_TEMP_EMAIL, "") ?: ""
        val username = prefs.getString(KEY_TEMP_USERNAME, "") ?: ""
        val fullName = prefs.getString(KEY_TEMP_FULLNAME, "") ?: ""
        val password = prefs.getString(KEY_TEMP_PASSWORD, "") ?: ""
        
        return mapOf(
            "firebaseUid" to fbUid,
            "email" to email,
            "username" to username,
            "fullName" to fullName,
            "password" to password
        )
    }

    fun clearTempRegistrationData() {
        editor.remove(KEY_TEMP_FB_UID)
        editor.remove(KEY_TEMP_EMAIL)
        editor.remove(KEY_TEMP_USERNAME)
        editor.remove(KEY_TEMP_FULLNAME)
        editor.remove(KEY_TEMP_PASSWORD)
        editor.apply()
        println("🧹 Temporary registration data cleared")
    }

    // ===== STATUS CHECKER =====
    private var statusCheckJob: Job? = null

    private fun startStatusChecker() {
        stopStatusChecker()
        isRunning = true

        println("🚀 Status checker STARTED")

        statusCheckJob = CoroutineScope(Dispatchers.IO).launch {
            while (isRunning) {
                try {
                    val currentUser = getCurrentUser()
                    if (currentUser == null) {
                        println("❌ No current user, stopping status checker")
                        isRunning = false
                        break
                    }

                    println("🔍 Checking status for user: ${currentUser.firebaseUid}")

                    val isFirebaseValid = FirebaseAuthHelper.isUserValid()
                    if (!isFirebaseValid) {
                        println("Firebase account is no longer valid. Forcing logout NOW")
                        withContext(Dispatchers.Main) {
                            forceLogout("Your account is no longer available.")
                        }
                        isRunning = false
                        break
                    }

                    // Call the API with proper map
                    val response = ApiClient.apiService.checkUserStatus(mapOf("firebaseUid" to currentUser.firebaseUid))

                    println("📥 Status check response code: ${response.code()}")

                    if (response.isSuccessful) {
                        val body = response.body()
                        println("📦 Response body: $body")

                        if (body != null && body.status == "deleted") {
                            println("USER IS DELETED! Forcing logout NOW")
                            withContext(Dispatchers.Main) {
                                forceLogout("Your account has been deleted")
                            }
                            isRunning = false
                            break
                        } else if (body != null && body.success) {
                            println("📊 User status from server: ${body.status}")

                            if (body.status == "Suspended") {
                                println("🚫 USER IS SUSPENDED! Forcing logout NOW")
                                withContext(Dispatchers.Main) {
                                    forceLogout("Your account has been suspended")
                                }
                                isRunning = false
                                break
                            } else if (body.status == "deleted") {
                                println("🗑️ USER IS DELETED! Forcing logout NOW")
                                withContext(Dispatchers.Main) {
                                    forceLogout("Your account has been deleted")
                                }
                                isRunning = false
                                break
                            } else {
                                println("✅ User is active (status: ${body.status})")
                            }
                        } else {
                            println("❌ API returned success=false: ${body?.message}")
                        }
                    } else {
                        val errorBody = response.errorBody()?.string()
                        println("❌ HTTP error ${response.code()}: $errorBody")
                    }

                    println("⏰ Waiting 30 seconds for next check...")
                    delay(30000)

                } catch (e: Exception) {
                    println("❌ Status checker error: ${e.message}")
                    e.printStackTrace()
                    delay(60000)
                }
            }
            println("🛑 Status checker stopped")
        }
    }

    private fun stopStatusChecker() {
        isRunning = false
        statusCheckJob?.cancel()
        statusCheckJob = null
        println("🛑 Status checker stopped")
    }

    private fun forceLogout(message: String) {
        clearSession()

        val intent = Intent(appContext, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("logout_message", message)
        }
        appContext.startActivity(intent)

        Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
    }
}
