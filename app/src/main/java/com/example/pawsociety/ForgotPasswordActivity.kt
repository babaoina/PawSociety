package com.example.pawsociety

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.pawsociety.data.repository.AuthRepository
import com.example.pawsociety.util.FirebaseAuthHelper
import com.example.pawsociety.util.KeyboardAwareScrollHelper
import kotlinx.coroutines.launch

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var resetButton: Button
    private lateinit var backButton: ImageView
    private lateinit var emailError: TextView
    private lateinit var successMessage: TextView
    private lateinit var successContainer: View
    private var resetCooldown = false
    private val authRepository = AuthRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        initializeViews()
        KeyboardAwareScrollHelper.attach(emailInput)
        setupClickListeners()
        setupValidation()
    }

    private fun initializeViews() {
        emailInput = findViewById(R.id.forgot_password_email)
        resetButton = findViewById(R.id.forgot_password_reset_button)
        backButton = findViewById(R.id.forgot_password_back_button)
        emailError = findViewById(R.id.forgot_password_email_error)
        successMessage = findViewById(R.id.forgot_password_success_message)
        successContainer = findViewById(R.id.forgot_password_success_container)
    }

    private fun setupClickListeners() {
        backButton.setOnClickListener {
            finish()
        }

        resetButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            if (validateEmail(email) && !resetCooldown) {
                sendPasswordReset(email)
            }
        }
    }

    private fun setupValidation() {
        emailInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (emailInput.text.toString().trim().isNotEmpty()) {
                    emailInput.background = ContextCompat.getDrawable(
                        this@ForgotPasswordActivity,
                        R.drawable.input_rounded
                    )
                    emailError.visibility = View.GONE
                }
            }
        })
    }

    private fun validateEmail(email: String): Boolean {
        if (email.isEmpty()) {
            emailInput.background =
                ContextCompat.getDrawable(this, R.drawable.input_oval_error)
            emailError.text = "Email is required"
            emailError.visibility = View.VISIBLE
            return false
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.background =
                ContextCompat.getDrawable(this, R.drawable.input_oval_error)
            emailError.text = "Invalid email address"
            emailError.visibility = View.VISIBLE
            return false
        }

        return true
    }

    private fun sendPasswordReset(email: String) {
        resetButton.isEnabled = false
        resetButton.text = "Sending..."
        resetCooldown = true

        lifecycleScope.launch {
            try {
                // First try backend API
                val apiResult = authRepository.forgotPassword(email)
                
                if (apiResult.isSuccess) {
                    // Also attempt Firebase as backup
                    FirebaseAuthHelper.sendPasswordResetEmail(email)
                    showSuccess(email)
                } else {
                    val error = apiResult.exceptionOrNull()?.message ?: "Failed to send reset email"
                    showError(error)
                    resetButtonState()
                }
            } catch (e: Exception) {
                showError(e.message ?: "An unexpected error occurred")
                resetButtonState()
            }
        }
    }

    private fun showSuccess(email: String) {
        // Hide input fields
        emailInput.visibility = View.GONE
        emailError.visibility = View.GONE
        resetButton.visibility = View.GONE

        // Show success message
        successContainer.visibility = View.VISIBLE
        successMessage.text = "✓ Password reset link sent to\n$email\n\nCheck your email and follow the link to reset your password."

        // Auto-dismiss after 5 seconds
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            setResult(RESULT_OK, Intent().putExtra("email_reset_sent", true))
            finish()
        }, 5000)
    }

    private fun showError(errorMessage: String) {
        val userFriendlyError = when {
            errorMessage.contains("user-not-found", ignoreCase = true) -> "No account found with this email"
            errorMessage.contains("too-many-requests", ignoreCase = true) -> "Too many attempts. Please try again later"
            errorMessage.contains("invalid-email", ignoreCase = true) -> "Invalid email address"
            else -> errorMessage
        }

        Toast.makeText(this, userFriendlyError, Toast.LENGTH_LONG).show()
    }

    private fun resetButtonState() {
        resetButton.isEnabled = true
        resetButton.text = "Send Reset Link"
        resetCooldown = false
    }
}
