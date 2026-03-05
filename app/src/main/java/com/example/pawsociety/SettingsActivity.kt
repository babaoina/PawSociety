package com.example.pawsociety

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.pawsociety.util.FirebaseAuthHelper
import com.example.pawsociety.util.SessionManager
import com.example.pawsociety.util.SocketManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sessionManager = SessionManager(this)

        // Back Button
        val backButton = findViewById<ImageView>(R.id.btn_back)
        backButton.setOnClickListener {
            finish()
        }

        // Settings Options
        val optionFollowFriends = findViewById<LinearLayout>(R.id.option_follow_friends)
        val optionNotifications = findViewById<LinearLayout>(R.id.option_notifications)
        val optionPrivacy = findViewById<LinearLayout>(R.id.option_privacy)
        val optionSecurity = findViewById<LinearLayout>(R.id.option_security)
        val optionHelpCenter = findViewById<LinearLayout>(R.id.option_help_center)
        val optionAbout = findViewById<LinearLayout>(R.id.option_about)
        val optionBlockedUsers = findViewById<LinearLayout>(R.id.option_blocked_users)
        val optionDeleteAccount = findViewById<LinearLayout>(R.id.option_delete_account)
        val logoutButton = findViewById<LinearLayout>(R.id.btn_logout)

        // Follow and invite friends
        optionFollowFriends.setOnClickListener {
            Toast.makeText(this, "Follow and invite friends - Coming soon!", Toast.LENGTH_SHORT).show()
        }

        // Notifications
        optionNotifications.setOnClickListener {
            Toast.makeText(this, "Notification settings - Coming soon!", Toast.LENGTH_SHORT).show()
        }

        // Privacy
        optionPrivacy.setOnClickListener {
            Toast.makeText(this, "Privacy settings - Coming soon!", Toast.LENGTH_SHORT).show()
        }

        // Security
        optionSecurity.setOnClickListener {
            Toast.makeText(this, "Security settings - Coming soon!", Toast.LENGTH_SHORT).show()
        }

        // Help Center
        optionHelpCenter.setOnClickListener {
            Toast.makeText(this, "Help Center - Coming soon!", Toast.LENGTH_SHORT).show()
        }

        // About
        optionAbout.setOnClickListener {
            Toast.makeText(this, "About PawSociety - Version 1.0.0", Toast.LENGTH_SHORT).show()
        }

        // Blocked Users
        optionBlockedUsers.setOnClickListener {
            startActivity(Intent(this, BlockedUsersActivity::class.java))
        }

        // Delete Account
        optionDeleteAccount.setOnClickListener {
            startActivity(Intent(this, DeleteAccountActivity::class.java))
        }

        // LOGOUT BUTTON
        logoutButton.setOnClickListener {
            // ADD SOCKET DISCONNECT HERE
            SocketManager.disconnect()
            println("🔴 User logged out - now OFFLINE")

            // Sign out from Firebase
            FirebaseAuthHelper.signOut()

            // Clear local session
            sessionManager.clearSession()

            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()

            // Redirect to Login
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}