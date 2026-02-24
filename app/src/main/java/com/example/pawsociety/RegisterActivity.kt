package com.example.pawsociety

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
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
import com.example.pawsociety.api.ApiUser
import com.example.pawsociety.data.repository.AuthRepository
import com.example.pawsociety.util.FirebaseAuthHelper
import com.example.pawsociety.util.SessionManager
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {

    private lateinit var etLastName: EditText
    private lateinit var etFirstName: EditText
    private lateinit var etMiddleInitial: EditText
    private lateinit var etUsername: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var etPhone: EditText
    private lateinit var eyeIconPassword: ImageView
    private lateinit var eyeIconConfirmPassword: ImageView

    // Error TextViews
    private lateinit var lastNameError: TextView
    private lateinit var firstNameError: TextView
    private lateinit var middleInitialError: TextView
    private lateinit var usernameError: TextView
    private lateinit var emailError: TextView
    private lateinit var passwordError: TextView
    private lateinit var confirmPasswordError: TextView
    private lateinit var phoneError: TextView

    private val authRepository = AuthRepository()
    private lateinit var sessionManager: SessionManager

    private var isCreatingAccount = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        sessionManager = SessionManager(this)

        initializeViews()
        setupValidationListeners()
        setupClickListeners()
        setupEyeIcons()
    }

    private fun initializeViews() {
        etLastName = findViewById(R.id.et_last_name)
        etFirstName = findViewById(R.id.et_first_name)
        etMiddleInitial = findViewById(R.id.et_middle_initial)
        etUsername = findViewById(R.id.et_username)
        etEmail = findViewById(R.id.et_email)
        etPassword = findViewById(R.id.et_password)
        etConfirmPassword = findViewById(R.id.et_confirm_password)
        etPhone = findViewById(R.id.et_phone)
        eyeIconPassword = findViewById(R.id.eye_icon_password)
        eyeIconConfirmPassword = findViewById(R.id.eye_icon_confirm_password)

        // Initialize error TextViews
        lastNameError = findViewById(R.id.last_name_error)
        firstNameError = findViewById(R.id.first_name_error)
        middleInitialError = findViewById(R.id.middle_initial_error)
        usernameError = findViewById(R.id.username_error)
        emailError = findViewById(R.id.email_error)
        passwordError = findViewById(R.id.password_error)
        confirmPasswordError = findViewById(R.id.confirm_password_error)
        phoneError = findViewById(R.id.phone_error)
    }

    private fun setupClickListeners() {
        val backButton = findViewById<Button>(R.id.btn_back)
        val createAccountButton = findViewById<Button>(R.id.btn_create_account)

        backButton.setOnClickListener {
            finish()
        }

        createAccountButton.setOnClickListener {
            if (!isCreatingAccount && validateAllFields()) {
                createAccount()
            }
        }
    }

    private fun setupEyeIcons() {
        // Password eye icon
        var isPasswordVisible = false

        eyeIconPassword.setOnClickListener {
            // Save current text
            val currentText = etPassword.text.toString()
            val cursorPosition = etPassword.selectionStart

            if (isPasswordVisible) {
                // Hide password
                etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                eyeIconPassword.setImageResource(R.drawable.ic_eye_closed)
            } else {
                // Show password
                etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                eyeIconPassword.setImageResource(R.drawable.ic_eye_open)
            }

            // Toggle state
            isPasswordVisible = !isPasswordVisible

            // Restore text and cursor position
            etPassword.setText(currentText)
            try {
                etPassword.setSelection(cursorPosition.coerceAtMost(currentText.length))
            } catch (e: Exception) {
                etPassword.setSelection(currentText.length)
            }

            // KEEP NORMAL FONT (no techy monospace)
            etPassword.typeface = android.graphics.Typeface.DEFAULT
        }

        // Confirm Password eye icon
        var isConfirmPasswordVisible = false

        eyeIconConfirmPassword.setOnClickListener {
            // Save current text
            val currentText = etConfirmPassword.text.toString()
            val cursorPosition = etConfirmPassword.selectionStart

            if (isConfirmPasswordVisible) {
                // Hide password
                etConfirmPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                eyeIconConfirmPassword.setImageResource(R.drawable.ic_eye_closed)
            } else {
                // Show password
                etConfirmPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                eyeIconConfirmPassword.setImageResource(R.drawable.ic_eye_open)
            }

            // Toggle state
            isConfirmPasswordVisible = !isConfirmPasswordVisible

            // Restore text and cursor position
            etConfirmPassword.setText(currentText)
            try {
                etConfirmPassword.setSelection(cursorPosition.coerceAtMost(currentText.length))
            } catch (e: Exception) {
                etConfirmPassword.setSelection(currentText.length)
            }

            // KEEP NORMAL FONT (no techy monospace)
            etConfirmPassword.typeface = android.graphics.Typeface.DEFAULT
        }
    }

    private fun setupValidationListeners() {
        // Last Name validation
        etLastName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateLastName()
            }
        })

        // First Name validation
        etFirstName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateFirstName()
            }
        })

        // Middle initial validation
        etMiddleInitial.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateMiddleInitial()
            }
        })

        // Username validation
        etUsername.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateUsername()
            }
        })

        // Email validation
        etEmail.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateEmail()
            }
        })

        // Password validation
        etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validatePassword()
                if (etConfirmPassword.text.toString().isNotEmpty()) {
                    validateConfirmPassword()
                }
            }
        })

        // Confirm Password validation
        etConfirmPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                validateConfirmPassword()
            }
        })

        // Phone validation with auto-formatting
        etPhone.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()
                val digits = input.filter { it.isDigit() }
                if (digits.length > 11) {
                    etPhone.setText(digits.substring(0, 11))
                    etPhone.setSelection(11)
                } else if (input != digits) {
                    etPhone.setText(digits)
                    etPhone.setSelection(digits.length)
                }
                validatePhone()
            }
        })
    }

    private fun validateLastName(): Boolean {
        val name = etLastName.text.toString().trim()
        return when {
            name.isEmpty() -> {
                etLastName.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
                lastNameError.text = "Last name is required"
                lastNameError.visibility = View.VISIBLE
                false
            }
            name.length < 2 -> {
                etLastName.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
                lastNameError.text = "Last name must be at least 2 characters"
                lastNameError.visibility = View.VISIBLE
                false
            }
            !name.matches(Regex("^[a-zA-Z\\s.-]+$")) -> {
                etLastName.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
                lastNameError.text = "Only letters, spaces, dots, and hyphens allowed"
                lastNameError.visibility = View.VISIBLE
                false
            }
            else -> {
                etLastName.background = ContextCompat.getDrawable(this, R.drawable.input_oval)
                lastNameError.visibility = View.GONE
                true
            }
        }
    }

    private fun validateFirstName(): Boolean {
        val name = etFirstName.text.toString().trim()
        return when {
            name.isEmpty() -> {
                etFirstName.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
                firstNameError.text = "First name is required"
                firstNameError.visibility = View.VISIBLE
                false
            }
            name.length < 2 -> {
                etFirstName.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
                firstNameError.text = "First name must be at least 2 characters"
                firstNameError.visibility = View.VISIBLE
                false
            }
            !name.matches(Regex("^[a-zA-Z\\s.-]+$")) -> {
                etFirstName.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
                firstNameError.text = "Only letters, spaces, dots, and hyphens allowed"
                firstNameError.visibility = View.VISIBLE
                false
            }
            else -> {
                etFirstName.background = ContextCompat.getDrawable(this, R.drawable.input_oval)
                firstNameError.visibility = View.GONE
                true
            }
        }
    }

    private fun validateMiddleInitial(): Boolean {
        val initial = etMiddleInitial.text.toString().trim()
        return when {
            initial.isEmpty() -> {
                etMiddleInitial.background = ContextCompat.getDrawable(this, R.drawable.input_oval)
                middleInitialError.visibility = View.GONE
                true // Optional
            }
            initial.length != 1 -> {
                etMiddleInitial.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
                middleInitialError.text = "Must be a single letter"
                middleInitialError.visibility = View.VISIBLE
                false
            }
            !initial[0].isLetter() -> {
                etMiddleInitial.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
                middleInitialError.text = "Must be a letter"
                middleInitialError.visibility = View.VISIBLE
                false
            }
            else -> {
                etMiddleInitial.background = ContextCompat.getDrawable(this, R.drawable.input_oval)
                middleInitialError.visibility = View.GONE
                true
            }
        }
    }

    private fun validateUsername(): Boolean {
        val username = etUsername.text.toString().trim()
        return when {
            username.isEmpty() -> {
                etUsername.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
                usernameError.text = "Username is required"
                usernameError.visibility = View.VISIBLE
                false
            }
            username.length < 3 -> {
                etUsername.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
                usernameError.text = "Username must be at least 3 characters"
                usernameError.visibility = View.VISIBLE
                false
            }
            username.length > 20 -> {
                etUsername.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
                usernameError.text = "Username must be less than 20 characters"
                usernameError.visibility = View.VISIBLE
                false
            }
            !username.matches(Regex("^[a-zA-Z0-9._]+$")) -> {
                etUsername.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
                usernameError.text = "Only letters, numbers, dots, and underscores allowed"
                usernameError.visibility = View.VISIBLE
                false
            }
            username.matches(Regex("^[0-9].*")) -> {
                etUsername.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
                usernameError.text = "Username cannot start with a number"
                usernameError.visibility = View.VISIBLE
                false
            }
            else -> {
                etUsername.background = ContextCompat.getDrawable(this, R.drawable.input_oval)
                usernameError.visibility = View.GONE
                true
            }
        }
    }

    private fun validateEmail(): Boolean {
        val email = etEmail.text.toString().trim()
        return when {
            email.isEmpty() -> {
                etEmail.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
                emailError.text = "Email is required"
                emailError.visibility = View.VISIBLE
                false
            }
            !email.endsWith("@gmail.com", ignoreCase = true) -> {
                etEmail.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
                emailError.text = "Only Gmail addresses are allowed (@gmail.com)"
                emailError.visibility = View.VISIBLE
                false
            }
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                etEmail.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
                emailError.text = "Please enter a valid email address"
                emailError.visibility = View.VISIBLE
                false
            }
            else -> {
                etEmail.background = ContextCompat.getDrawable(this, R.drawable.input_oval)
                emailError.visibility = View.GONE
                true
            }
        }
    }

    private fun validatePassword(): Boolean {
        val password = etPassword.text.toString()
        return when {
            password.isEmpty() -> {
                etPassword.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
                passwordError.text = "Password is required"
                passwordError.visibility = View.VISIBLE
                false
            }
            password.length < 8 -> {
                etPassword.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
                passwordError.text = "Password must be at least 8 characters"
                passwordError.visibility = View.VISIBLE
                false
            }
            !password.matches(Regex(".*[A-Z].*")) -> {
                etPassword.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
                passwordError.text = "Must contain at least one uppercase letter"
                passwordError.visibility = View.VISIBLE
                false
            }
            !password.matches(Regex(".*[0-9].*")) -> {
                etPassword.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
                passwordError.text = "Must contain at least one number"
                passwordError.visibility = View.VISIBLE
                false
            }
            password.contains(" ") -> {
                etPassword.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
                passwordError.text = "Password cannot contain spaces"
                passwordError.visibility = View.VISIBLE
                false
            }
            else -> {
                etPassword.background = ContextCompat.getDrawable(this, R.drawable.input_oval)
                passwordError.visibility = View.GONE
                true
            }
        }
    }

    private fun validateConfirmPassword(): Boolean {
        val password = etPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()
        return when {
            confirmPassword.isEmpty() -> {
                etConfirmPassword.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
                confirmPasswordError.text = "Please confirm your password"
                confirmPasswordError.visibility = View.VISIBLE
                false
            }
            password != confirmPassword -> {
                etConfirmPassword.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
                confirmPasswordError.text = "Passwords do not match"
                confirmPasswordError.visibility = View.VISIBLE
                false
            }
            else -> {
                etConfirmPassword.background = ContextCompat.getDrawable(this, R.drawable.input_oval)
                confirmPasswordError.visibility = View.GONE
                true
            }
        }
    }

    private fun validatePhone(): Boolean {
        val phone = etPhone.text.toString().trim()
        return when {
            phone.isEmpty() -> {
                etPhone.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
                phoneError.text = "Phone number is required"
                phoneError.visibility = View.VISIBLE
                false
            }
            phone.length != 11 -> {
                etPhone.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
                phoneError.text = "Phone number must be exactly 11 digits"
                phoneError.visibility = View.VISIBLE
                false
            }
            !phone.matches(Regex("^09\\d{9}$")) -> {
                etPhone.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
                phoneError.text = "Must start with 09 (e.g., 09123456789)"
                phoneError.visibility = View.VISIBLE
                false
            }
            else -> {
                etPhone.background = ContextCompat.getDrawable(this, R.drawable.input_oval)
                phoneError.visibility = View.GONE
                true
            }
        }
    }

    private fun validateAllFields(): Boolean {
        val isLastNameValid = validateLastName()
        val isFirstNameValid = validateFirstName()
        val isMiddleInitialValid = validateMiddleInitial()
        val isUsernameValid = validateUsername()
        val isEmailValid = validateEmail()
        val isPasswordValid = validatePassword()
        val isConfirmPasswordValid = validateConfirmPassword()
        val isPhoneValid = validatePhone()

        return isLastNameValid && isFirstNameValid && isMiddleInitialValid &&
                isUsernameValid && isEmailValid && isPasswordValid &&
                isConfirmPasswordValid && isPhoneValid
    }

    private fun createAccount() {
        isCreatingAccount = true
        val createAccountButton = findViewById<Button>(R.id.btn_create_account)
        createAccountButton.isEnabled = false
        createAccountButton.text = "Creating Account..."

        val lastName = etLastName.text.toString().trim()
        val firstName = etFirstName.text.toString().trim()
        val middleInitial = etMiddleInitial.text.toString().trim()
        val fullName = if (middleInitial.isNotEmpty()) {
            "$lastName, $firstName $middleInitial."
        } else {
            "$lastName, $firstName"
        }

        val username = etUsername.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        val phone = etPhone.text.toString().trim()

        lifecycleScope.launch {
            try {
                // Step 1: Register with Firebase
                println("🔐 Starting Firebase registration for: $email")
                val firebaseResult = FirebaseAuthHelper.registerWithEmail(email, password)

                if (firebaseResult.isFailure) {
                    val error = firebaseResult.exceptionOrNull()?.message ?: "Registration failed"
                    println("❌ Firebase registration failed: $error")
                    showError(getFirebaseAuthErrorMessage(error))
                    isCreatingAccount = false
                    createAccountButton.isEnabled = true
                    createAccountButton.text = "Create Account"
                    return@launch
                }

                val firebaseUser = firebaseResult.getOrNull()!!
                println("✅ Firebase registration successful! UID: ${firebaseUser.uid}")

                // Step 2: Create user in MongoDB via backend
                println("🌐 Creating user in backend...")
                val backendResult = authRepository.firebaseLogin(
                    firebaseUid = firebaseUser.uid,
                    email = firebaseUser.email ?: email,
                    username = username,
                    fullName = fullName,
                    phone = phone
                )

                if (backendResult.isFailure) {
                    val error = backendResult.exceptionOrNull()?.message ?: "Failed to connect to server"
                    println("❌ Backend registration failed: $error")
                    // Firebase succeeded but backend failed - create local session
                    val localUser = com.example.pawsociety.api.ApiUser(
                        firebaseUid = firebaseUser.uid,
                        email = firebaseUser.email ?: email,
                        username = username,
                        fullName = fullName
                    )
                    sessionManager.saveUserSession(localUser)
                    println("⚠️ Saved local session, proceeding to home...")

                    Toast.makeText(
                        this@RegisterActivity,
                        "Account created! (Offline mode)",
                        Toast.LENGTH_LONG
                    ).show()

                    // Navigate to Home
                    val intent = Intent(this@RegisterActivity, HomeActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                    return@launch
                }

                val apiUser = backendResult.getOrNull()!!
                println("✅ Backend user created! Username: ${apiUser.username}")

                // Step 3: Send email verification
                FirebaseAuthHelper.sendEmailVerification()
                println("📧 Email verification sent")

                // Step 4: Save session
                sessionManager.saveUserSession(apiUser)
                println("💾 Session saved")

                Toast.makeText(
                    this@RegisterActivity,
                    "Account created successfully! Please check your email to verify.",
                    Toast.LENGTH_LONG
                ).show()

                // Navigate to Home
                val intent = Intent(this@RegisterActivity, HomeActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()

            } catch (e: Exception) {
                println("❌ Unexpected error: ${e.message}")
                e.printStackTrace()
                showError(e.message ?: "An unexpected error occurred")
            } finally {
                isCreatingAccount = false
                createAccountButton.isEnabled = true
                createAccountButton.text = "Create Account"
            }
        }
    }

    private fun getFirebaseAuthErrorMessage(firebaseError: String): String {
        return when {
            firebaseError.contains("ERROR_EMAIL_ALREADY_IN_USE") -> "This email is already registered"
            firebaseError.contains("ERROR_INVALID_EMAIL") -> "Invalid email address"
            firebaseError.contains("ERROR_WEAK_PASSWORD") -> "Password is too weak"
            firebaseError.contains("ERROR_USER_DISABLED") -> "This account has been disabled"
            firebaseError.contains("ERROR_TOO_MANY_REQUESTS") -> "Too many attempts. Please try again later."
            else -> firebaseError
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}