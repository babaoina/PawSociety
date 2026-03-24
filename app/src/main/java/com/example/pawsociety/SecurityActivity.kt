package com.example.pawsociety

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pawsociety.data.repository.UserRepository
import com.example.pawsociety.util.SecurityUtil
import com.example.pawsociety.util.SessionManager
import com.example.pawsociety.util.SocketManager
import kotlinx.coroutines.launch
import org.json.JSONObject

class SecurityActivity : AppCompatActivity() {

    companion object {
        private const val SECURITY_CHANGE_COOLDOWN_MS = 30L * 24L * 60L * 60L * 1000L
        private const val PREFS_SECURITY = "security_cooldown_prefs"
        private const val KEY_LAST_PASSWORD_CHANGE_AT = "last_password_change_at"
        private const val KEY_LAST_EMAIL_CHANGE_AT = "last_email_change_at"
    }

    private lateinit var sessionManager: SessionManager
    private lateinit var btnBack: ImageView
    private lateinit var btnChangePassword: LinearLayout
    private lateinit var btnEmail: LinearLayout
    private lateinit var btnPhone: LinearLayout
    private lateinit var btnSessions: LinearLayout
    private lateinit var tvPasswordTimer: TextView
    private lateinit var tvEmailTimer: TextView
    private lateinit var progressBar: ProgressBar

    private val userRepository = UserRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_security)

        sessionManager = SessionManager(this)

        initViews()
        setupClickListeners()
    }

    private fun initViews() {
        btnBack = findViewById(R.id.btn_back)
        btnChangePassword = findViewById(R.id.btn_change_password)
        btnEmail = findViewById(R.id.btn_email)
        btnPhone = findViewById(R.id.btn_phone)
        btnSessions = findViewById(R.id.btn_sessions)
        tvPasswordTimer = findViewById(R.id.tv_password_timer)
        tvEmailTimer = findViewById(R.id.tv_email_timer)
        progressBar = findViewById(R.id.progress_bar)
        progressBar.visibility = View.GONE
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener { finish() }
        btnChangePassword.setOnClickListener { showChangePasswordDialog() }
        btnEmail.setOnClickListener { showUpdateEmailDialog() }
        btnPhone.setOnClickListener { showUpdatePhoneDialog() }
        btnSessions.setOnClickListener { showActiveSessions() }
    }

    override fun onResume() {
        super.onResume()
        refreshSecurityCooldowns()
    }

    private fun refreshSecurityCooldowns() {
        updateSecurityCooldownUI()

        val currentUser = sessionManager.getCurrentUser() ?: return
        lifecycleScope.launch {
            val result = userRepository.getSecurityCooldowns(currentUser.firebaseUid)
            if (result.isSuccess) {
                val settings = result.getOrNull()
                settings?.passwordCooldownRemainingMs?.let { remaining ->
                    if (remaining > 0) {
                        saveCooldownTimestamp(KEY_LAST_PASSWORD_CHANGE_AT, System.currentTimeMillis() - (SECURITY_CHANGE_COOLDOWN_MS - remaining))
                    }
                }
                settings?.emailCooldownRemainingMs?.let { remaining ->
                    if (remaining > 0) {
                        saveCooldownTimestamp(KEY_LAST_EMAIL_CHANGE_AT, System.currentTimeMillis() - (SECURITY_CHANGE_COOLDOWN_MS - remaining))
                    }
                }
                if ((settings?.passwordCooldownRemainingMs ?: 0L) <= 0L) clearCooldownTimestamp(KEY_LAST_PASSWORD_CHANGE_AT)
                if ((settings?.emailCooldownRemainingMs ?: 0L) <= 0L) clearCooldownTimestamp(KEY_LAST_EMAIL_CHANGE_AT)
                updateSecurityCooldownUI()
            }
        }
    }

    private fun showChangePasswordDialog() {
        val passwordCooldownRemaining = getCooldownRemaining(KEY_LAST_PASSWORD_CHANGE_AT)
        if (passwordCooldownRemaining > 0L) {
            Toast.makeText(
                this,
                "You can change your password again in ${formatCooldown(passwordCooldownRemaining)}.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_change_password, null)
        val etCurrentPassword = dialogView.findViewById<EditText>(R.id.et_current_password)
        val etNewPassword = dialogView.findViewById<EditText>(R.id.et_new_password)
        val etConfirmPassword = dialogView.findViewById<EditText>(R.id.et_confirm_password)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btn_cancel)
        val btnChange = dialogView.findViewById<TextView>(R.id.btn_change)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progress_bar)

        val dialog = AlertDialog.Builder(this, R.style.Theme_PawSociety_Dialog)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }

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

            if (current == newPass) {
                Toast.makeText(this, "New password must be different from current password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnChange.isEnabled = false
            progressBar.visibility = View.VISIBLE
            etCurrentPassword.isEnabled = false
            etNewPassword.isEnabled = false
            etConfirmPassword.isEnabled = false

            changePassword(current, newPass, dialog, btnChange, progressBar, etCurrentPassword, etNewPassword, etConfirmPassword)
        }

        dialog.show()
    }

    private fun changePassword(
        oldPassword: String,
        newPassword: String,
        dialog: AlertDialog,
        btnChange: TextView,
        progressBar: ProgressBar,
        etCurrentPassword: EditText,
        etNewPassword: EditText,
        etConfirmPassword: EditText
    ) {
        val currentUser = sessionManager.getCurrentUser() ?: run {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val result = userRepository.changePassword(
                    firebaseUid = currentUser.firebaseUid,
                    oldPassword = oldPassword,
                    newPassword = newPassword
                )

                if (result.isSuccess) {
                    saveCooldownTimestamp(KEY_LAST_PASSWORD_CHANGE_AT, System.currentTimeMillis())
                    updateSecurityCooldownUI()
                    Toast.makeText(this@SecurityActivity, "Password changed successfully", Toast.LENGTH_SHORT).show()

                    val eventData = JSONObject().apply {
                        put("firebaseUid", currentUser.firebaseUid)
                        put("event", "password_changed")
                        put("timestamp", System.currentTimeMillis())
                    }
                    SocketManager.emit("security-settings-updated", eventData)

                    dialog.dismiss()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Failed to change password"
                    Toast.makeText(this@SecurityActivity, errorMsg, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("SecurityActivity", "Error changing password: ${e.message}")
                Toast.makeText(this@SecurityActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                progressBar.visibility = View.GONE
                btnChange.isEnabled = true
                etCurrentPassword.isEnabled = true
                etNewPassword.isEnabled = true
                etConfirmPassword.isEnabled = true
            }
        }
    }

    private fun showUpdateEmailDialog() {
        val emailCooldownRemaining = getCooldownRemaining(KEY_LAST_EMAIL_CHANGE_AT)
        if (emailCooldownRemaining > 0L) {
            Toast.makeText(
                this,
                "You can change your email again in ${formatCooldown(emailCooldownRemaining)}.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_update_email, null)
        val tvCurrentEmail = dialogView.findViewById<TextView>(R.id.tv_current_email)
        val etNewEmail = dialogView.findViewById<EditText>(R.id.et_new_email)
        val etPassword = dialogView.findViewById<EditText>(R.id.et_password) ?: EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Current Password"
            (dialogView as? LinearLayout)?.addView(this, 0)
        }
        val btnCancel = dialogView.findViewById<TextView>(R.id.btn_cancel)
        val btnUpdate = dialogView.findViewById<TextView>(R.id.btn_update)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progress_bar) ?: ProgressBar(this).apply {
            visibility = View.GONE
            (dialogView as? LinearLayout)?.addView(this)
        }

        val currentUser = sessionManager.getCurrentUser()
        tvCurrentEmail.text = currentUser?.email ?: "Not set"

        val dialog = AlertDialog.Builder(this, R.style.Theme_PawSociety_Dialog)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnUpdate.setOnClickListener {
            val newEmail = etNewEmail.text.toString().trim()
            val password = etPassword.text.toString()

            if (newEmail.isEmpty()) {
                Toast.makeText(this, "Email is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.isEmpty()) {
                Toast.makeText(this, "Password is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(newEmail).matches()) {
                Toast.makeText(this, "Please enter a valid email", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            btnUpdate.isEnabled = false
            etNewEmail.isEnabled = false
            etPassword.isEnabled = false

            lifecycleScope.launch {
                try {
                    if (currentUser == null) {
                        Toast.makeText(this@SecurityActivity, "User not logged in", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val result = userRepository.changeEmail(currentUser.firebaseUid, newEmail, password)

                    if (result.isSuccess) {
                        saveCooldownTimestamp(KEY_LAST_EMAIL_CHANGE_AT, System.currentTimeMillis())
                        updateSecurityCooldownUI()
                        Toast.makeText(this@SecurityActivity, "Email changed successfully", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Failed to change email"
                        Toast.makeText(this@SecurityActivity, error, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("SecurityActivity", "Error changing email: ${e.message}")
                    Toast.makeText(this@SecurityActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    progressBar.visibility = View.GONE
                    btnUpdate.isEnabled = true
                    etNewEmail.isEnabled = true
                    etPassword.isEnabled = true
                }
            }
        }

        dialog.show()
    }

    private fun updateSecurityCooldownUI() {
        val passwordRemaining = getCooldownRemaining(KEY_LAST_PASSWORD_CHANGE_AT)
        val emailRemaining = getCooldownRemaining(KEY_LAST_EMAIL_CHANGE_AT)

        updateCooldownRow(
            button = btnChangePassword,
            timerView = tvPasswordTimer,
            remainingMs = passwordRemaining,
            lockedMessage = "Password is locked. You can change it again in ${formatCooldown(passwordRemaining)}.",
            availableMessage = "You can change your password now. After that, the next change is available in 30 days."
        )

        updateCooldownRow(
            button = btnEmail,
            timerView = tvEmailTimer,
            remainingMs = emailRemaining,
            lockedMessage = "Email is locked. You can change it again in ${formatCooldown(emailRemaining)}.",
            availableMessage = "You can change your email now. After that, the next change is available in 30 days."
        )
    }

    private fun updateCooldownRow(
        button: LinearLayout,
        timerView: TextView,
        remainingMs: Long,
        lockedMessage: String,
        availableMessage: String
    ) {
        val isLocked = remainingMs > 0L
        button.isEnabled = !isLocked
        button.isClickable = !isLocked
        button.alpha = if (isLocked) 0.65f else 1f
        timerView.visibility = View.VISIBLE
        timerView.text = if (isLocked) lockedMessage else availableMessage
        timerView.setTextColor(
            if (isLocked) android.graphics.Color.parseColor("#C57A1F")
            else android.graphics.Color.parseColor("#8A8A8A")
        )
    }

    private fun getCooldownRemaining(key: String): Long {
        val prefs = getSharedPreferences(PREFS_SECURITY, MODE_PRIVATE)
        val lastChangedAt = prefs.getLong(key, 0L)
        if (lastChangedAt <= 0L) return 0L
        val nextAllowedAt = lastChangedAt + SECURITY_CHANGE_COOLDOWN_MS
        return (nextAllowedAt - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    private fun saveCooldownTimestamp(key: String, timestamp: Long) {
        getSharedPreferences(PREFS_SECURITY, MODE_PRIVATE)
            .edit()
            .putLong(key, timestamp)
            .apply()
    }

    private fun clearCooldownTimestamp(key: String) {
        getSharedPreferences(PREFS_SECURITY, MODE_PRIVATE)
            .edit()
            .remove(key)
            .apply()
    }

    private fun formatCooldown(remainingMs: Long): String {
        val totalHours = remainingMs / (60 * 60 * 1000)
        val days = totalHours / 24
        val hours = totalHours % 24

        return when {
            days > 0 -> "$days day${if (days == 1L) "" else "s"}${if (hours > 0) " and $hours hour${if (hours == 1L) "" else "s"}" else ""}"
            hours > 0 -> "$hours hour${if (hours == 1L) "" else "s"}"
            else -> "less than 1 hour"
        }
    }

    private fun showUpdatePhoneDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_update_phone, null)
        val etNewPhone = dialogView.findViewById<EditText>(R.id.et_new_phone)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btn_cancel)
        val btnUpdate = dialogView.findViewById<TextView>(R.id.btn_update)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progress_bar) ?: ProgressBar(this).apply {
            visibility = View.GONE
            (dialogView as? LinearLayout)?.addView(this)
        }

        val dialog = AlertDialog.Builder(this, R.style.Theme_PawSociety_Dialog)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnUpdate.setOnClickListener {
            val newPhone = etNewPhone.text.toString().trim()

            if (newPhone.isEmpty()) {
                Toast.makeText(this, "Phone number is required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (newPhone.length < 10 || !newPhone.matches(Regex("^[0-9+\\-\\s]+$"))) {
                Toast.makeText(this, "Please enter a valid phone number", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            btnUpdate.isEnabled = false
            etNewPhone.isEnabled = false

            lifecycleScope.launch {
                try {
                    val currentUser = sessionManager.getCurrentUser()
                    if (currentUser == null) {
                        Toast.makeText(this@SecurityActivity, "User not logged in", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val result = userRepository.updatePhone(currentUser.firebaseUid, newPhone)

                    if (result.isSuccess) {
                        Toast.makeText(this@SecurityActivity, "Phone number updated successfully", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    } else {
                        val error = result.exceptionOrNull()?.message ?: "Failed to update phone"
                        Toast.makeText(this@SecurityActivity, error, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("SecurityActivity", "Error updating phone: ${e.message}")
                    Toast.makeText(this@SecurityActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    progressBar.visibility = View.GONE
                    btnUpdate.isEnabled = true
                    etNewPhone.isEnabled = true
                }
            }
        }

        dialog.show()
    }

    private fun showActiveSessions() {
        val currentUser = sessionManager.getCurrentUser() ?: return
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val result = userRepository.getActiveSessions(currentUser.firebaseUid)
                progressBar.visibility = View.GONE

                if (result.isSuccess) {
                    showSessionsDialog(result.getOrNull() ?: emptyList())
                } else {
                    showCurrentDeviceSession()
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Log.e("SecurityActivity", "Error loading sessions: ${e.message}")
                showCurrentDeviceSession()
            }
        }
    }

    private fun showSessionsDialog(sessions: List<Map<String, Any>>) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_active_sessions, null)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btn_cancel)
        val btnLogoutAll = dialogView.findViewById<TextView>(R.id.btn_logout_all)
        val sessionsContainer = dialogView.findViewById<LinearLayout>(R.id.sessions_container)
        val currentDeviceInfo = SecurityUtil.getDeviceInfo()

        for (session in sessions) {
            val sessionView = layoutInflater.inflate(R.layout.item_session, sessionsContainer, false)
            val tvDeviceName = sessionView.findViewById<TextView>(R.id.tv_device_name)
            val tvLastActive = sessionView.findViewById<TextView>(R.id.tv_last_active)
            val btnLogout = sessionView.findViewById<ImageView>(R.id.btn_logout)
            val isCurrent = sessionView.findViewById<View>(R.id.current_badge)

            val deviceName = session["deviceName"] as? String ?: session["model"] as? String ?: "Unknown Device"
            val lastActive = session["lastActive"] as? Long ?: System.currentTimeMillis()
            val sessionId = session["sessionId"] as? String

            tvDeviceName.text =
                deviceName + if (deviceName.contains(currentDeviceInfo.phoneModel, ignoreCase = true)) " (Current)" else ""
            tvLastActive.text = "Active: ${SecurityUtil.formatLoginTime(lastActive)}"
            isCurrent.visibility =
                if (deviceName.contains(currentDeviceInfo.phoneModel, ignoreCase = true)) View.VISIBLE else View.GONE

            btnLogout.setOnClickListener {
                if (sessionId != null) {
                    logoutFromDevice(sessionId)
                }
            }

            sessionsContainer.addView(sessionView)
        }

        val dialog = AlertDialog.Builder(this, R.style.Theme_PawSociety_Dialog)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnLogoutAll.setOnClickListener {
            logoutFromAllDevices()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showCurrentDeviceSession() {
        val deviceDisplay = SecurityUtil.getDeviceDisplayName()
        val dialogView = layoutInflater.inflate(R.layout.dialog_active_sessions, null)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btn_cancel)
        val sessionsContainer = dialogView.findViewById<LinearLayout>(R.id.sessions_container)
        val sessionView = layoutInflater.inflate(R.layout.item_session, sessionsContainer, false)
        val tvDeviceName = sessionView.findViewById<TextView>(R.id.tv_device_name)
        val tvLastActive = sessionView.findViewById<TextView>(R.id.tv_last_active)
        val isCurrent = sessionView.findViewById<View>(R.id.current_badge)

        tvDeviceName.text = "$deviceDisplay (Current)"
        tvLastActive.text = "Active: Now"
        isCurrent.visibility = View.VISIBLE
        sessionsContainer.addView(sessionView)

        val dialog = AlertDialog.Builder(this, R.style.Theme_PawSociety_Dialog)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun logoutFromDevice(sessionId: String) {
        val currentUser = sessionManager.getCurrentUser() ?: return

        lifecycleScope.launch {
            try {
                val result = userRepository.logoutSession(currentUser.firebaseUid, sessionId)

                if (result.isSuccess) {
                    Toast.makeText(this@SecurityActivity, "Device logged out", Toast.LENGTH_SHORT).show()
                    val eventData = JSONObject().apply {
                        put("firebaseUid", currentUser.firebaseUid)
                        put("sessionId", sessionId)
                        put("timestamp", System.currentTimeMillis())
                    }
                    SocketManager.emit("session-logged-out", eventData)
                } else {
                    Toast.makeText(this@SecurityActivity, "Failed to logout device", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SecurityActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun logoutFromAllDevices() {
        val currentUser = sessionManager.getCurrentUser() ?: return

        lifecycleScope.launch {
            try {
                val result = userRepository.logoutAllSessions(currentUser.firebaseUid)

                if (result.isSuccess) {
                    Toast.makeText(this@SecurityActivity, "Logged out from all devices", Toast.LENGTH_LONG).show()
                    val eventData = JSONObject().apply {
                        put("firebaseUid", currentUser.firebaseUid)
                        put("timestamp", System.currentTimeMillis())
                    }
                    SocketManager.emit("all-sessions-logged-out", eventData)
                } else {
                    Toast.makeText(this@SecurityActivity, "Failed to logout", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@SecurityActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
