package com.example.pawsociety

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.pawsociety.adapters.ImageCarouselAdapter
import com.example.pawsociety.data.repository.AuthRepository
import com.example.pawsociety.util.FCMTokenManager
import com.example.pawsociety.util.FirebaseAuthHelper
import com.example.pawsociety.util.SessionManager
import com.example.pawsociety.util.SocketManager
import com.example.pawsociety.utils.FadePageTransformer
// ✅ ADD THESE IMPORTS
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import android.util.Log

class LoginActivity : AppCompatActivity() {

    private lateinit var emailInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginButton: Button
    private lateinit var createAccountButton: TextView
    private lateinit var forgotPassword: TextView
    private lateinit var eyeIcon: ImageView
    private lateinit var emailError: TextView
    private lateinit var passwordError: TextView

    // Social login containers
    private lateinit var googleLoginContainer: LinearLayout
    private lateinit var emailLoginContainer: LinearLayout

    // Hidden form container
    private lateinit var emailFormContainer: LinearLayout
    private lateinit var mainContainer: LinearLayout
    private lateinit var btnBackFromEmail: TextView

    // Carousel
    private lateinit var imageCarousel: ViewPager2
    private lateinit var indicatorContainer: LinearLayout
    private val imageList = mutableListOf<Int>()
    private val handler = Handler(Looper.getMainLooper())
    private var currentPage = 0
    private lateinit var carouselAdapter: ImageCarouselAdapter

    // Google Sign In
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 9001

    private val authRepository = AuthRepository()
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        sessionManager = SessionManager(this)

        // Check if this is a force logout (skip auto-login in this case)
        val isForceLogout = intent.getBooleanExtra("force_logout", false)

        // Check if user is already logged in (but skip if force logout)
        if (!isForceLogout && sessionManager.isLoggedIn() && FirebaseAuthHelper.isSignedIn) {
            navigateToHome()
            return
        }

        initializeViews()
        setupImageCarousel()
        setupGoogleSignIn()
        setupClickListeners()
        setupEyeIcon()
        setupValidation()
    }

    private fun initializeViews() {
        emailInput = findViewById(R.id.email_input)
        passwordInput = findViewById(R.id.password_input)
        loginButton = findViewById(R.id.login_button)
        createAccountButton = findViewById(R.id.btn_create_account)
        forgotPassword = findViewById(R.id.forgot_password)
        eyeIcon = findViewById(R.id.eye_icon)
        emailError = findViewById(R.id.email_error)
        passwordError = findViewById(R.id.password_error)

        googleLoginContainer = findViewById(R.id.btn_continue_google)
        emailLoginContainer = findViewById(R.id.btn_continue_email)

        emailFormContainer = findViewById(R.id.email_form_container)
        mainContainer = findViewById(R.id.main_container)
        btnBackFromEmail = findViewById(R.id.btn_back_from_email)

        imageCarousel = findViewById(R.id.image_carousel)
        indicatorContainer = findViewById(R.id.indicator_container)
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .requestProfile()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun setupImageCarousel() {
        imageList.clear()
        imageList.addAll(
            listOf(
                R.drawable.dog1,
                R.drawable.dog2,
                R.drawable.dog3,
                R.drawable.cat1,
                R.drawable.cat2,
                R.drawable.cat3,
                R.drawable.fish1,
                R.drawable.fish2,
                R.drawable.bird1,
                R.drawable.bird2
            ).shuffled()
        )

        carouselAdapter = ImageCarouselAdapter(imageList)
        imageCarousel.adapter = carouselAdapter
        imageCarousel.setPageTransformer(FadePageTransformer())

        setupIndicators()

        imageCarousel.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentPage = position
                updateIndicators(position)
            }
        })

        startAutoSlide()
    }

    private fun setupIndicators() {
        indicatorContainer.removeAllViews()
        for (i in imageList.indices) {
            val dot = View(this)
            val params = LinearLayout.LayoutParams(8.dp, 8.dp)
            params.setMargins(4.dp, 0, 4.dp, 0)
            dot.layoutParams = params

            if (i == 0) {
                dot.setBackgroundResource(R.drawable.circle_filled_brown)
            } else {
                dot.setBackgroundResource(R.drawable.circle_white)
                dot.alpha = 0.5f
            }
            indicatorContainer.addView(dot)
        }
    }

    private fun updateIndicators(position: Int) {
        for (i in 0 until indicatorContainer.childCount) {
            val dot = indicatorContainer.getChildAt(i)
            if (i == position) {
                dot.setBackgroundResource(R.drawable.circle_filled_brown)
                dot.alpha = 1f
            } else {
                dot.setBackgroundResource(R.drawable.circle_white)
                dot.alpha = 0.5f
            }
        }
    }

    private fun startAutoSlide() {
        val runnable = object : Runnable {
            override fun run() {
                if (imageList.isNotEmpty()) {
                    val nextPage = (currentPage + 1) % imageList.size
                    imageCarousel.setCurrentItem(nextPage, true)
                    currentPage = nextPage
                    handler.postDelayed(this, 3000)
                }
            }
        }
        handler.postDelayed(runnable, 3000)
    }

    private fun setupClickListeners() {
        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (validateInput(email, password)) {
                performLogin(email, password)
            }
        }

        createAccountButton.setOnClickListener {
            startActivity(Intent(this, RegisterWizardActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        forgotPassword.setOnClickListener {
            val email = emailInput.text.toString().trim()
            if (email.isEmpty()) {
                Toast.makeText(this, "Please enter your email first", Toast.LENGTH_SHORT).show()
            } else {
                sendPasswordResetEmail(email)
            }
        }

        googleLoginContainer.setOnClickListener {
            signInWithGoogle()
        }

        emailLoginContainer.setOnClickListener {
            showEmailForm()
        }

        btnBackFromEmail.setOnClickListener {
            hideEmailForm()
        }
    }

    private fun signInWithGoogle() {
        // Sign out first to clear cached account, then show account chooser
        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!, account)
            } catch (e: ApiException) {
                Toast.makeText(this, "Google sign in failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String, account: GoogleSignInAccount) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)

        lifecycleScope.launch {
            try {
                val authResult = FirebaseAuthHelper.signInWithCredential(credential)

                if (authResult.isFailure) {
                    Toast.makeText(this@LoginActivity, "Authentication failed", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val firebaseUser = authResult.getOrNull()!!

                val backendResult = authRepository.firebaseLogin(
                    firebaseUid = firebaseUser.uid,
                    email = account.email ?: "",
                    username = account.displayName?.lowercase()?.replace(" ", "_") ?: "",
                    fullName = account.displayName ?: ""
                )

                if (backendResult.isSuccess) {
                    val apiUser = backendResult.getOrNull()!!

                    if (apiUser.fullName.isNotEmpty() &&
                        apiUser.username.isNotEmpty() &&
                        !apiUser.username.contains("temp") &&
                        apiUser.phone?.isNotEmpty() == true) {
                        sessionManager.saveUserSession(apiUser)
                        navigateToHome()
                    } else {
                        goToRegistrationWizardWithGoogleData(account)
                    }
                } else {
                    goToRegistrationWizardWithGoogleData(account)
                }

            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun goToRegistrationWizardWithGoogleData(account: GoogleSignInAccount) {
        val intent = Intent(this, RegisterWizardActivity::class.java)
        intent.putExtra("google_sign_in", true)
        intent.putExtra("google_email", account.email ?: "")
        intent.putExtra("google_name", account.displayName ?: "")
        intent.putExtra("google_photo_url", account.photoUrl?.toString() ?: "")
        startActivity(intent)
        finish()
    }

    private fun showEmailForm() {
        mainContainer.visibility = View.GONE
        emailFormContainer.visibility = View.VISIBLE
    }

    private fun hideEmailForm() {
        mainContainer.visibility = View.VISIBLE
        emailFormContainer.visibility = View.GONE
    }

    private fun setupEyeIcon() {
        var isPasswordVisible = false

        eyeIcon.setOnClickListener {
            val currentText = passwordInput.text.toString()
            val cursorPosition = passwordInput.selectionStart

            if (isPasswordVisible) {
                passwordInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                eyeIcon.setImageResource(R.drawable.ic_eye_closed)
            } else {
                passwordInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                eyeIcon.setImageResource(R.drawable.ic_eye_open)
            }

            isPasswordVisible = !isPasswordVisible
            passwordInput.setText(currentText)
            passwordInput.setSelection(cursorPosition.coerceAtMost(currentText.length))
        }
    }

    private fun setupValidation() {
        emailInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                emailInput.background = ContextCompat.getDrawable(this@LoginActivity, R.drawable.input_oval)
                emailError.visibility = View.GONE
            }
        })

        passwordInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                passwordInput.background = ContextCompat.getDrawable(this@LoginActivity, R.drawable.input_oval)
                passwordError.visibility = View.GONE
            }
        })
    }

    private fun validateInput(email: String, password: String): Boolean {
        var isValid = true

        if (email.isEmpty()) {
            emailInput.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
            emailError.text = "Email is required"
            emailError.visibility = View.VISIBLE
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
            emailError.text = "Invalid email address"
            emailError.visibility = View.VISIBLE
            isValid = false
        }

        if (password.isEmpty()) {
            passwordInput.background = ContextCompat.getDrawable(this, R.drawable.input_oval_error)
            passwordError.text = "Password is required"
            passwordError.visibility = View.VISIBLE
            isValid = false
        }

        return isValid
    }

    private fun performLogin(email: String, password: String) {
        loginButton.isEnabled = false
        loginButton.text = "Logging in..."

        lifecycleScope.launch {
            try {
                val firebaseResult = FirebaseAuthHelper.loginWithEmail(email, password)

                if (firebaseResult.isFailure) {
                    val error = firebaseResult.exceptionOrNull()?.message ?: "Login failed"
                    showError(getFirebaseAuthErrorMessage(error))
                    resetLoginButton()
                    return@launch
                }

                val firebaseUser = firebaseResult.getOrNull()!!

                val backendResult = authRepository.firebaseLogin(
                    firebaseUid = firebaseUser.uid,
                    email = firebaseUser.email ?: email
                )

                val apiUser = if (backendResult.isSuccess) {
                    backendResult.getOrNull()!!
                } else {
                    com.example.pawsociety.api.ApiUser(
                        firebaseUid = firebaseUser.uid,
                        email = firebaseUser.email ?: email,
                        username = email.split("@").first(),
                        fullName = email.split("@").first()
                    )
                }

                sessionManager.saveUserSession(apiUser)

                SocketManager.connect()
                SocketManager.joinUserRoom(apiUser.firebaseUid)
                FCMTokenManager.initialize(apiUser.firebaseUid)

                Toast.makeText(this@LoginActivity, "Welcome back, ${apiUser.username}!", Toast.LENGTH_SHORT).show()
                navigateToHome()

            } catch (e: Exception) {
                showError(e.message ?: "An unexpected error occurred")
            } finally {
                resetLoginButton()
            }
        }
    }

    private fun resetLoginButton() {
        loginButton.isEnabled = true
        loginButton.text = "Log In"
    }

    private fun sendPasswordResetEmail(email: String) {
        lifecycleScope.launch {
            val result = FirebaseAuthHelper.sendPasswordResetEmail(email)

            if (result.isSuccess) {
                Toast.makeText(
                    this@LoginActivity,
                    "Password reset link sent to $email",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                val error = result.exceptionOrNull()?.message ?: "Failed to send reset email"
                Toast.makeText(this@LoginActivity, error, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getFirebaseAuthErrorMessage(firebaseError: String): String {
        return when {
            firebaseError.contains("ERROR_USER_NOT_FOUND") -> "No account found with this email"
            firebaseError.contains("ERROR_WRONG_PASSWORD") -> "Incorrect password"
            firebaseError.contains("ERROR_INVALID_EMAIL") -> "Invalid email address"
            firebaseError.contains("ERROR_USER_DISABLED") -> "This account has been disabled"
            firebaseError.contains("ERROR_TOO_MANY_REQUESTS") -> "Too many failed attempts. Please try again later."
            else -> firebaseError
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}