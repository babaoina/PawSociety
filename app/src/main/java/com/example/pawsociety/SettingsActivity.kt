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
        val builder = AlertDialog.Builder(this, R.style.Theme_PawSociety_Dialog)
        builder.setTitle("Delete Account")
        builder.setMessage("Are you sure you want to delete your account? This action cannot be undone and all your data will be permanently lost.")
        builder.setPositiveButton("Delete") { _, _ ->
            performDeleteAccount()
        }
        builder.setNegativeButton("Cancel", null)
        val dialog = builder.show()
        
        // Style the buttons
        val deleteButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val cancelButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        
        deleteButton?.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
        deleteButton?.setBackgroundColor(android.graphics.Color.parseColor("#7A4F2B"))
        
        cancelButton?.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
        cancelButton?.setBackgroundColor(android.graphics.Color.parseColor("#999999"))
        
        // Style the title and message
        val title = dialog.findViewById<android.widget.TextView>(android.R.id.title)
        val message = dialog.findViewById<android.widget.TextView>(android.R.id.message)
        title?.setTextColor(android.graphics.Color.parseColor("#333333"))
        message?.setTextColor(android.graphics.Color.parseColor("#333333"))
    }

    private fun performDeleteAccount() {
        // Launch the full delete account flow with password verification
        val intent = Intent(this, DeleteAccountActivity::class.java)
        startActivity(intent)
    }

    private fun showLogoutConfirmation() {
        val builder = AlertDialog.Builder(this, R.style.Theme_PawSociety_Dialog)
        builder.setTitle("Log Out")
        builder.setMessage("Are you sure you want to log out?")
        builder.setPositiveButton("Log Out") { _, _ ->
            performLogout()
        }
        builder.setNegativeButton("Cancel", null)
        val dialog = builder.show()
        
        // Style the buttons
        val logoutButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val cancelButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        
        logoutButton?.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
        logoutButton?.setBackgroundColor(android.graphics.Color.parseColor("#7A4F2B"))
        
        cancelButton?.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
        cancelButton?.setBackgroundColor(android.graphics.Color.parseColor("#999999"))
        
        // Style the title and message
        val title = dialog.findViewById<android.widget.TextView>(android.R.id.title)
        val message = dialog.findViewById<android.widget.TextView>(android.R.id.message)
        title?.setTextColor(android.graphics.Color.parseColor("#333333"))
        message?.setTextColor(android.graphics.Color.parseColor("#333333"))
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
