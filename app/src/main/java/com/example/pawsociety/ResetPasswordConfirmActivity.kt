package com.example.pawsociety

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.pawsociety.util.FirebaseAuthHelper
import com.example.pawsociety.util.KeyboardAwareScrollHelper
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.io.IOException

class ResetPasswordConfirmActivity : AppCompatActivity() {

    private lateinit var newPasswordInput: EditText
    private lateinit var confirmPasswordInput: EditText
    private lateinit var resetButton: Button
    private lateinit var backButton: ImageView
    private lateinit var eyeIcon1: ImageView
    private lateinit var eyeIcon2: ImageView
    private lateinit var newPasswordError: TextView
    private lateinit var confirmPasswordError: TextView
    private lateinit var successContainer: LinearLayout
    private lateinit var successMessage: TextView
    private lateinit var formContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var passwordStrengthIndicator: TextView
    
    private var oobCode: String? = null
    private var isPasswordVisible1 = false
    private var isPasswordVisible2 = false
    private var resetCooldown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password_confirm)

        // Extract oobCode from deep link
        oobCode = intent.data?.getQueryParameter("oobCode")
        
        Log.d("ResetPasswordConfirm", "📝 Reset Activity Created")
        Log.d("ResetPasswordConfirm", "🔗 Intent data: ${intent.data}")
        Log.d("ResetPasswordConfirm", "📋 oobCode extracted: ${if (oobCode.isNullOrEmpty()) "EMPTY/NULL" else "✅ Found (length: ${oobCode?.length})"}")
        
        if (oobCode == null) {
            Log.e("ResetPasswordConfirm", "❌ No oobCode found in deep link!")
            Toast.makeText(this, "Invalid password reset link", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        KeyboardAwareScrollHelper.attach(newPasswordInput, confirmPasswordInput)
        setupClickListeners()
        setupValidation()
    }

    private fun initializeViews() {
        newPasswordInput = findViewById(R.id.new_password_input)
        confirmPasswordInput = findViewById(R.id.confirm_password_input)
        resetButton = findViewById(R.id.reset_confirm_button)
        backButton = findViewById(R.id.reset_confirm_back_button)
        eyeIcon1 = findViewById(R.id.eye_icon_1)
        eyeIcon2 = findViewById(R.id.eye_icon_2)
        newPasswordError = findViewById(R.id.new_password_error)
        confirmPasswordError = findViewById(R.id.confirm_password_error)
        successContainer = findViewById(R.id.reset_success_container)
        successMessage = findViewById(R.id.reset_success_message)
        formContainer = findViewById(R.id.reset_form_container)
        progressBar = findViewById(R.id.reset_progress_bar)
        passwordStrengthIndicator = findViewById(R.id.password_strength_indicator)
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            finish()
        }

        resetButton.setOnClickListener {
            val newPassword = newPasswordInput.text.toString()
            val confirmPassword = confirmPasswordInput.text.toString()

            if (validatePasswords(newPassword, confirmPassword) && !resetCooldown) {
                confirmPasswordReset(newPassword)
            }
        }

        eyeIcon1.setOnClickListener {
            togglePasswordVisibility(newPasswordInput, eyeIcon1)
            isPasswordVisible1 = !isPasswordVisible1
        }

        eyeIcon2.setOnClickListener {
            togglePasswordVisibility(confirmPasswordInput, eyeIcon2)
            isPasswordVisible2 = !isPasswordVisible2
        }
    }

    private fun setupValidation() {
        newPasswordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePasswordStrength(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {
                if (newPasswordInput.text.toString().isNotEmpty()) {
                    newPasswordInput.background =
                        ContextCompat.getDrawable(this@ResetPasswordConfirmActivity, R.drawable.input_rounded)
                    newPasswordError.visibility = View.GONE
                }
            }
        })

        confirmPasswordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (confirmPasswordInput.text.toString().isNotEmpty()) {
                    confirmPasswordInput.background =
                        ContextCompat.getDrawable(this@ResetPasswordConfirmActivity, R.drawable.input_rounded)
                    confirmPasswordError.visibility = View.GONE
                }
            }
        })
    }

    private fun updatePasswordStrength(password: String) {
        when {
            password.length < 6 -> {
                passwordStrengthIndicator.text = "Weak"
                passwordStrengthIndicator.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
            }
            password.length < 10 -> {
                passwordStrengthIndicator.text = "Medium"
                passwordStrengthIndicator.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
            }
            else -> {
                passwordStrengthIndicator.text = "Strong"
                passwordStrengthIndicator.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            }
        }
    }

    private fun togglePasswordVisibility(editText: EditText, eyeIcon: ImageView) {
        val currentText = editText.text.toString()
        val cursorPosition = editText.selectionStart

        if (editText.inputType == (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            eyeIcon.setImageResource(R.drawable.ic_eye_open)
        } else {
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            eyeIcon.setImageResource(R.drawable.ic_eye_closed)
        }

        editText.setText(currentText)
        editText.setSelection(cursorPosition.coerceAtMost(currentText.length))
    }

    private fun validatePasswords(newPassword: String, confirmPassword: String): Boolean {
        var isValid = true

        // Validate new password
        if (newPassword.isEmpty()) {
            newPasswordInput.background =
                ContextCompat.getDrawable(this, R.drawable.input_oval_error)
            newPasswordError.text = "Password is required"
            newPasswordError.visibility = View.VISIBLE
            isValid = false
        } else if (newPassword.length < 6) {
            newPasswordInput.background =
                ContextCompat.getDrawable(this, R.drawable.input_oval_error)
            newPasswordError.text = "Password must be at least 6 characters"
            newPasswordError.visibility = View.VISIBLE
            isValid = false
        } else if (!newPassword.matches(Regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$"))) {
            newPasswordInput.background =
                ContextCompat.getDrawable(this, R.drawable.input_oval_error)
            newPasswordError.text = "Password must contain uppercase, lowercase, and numbers"
            newPasswordError.visibility = View.VISIBLE
            isValid = false
        }

        // Validate confirm password
        if (confirmPassword.isEmpty()) {
            confirmPasswordInput.background =
                ContextCompat.getDrawable(this, R.drawable.input_oval_error)
            confirmPasswordError.text = "Please confirm your password"
            confirmPasswordError.visibility = View.VISIBLE
            isValid = false
        } else if (confirmPassword != newPassword) {
            confirmPasswordInput.background =
                ContextCompat.getDrawable(this, R.drawable.input_oval_error)
            confirmPasswordError.text = "Passwords do not match"
            confirmPasswordError.visibility = View.VISIBLE
            isValid = false
        }

        return isValid
    }

    private fun confirmPasswordReset(newPassword: String) {
        resetButton.isEnabled = false
        progressBar.visibility = View.VISIBLE
        resetButton.text = "Updating Password..."
        resetCooldown = true

        Log.d("ResetPasswordConfirm", "🔐 Starting password reset with oobCode: ${oobCode?.substring(0, 10)}...")

        lifecycleScope.launch {
            try {
                // Use Firebase to confirm password reset with oobCode
                Log.d("ResetPasswordConfirm", "📡 Calling Firebase confirmPasswordReset...")
                val result = FirebaseAuthHelper.confirmPasswordReset(oobCode!!, newPassword)

                if (result.isSuccess) {
                    Log.d("ResetPasswordConfirm", "✅ Firebase confirmPasswordReset successful!")
                    
                    // Wait a moment for Firebase to propagate the change
                    kotlinx.coroutines.delay(1000)
                    
                    // Now try to verify by signing in with the new password
                    verifyPasswordResetWorked(newPassword)
                } else {
                    val firebaseError = result.exceptionOrNull()
                    Log.e("ResetPasswordConfirm", "❌ Firebase confirmPasswordReset failed: ${firebaseError?.message}")
                    firebaseError?.printStackTrace()
                    val error = firebaseError?.message ?: "Failed to reset password"
                    showError(error)
                    resetButtonState()
                }
            } catch (e: Exception) {
                Log.e("ResetPasswordConfirm", "❌ Exception during confirmPasswordReset: ${e.message}")
                e.printStackTrace()
                showError(e.message ?: "An unexpected error occurred")
                resetButtonState()
            }
        }
    }

    private suspend fun verifyPasswordResetWorked(newPassword: String) {
        try {
            Log.d("ResetPasswordConfirm", "🔍 Verifying password reset by attempting login...")
            
            // Try to get the email from the reset link metadata
            val firebaseAuth = FirebaseAuth.getInstance()
            
            // Check current Firebase user email (if any)
            val currentUser = firebaseAuth.currentUser
            val userEmail = currentUser?.email
            
            Log.d("ResetPasswordConfirm", "📧 Current Firebase user: $userEmail")
            
            if (userEmail != null) {
                // Try to verify the password reset worked by re-authenticating
                val verifyResult = FirebaseAuthHelper.loginWithEmail(userEmail, newPassword)
                
                if (verifyResult.isSuccess) {
                    Log.d("ResetPasswordConfirm", "✅ Password reset verified! User can login with new password")
                    FirebaseAuthHelper.signOut()
                    showSuccess()
                } else {
                    Log.w("ResetPasswordConfirm", "⚠️ Password verification failed: ${verifyResult.exceptionOrNull()?.message}")
                    // Still show success since Firebase confirmed the reset
                    showSuccess()
                }
            } else {
                Log.w("ResetPasswordConfirm", "⚠️ No email in Firebase, trusting Firebase confirmPasswordReset was successful")
                showSuccess()
            }
        } catch (e: Exception) {
            Log.e("ResetPasswordConfirm", "❌ Verification error: ${e.message}")
            e.printStackTrace()
            // Still show success since Firebase confirmed the reset
            showSuccess()
        }
    }

    private fun showSuccess() {
        Log.d("ResetPasswordConfirm", "✅ Showing success screen")
        
        // Hide form
        formContainer.visibility = View.GONE
        progressBar.visibility = View.GONE

        // Show success message
        successContainer.visibility = View.VISIBLE
        successMessage.text = "✓ Password Reset Successfully!\n\nYour password has been updated. You can now log in with your new password."

        // Wait 4 seconds (give Firebase more time to sync) then redirect
        Log.d("ResetPasswordConfirm", "⏳ Waiting 4 seconds before redirecting to login...")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            Log.d("ResetPasswordConfirm", "➡️ Redirecting to LoginActivity")
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }, 4000)
    }

    private fun showError(errorMessage: String) {
        Log.e("ResetPasswordConfirm", "❌ Showing error: $errorMessage")
        
        val userFriendlyError = when {
            errorMessage.contains("timeout", ignoreCase = true) || 
            errorMessage.contains("network", ignoreCase = true) ||
            errorMessage is IOException -> "Network error. Please check your connection and try again"
            
            errorMessage.contains("invalid", ignoreCase = true) -> "This password reset link has expired. Please request a new one."
            
            errorMessage.contains("used", ignoreCase = true) -> "This link has already been used. Please request a new password reset."
            
            errorMessage.contains("INVALID_OOB_CODE", ignoreCase = true) -> "Invalid reset link. Please request a new password reset from the app."
            
            errorMessage.contains("EXPIRED_OOB_CODE", ignoreCase = true) -> "This reset link has expired. Please request a new one."
            
            else -> errorMessage
        }

        Toast.makeText(this, userFriendlyError, Toast.LENGTH_LONG).show()
    }

    private fun resetButtonState() {
        resetButton.isEnabled = true
        resetButton.text = "Update Password"
        progressBar.visibility = View.GONE
        resetCooldown = false
    }
}
