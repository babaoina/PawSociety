package com.example.pawsociety

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.example.pawsociety.util.SessionManager

class SecurityActivity : AppCompatActivity() {

    private lateinit var sessionManager: SessionManager
    private lateinit var btnBack: ImageView
    private lateinit var switchTwoFactor: SwitchCompat
    private lateinit var switchLoginAlerts: SwitchCompat
    private lateinit var btnChangePassword: LinearLayout
    private lateinit var btnEmail: LinearLayout
    private lateinit var btnPhone: LinearLayout
    private lateinit var btnSessions: LinearLayout
    private lateinit var btnSave: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security)

        sessionManager = SessionManager(this)

        initViews()
        setupClickListeners()
        loadSecuritySettings()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btn_back)
        switchTwoFactor = findViewById(R.id.switch_two_factor)
        switchLoginAlerts = findViewById(R.id.switch_login_alerts)
        btnChangePassword = findViewById(R.id.btn_change_password)
        btnEmail = findViewById(R.id.btn_email)
        btnPhone = findViewById(R.id.btn_phone)
        btnSessions = findViewById(R.id.btn_sessions)
        btnSave = findViewById(R.id.btn_save)

        println("✅ SecurityActivity views initialized")
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnSave.setOnClickListener {
            saveSecuritySettings()
        }

        btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }

        btnEmail.setOnClickListener {
            showUpdateEmailDialog()
        }

        btnPhone.setOnClickListener {
            showUpdatePhoneDialog()
        }

        btnSessions.setOnClickListener {
            showActiveSessions()
        }
    }

    private fun loadSecuritySettings() {
        val prefs = getSharedPreferences("security_settings", MODE_PRIVATE)
        switchTwoFactor.isChecked = prefs.getBoolean("two_factor", false)
        switchLoginAlerts.isChecked = prefs.getBoolean("login_alerts", true)
    }

    private fun saveSecuritySettings() {
        val prefs = getSharedPreferences("security_settings", MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean("two_factor", switchTwoFactor.isChecked)
            putBoolean("login_alerts", switchLoginAlerts.isChecked)
            apply()
        }

        Toast.makeText(this, "Security settings saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun showChangePasswordDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val etCurrentPassword = dialogView.findViewById<EditText>(R.id.et_current_password)
        val etNewPassword = dialogView.findViewById<EditText>(R.id.et_new_password)
        val etConfirmPassword = dialogView.findViewById<EditText>(R.id.et_confirm_password)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btn_cancel)
        val btnChange = dialogView.findViewById<TextView>(R.id.btn_change)

        val dialog = AlertDialog.Builder(this, R.style.Theme_PawSociety_Dialog)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnChange.setOnClickListener {
            val current = etCurrentPassword.text.toString()
            val newPass = etNewPassword.text.toString()
            val confirm = etConfirmPassword.text.toString()

            if (current.isEmpty() || newPass.isEmpty() || confirm.isEmpty()) {
                Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPass != confirm) {
                Toast.makeText(this, "New passwords don't match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPass.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Call API to change password
            Toast.makeText(this, "Password changed successfully", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showUpdateEmailDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_update_email, null)
        val tvCurrentEmail = dialogView.findViewById<TextView>(R.id.tv_current_email)
        val etNewEmail = dialogView.findViewById<EditText>(R.id.et_new_email)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btn_cancel)
        val btnUpdate = dialogView.findViewById<TextView>(R.id.btn_update)

        // Get current user email from session
        val currentUser = sessionManager.getCurrentUser()
        tvCurrentEmail.text = currentUser?.email ?: "Not set"

        val dialog = AlertDialog.Builder(this, R.style.Theme_PawSociety_Dialog)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnUpdate.setOnClickListener {
            val newEmail = etNewEmail.text.toString()
            if (newEmail.isEmpty()) {
                Toast.makeText(this, "Email is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                // Call API to update email
                Toast.makeText(this, "Email updated successfully", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please enter a valid email", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun showUpdatePhoneDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_update_phone, null)
        val etCurrentPhone = dialogView.findViewById<EditText>(R.id.et_current_phone)
        val etNewPhone = dialogView.findViewById<EditText>(R.id.et_new_phone)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btn_cancel)
        val btnUpdate = dialogView.findViewById<TextView>(R.id.btn_update)

        val dialog = AlertDialog.Builder(this, R.style.Theme_PawSociety_Dialog)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnUpdate.setOnClickListener {
            val currentPhone = etCurrentPhone.text.toString()
            val newPhone = etNewPhone.text.toString()

            if (currentPhone.isEmpty() || newPhone.isEmpty()) {
                Toast.makeText(this, "Both fields are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPhone.length >= 10 && newPhone.matches(Regex("^[0-9]+$"))) {
                // Call API to update phone
                Toast.makeText(this, "Phone number updated successfully", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Please enter a valid phone number", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun showActiveSessions() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_active_sessions, null)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btn_cancel)
        val btnLogoutAll = dialogView.findViewById<TextView>(R.id.btn_logout_all)

        val dialog = AlertDialog.Builder(this, R.style.Theme_PawSociety_Dialog)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnLogoutAll.setOnClickListener {
            Toast.makeText(this, "Logged out from all devices", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    override fun onResume() {
        super.onResume()
        // Refresh security settings if needed
        loadSecuritySettings()
    }
}