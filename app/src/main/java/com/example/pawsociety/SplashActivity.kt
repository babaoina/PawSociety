package com.example.pawsociety

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pawsociety.util.FirebaseAuthHelper
import com.example.pawsociety.util.SessionManager
import kotlinx.coroutines.launch
import com.example.pawsociety.util.SocketManager
class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val sessionManager = SessionManager(this)

        Handler(Looper.getMainLooper()).postDelayed({
            lifecycleScope.launch {
                try {
                    // Check if the Firebase session is actually valid on the server
                    val isFirebaseValid = FirebaseAuthHelper.isUserValid()
                    val hasLocalSession = sessionManager.isLoggedIn()

                    println("🔍 Splash check - Firebase valid: $isFirebaseValid, Local session: $hasLocalSession")

                    if (isFirebaseValid && hasLocalSession) {
                        // User is valid and logged in
                        val currentUser = sessionManager.getCurrentUser()
                        println("✅ User session valid, going to Home")

                        // Connect socket and set online status
                        SocketManager.connect()
                        currentUser?.let {
                            SocketManager.joinUserRoom(it.firebaseUid)
                            println("🟢 User ${it.username} is now ONLINE")
                        }

                        val intent = Intent(this@SplashActivity, HomeActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    } else {
                        // User is not valid or not logged in
                        println("⚠️ User session invalid, clearing and going to Login")

                        // Clear any stale local session
                        if (!isFirebaseValid && hasLocalSession) {
                            sessionManager.clearSession()
                            println("🧹 Cleared stale local session")
                        }

                        val intent = Intent(this@SplashActivity, LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                    }
                    finish()
                } catch (e: Exception) {
                    println("❌ SplashActivity error: ${e.message}")
                    e.printStackTrace()
                    // On error, go to login
                    val intent = Intent(this@SplashActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
        }, 2000)
    }
}