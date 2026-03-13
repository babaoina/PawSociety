package com.example.pawsociety

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pawsociety.data.repository.SettingsRepository
import com.example.pawsociety.util.FirebaseAuthHelper
import com.example.pawsociety.util.SessionManager
import com.example.pawsociety.util.SocketManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

class SplashActivity : AppCompatActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        settingsRepository = SettingsRepository()
        sessionManager = SessionManager(this)

        // Start checking after a short delay
        Handler(Looper.getMainLooper()).postDelayed({
            checkAppStatus()
        }, 1500) // 1.5 seconds delay
    }

    private fun checkAppStatus() {
        lifecycleScope.launch {
            try {
                // 🔥 STEP 1: Check maintenance mode with timeout
                val isMaintenance = withTimeout(5000) {
                    settingsRepository.isMaintenanceMode()
                }

                if (isMaintenance) {
                    // Get maintenance message
                    val message = withTimeout(3000) {
                        settingsRepository.getMaintenanceMessage()
                    }

                    // Go to maintenance screen
                    val intent = Intent(this@SplashActivity, MaintenanceActivity::class.java)
                    intent.putExtra("message", message)
                    startActivity(intent)
                    finish()
                    return@launch
                }

                // 🔥 STEP 2: If not in maintenance, proceed with normal flow
                proceedToNextScreen()

            } catch (e: TimeoutCancellationException) {
                // If timeout, assume no maintenance and proceed
                println("⚠️ Maintenance check timeout, proceeding normally")
                proceedToNextScreen()
            } catch (e: Exception) {
                println("❌ Error checking maintenance: ${e.message}")
                // On error, still proceed (don't block user)
                proceedToNextScreen()
            }
        }
    }

    private fun proceedToNextScreen() {
        lifecycleScope.launch {
            try {
                // Check if user is logged in and valid
                val isFirebaseValid = FirebaseAuthHelper.isUserValid()
                val hasLocalSession = sessionManager.isLoggedIn()

                println("🔍 Splash check - Firebase valid: $isFirebaseValid, Local session: $hasLocalSession")

                if (isFirebaseValid && hasLocalSession) {
                    // User is valid and logged in
                    val currentUser = sessionManager.getCurrentUser()
                    println("✅ User session valid, going to Home")

                    // Connect socket
                    SocketManager.connect()
                    currentUser?.let {
                        SocketManager.joinUserRoom(it.firebaseUid)
                        println("🟢 User ${it.username} is now ONLINE")
                    }

                    startActivity(Intent(this@SplashActivity, HomeActivity::class.java))
                } else {
                    // Not logged in or invalid
                    println("⚠️ No valid session, going to Login")

                    if (!isFirebaseValid && hasLocalSession) {
                        sessionManager.clearSession()
                    }

                    startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                }
                finish()

            } catch (e: Exception) {
                println("❌ Error in proceedToNextScreen: ${e.message}")
                startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                finish()
            }
        }
    }
}