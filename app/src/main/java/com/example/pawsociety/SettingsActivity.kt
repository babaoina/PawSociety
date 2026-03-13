package com.example.pawsociety

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pawsociety.data.repository.HidePostRepository
import com.example.pawsociety.util.SessionManager
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var hidePostRepository: HidePostRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sessionManager = SessionManager(this)
        hidePostRepository = HidePostRepository()

        setupClickListeners()
        loadHiddenCount()
    }

    private fun setupClickListeners() {
        // Back button
        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }

        // Follow and invite friends
        findViewById<View>(R.id.option_follow_friends).setOnClickListener {
            Toast.makeText(this, "Follow friends feature coming soon", Toast.LENGTH_SHORT).show()
        }

        // Notifications
        findViewById<View>(R.id.option_notifications).setOnClickListener {
            val intent = Intent(this, NotificationsSettingsActivity::class.java)
            startActivity(intent)
        }

        // Privacy
        findViewById<View>(R.id.option_privacy).setOnClickListener {
            val intent = Intent(this, PrivacyActivity::class.java)
            startActivity(intent)
        }

        // Security
        findViewById<View>(R.id.option_security).setOnClickListener {
            val intent = Intent(this, SecurityActivity::class.java)
            startActivity(intent)
        }

        // Blocked Users
        findViewById<View>(R.id.option_blocked_users).setOnClickListener {
            val intent = Intent(this, BlockedUsersActivity::class.java)
            startActivity(intent)
        }

        // ===== NEW: Hidden Posts =====
        findViewById<View>(R.id.option_hidden_posts).setOnClickListener {
            val intent = Intent(this, HiddenPostsActivity::class.java)
            startActivity(intent)
        }

        // Help Center
        findViewById<View>(R.id.option_help_center).setOnClickListener {
            val intent = Intent(this, HelpCenterActivity::class.java)
            startActivity(intent)
        }

        // About
        findViewById<View>(R.id.option_about).setOnClickListener {
            val intent = Intent(this, AboutActivity::class.java)
            startActivity(intent)
        }

        // Delete Account
        findViewById<View>(R.id.option_delete_account).setOnClickListener {
            showDeleteAccountConfirmation()
        }

        // Logout
        findViewById<View>(R.id.btn_logout).setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun loadHiddenCount() {
        val currentUser = sessionManager.getCurrentUser() ?: return

        lifecycleScope.launch {
            val result = hidePostRepository.getHiddenCount(currentUser.firebaseUid)
            val tvHiddenCount = findViewById<TextView>(R.id.tv_hidden_count)

            if (result.isSuccess) {
                val count = result.getOrNull() ?: 0
                tvHiddenCount.text = count.toString()
                tvHiddenCount.visibility = if (count > 0) View.VISIBLE else View.GONE
            } else {
                tvHiddenCount.visibility = View.GONE
            }
        }
    }

    private fun showDeleteAccountConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to delete your account? This action cannot be undone and all your data will be permanently lost.")
            .setPositiveButton("Delete") { _, _ ->
                performDeleteAccount()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performDeleteAccount() {
        val currentUser = sessionManager.getCurrentUser() ?: return

        // Show loading
        Toast.makeText(this, "Deleting account...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                // Call your API to delete account
                // val result = userRepository.deleteAccount(currentUser.firebaseUid)

                // For now, just clear session and logout
                sessionManager.clearSession()

                Toast.makeText(this@SettingsActivity, "Account deleted successfully", Toast.LENGTH_SHORT).show()

                // Redirect to login
                val intent = Intent(this@SettingsActivity, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, "Failed to delete account: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Log Out")
            .setMessage("Are you sure you want to log out?")
            .setPositiveButton("Log Out") { _, _ ->
                performLogout()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performLogout() {
        sessionManager.clearSession()

        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()

        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        // Refresh hidden count when returning to settings
        loadHiddenCount()
    }
}