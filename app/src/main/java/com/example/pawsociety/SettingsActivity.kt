package com.example.pawsociety

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.pawsociety.util.FirebaseAuthHelper
import com.example.pawsociety.util.SessionManager

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var sessionManager: SessionManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        sessionManager = SessionManager(this)

        // Back Button
        findViewById<android.widget.TextView>(R.id.btn_back).setOnClickListener {
            finish()
        }

        // Settings Options (keep existing code)
        val settingsOptions = listOf(
            R.id.option_follow_friends,
            R.id.option_notifications,
            R.id.option_privacy,
            R.id.option_security,
            R.id.option_help_center,
            R.id.option_about
        )

        settingsOptions.forEach { optionId ->
            findViewById<android.view.View>(optionId).setOnClickListener {
                val optionName = when (optionId) {
                    R.id.option_follow_friends -> "Follow and invite friends"
                    R.id.option_notifications -> "Notifications"
                    R.id.option_privacy -> "Privacy"
                    R.id.option_security -> "Security"
                    R.id.option_help_center -> "Help Center"
                    R.id.option_about -> "About PawSociety"
                    else -> "Settings"
                }
                Toast.makeText(this, optionName, Toast.LENGTH_SHORT).show()
            }
        }

        // LOGOUT BUTTON - Clear Firebase and local session
        findViewById<android.view.View>(R.id.btn_logout).setOnClickListener {
            // Sign out from Firebase
            FirebaseAuthHelper.signOut()
            
            // Clear local session
            sessionManager.clearSession()
            
            // Also clear old UserDatabase session for compatibility
            UserDatabase.logout(this)

            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()

            // Redirect to Login
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}