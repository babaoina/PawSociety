package com.example.pawsociety

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
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
    private val tag = "SplashActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        settingsRepository = SettingsRepository()
        sessionManager = SessionManager(this)

        println("🚀 $tag - Started")

        // 🔥 ADD THIS - Request notification permission for Android 13+
        requestNotificationPermission()

        // Start checking after a short delay
        Handler(Looper.getMainLooper()).postDelayed({
            checkAppStatus()
        }, 1500) // 1.5 seconds delay
    }

    // 🔥 ADD THIS FUNCTION
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    private fun checkAppStatus() {
        lifecycleScope.launch {
            try {
                println("🔍 $tag - Checking app status...")

                // STEP 1: Check maintenance mode with timeout
                val isMaintenance = withTimeout(5000) {
                    settingsRepository.isMaintenanceMode()
                }

                println("🚦 $tag - Maintenance mode from server: $isMaintenance")

                if (isMaintenance) {
                    // Get maintenance message
                    val message = withTimeout(3000) {
                        settingsRepository.getMaintenanceMessage()
                    }

                    println("📝 $tag - Maintenance message: $message")

                    // Go to maintenance screen
                    val intent = Intent(this@SplashActivity, MaintenanceActivity::class.java)
                    intent.putExtra("message", message)
                    startActivity(intent)
                    finish()
                    return@launch
                }

                println("✅ $tag - No maintenance, proceeding normally")

                // STEP 2: If not in maintenance, proceed with normal flow
                proceedToNextScreen()

            } catch (e: TimeoutCancellationException) {
                println("⚠️ $tag - Maintenance check timeout, proceeding normally")
                proceedToNextScreen()
            } catch (e: Exception) {
                println("❌ $tag - Error checking maintenance: ${e.message}")
                e.printStackTrace()
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

                println("🔍 $tag - Firebase valid: $isFirebaseValid, Local session: $hasLocalSession")

                if (isFirebaseValid && hasLocalSession) {
                    // User is valid and logged in
                    val currentUser = sessionManager.getCurrentUser()
                    println("✅ $tag - User session valid, going to Home")

                    // Connect socket
                    SocketManager.connect()
                    currentUser?.let {
                        SocketManager.joinUserRoom(it.firebaseUid)
                        println("🟢 $tag - User ${it.username} is now ONLINE")
                    }

                    startActivity(Intent(this@SplashActivity, HomeActivity::class.java))
                } else {
                    // Not logged in or invalid
                    println("⚠️ $tag - No valid session, going to Login")

                    if (!isFirebaseValid && hasLocalSession) {
                        sessionManager.clearSession()
                    }

                    startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                }
                finish()

            } catch (e: Exception) {
                println("❌ $tag - Error in proceedToNextScreen: ${e.message}")
                e.printStackTrace()
                startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                finish()
            }
        }
    }
}