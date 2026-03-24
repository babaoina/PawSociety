package com.example.pawsociety

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Observer
import androidx.viewpager2.widget.ViewPager2
import com.example.pawsociety.adapters.RegistrationPagerAdapter
import com.example.pawsociety.util.SessionManager
import com.example.pawsociety.viewmodels.RegistrationViewModel

class RegisterWizardActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var rootView: LinearLayout
    private lateinit var headerContainer: View
    private lateinit var indicatorContainer: LinearLayout
    private lateinit var btnBack: ImageView
    private lateinit var tvStepTitle: TextView
    private lateinit var progressBar: ProgressBar

    private val registrationViewModel: RegistrationViewModel by viewModels()
    private lateinit var sessionManager: SessionManager

    private val dots = mutableListOf<View>()
    private var currentStep = 0
    private val totalSteps = 6

    // Google Sign In data
    var isGoogleSignIn = false
    var googleEmail: String? = null
    var googleName: String? = null
    var googlePhotoUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_wizard)

        // Get Google Sign In data
        isGoogleSignIn = intent.getBooleanExtra("google_sign_in", false)
        googleEmail = intent.getStringExtra("google_email")
        googleName = intent.getStringExtra("google_name")
        googlePhotoUrl = intent.getStringExtra("google_photo_url")

        sessionManager = SessionManager(this)
        registrationViewModel.setSessionManager(sessionManager)

        // If Google Sign In, set email and name
        if (isGoogleSignIn && googleEmail != null) {
            registrationViewModel.setEmail(googleEmail!!)
            // Parse name: "Juan Dela Cruz" -> first and last
            val nameParts = googleName?.split(" ") ?: emptyList()
            if (nameParts.isNotEmpty()) {
                registrationViewModel.setFirstName(nameParts[0])
                if (nameParts.size > 1) {
                    registrationViewModel.setLastName(nameParts.drop(1).joinToString(" "))
                }
            }
            if (!googlePhotoUrl.isNullOrEmpty()) {
                registrationViewModel.setProfileImageUri(googlePhotoUrl)
            }
        }

        initializeViews()
        setupViewPager()
        setupDots()
        setupClickListeners()
        setupObservers()

        // For Google Sign In, skip Step 1 (email/password)
        if (isGoogleSignIn) {
            viewPager.currentItem = 1  // Start at Welcome screen
            currentStep = 1
            updateStepIndicator(1)
            updateStepTitle(1)
            updateBackButtonVisibility(1)
        }
    }

    private fun initializeViews() {
        rootView = findViewById(R.id.register_wizard_root)
        headerContainer = findViewById(R.id.register_header)
        viewPager = findViewById(R.id.view_pager)
        indicatorContainer = findViewById(R.id.indicator_container)
        btnBack = findViewById(R.id.btn_back)
        tvStepTitle = findViewById(R.id.tv_step_title)
        progressBar = findViewById(R.id.progress_bar)
        applyTopInsets()
    }

    private fun applyTopInsets() {
        val baseHeaderHeight = 56.dp
        val baseIndicatorMarginTop = 6.dp

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            val headerTopPadding = (statusBarInset + 6.dp).coerceAtLeast(0)

            headerContainer.layoutParams = headerContainer.layoutParams.apply {
                height = baseHeaderHeight + statusBarInset + 8.dp
            }
            headerContainer.setPadding(
                headerContainer.paddingLeft,
                headerTopPadding,
                headerContainer.paddingRight,
                headerContainer.paddingBottom
            )

            (indicatorContainer.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                params.topMargin = baseIndicatorMarginTop
                indicatorContainer.layoutParams = params
            }

            insets
        }
    }

    private fun setupViewPager() {
        val adapter = RegistrationPagerAdapter(this, registrationViewModel)
        viewPager.adapter = adapter
        viewPager.isUserInputEnabled = false

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentStep = position
                updateStepIndicator(position)
                updateStepTitle(position)
                updateBackButtonVisibility(position)
            }
        })
    }

    private fun setupDots() {
        indicatorContainer.removeAllViews()
        dots.clear()

        for (i in 0 until totalSteps) {
            val dot = View(this)
            val params = LinearLayout.LayoutParams(8.dp, 8.dp)
            params.setMargins(6.dp, 0, 6.dp, 0)
            dot.layoutParams = params

            if (i == 0) {
                dot.setBackgroundResource(R.drawable.circle_filled_brown)
            } else {
                dot.setBackgroundResource(R.drawable.circle_white)
                dot.background.setTint(ContextCompat.getColor(this, R.color.light_gray))
            }

            indicatorContainer.addView(dot)
            dots.add(dot)
        }
    }

    private fun updateStepIndicator(position: Int) {
        dots.forEachIndexed { index, dot ->
            if (index == position) {
                dot.setBackgroundResource(R.drawable.circle_filled_brown)
                dot.alpha = 1f
            } else if (index < position) {
                dot.setBackgroundResource(R.drawable.circle_filled_brown)
                dot.alpha = 0.5f
            } else {
                dot.setBackgroundResource(R.drawable.circle_white)
                dot.background.setTint(ContextCompat.getColor(this, R.color.light_gray))
                dot.alpha = 1f
            }
        }
    }

    private fun updateStepTitle(position: Int) {
        tvStepTitle.text = when (position) {
            0 -> "Create account"
            1 -> "Welcome!"
            2 -> "Your name"
            3 -> "Choose username"
            4 -> "Mobile number"
            5 -> "Profile photo"
            else -> ""
        }
    }

    private fun updateBackButtonVisibility(position: Int) {
        btnBack.visibility = when (position) {
            0 -> View.INVISIBLE
            1 -> View.INVISIBLE
            2 -> View.INVISIBLE
            3 -> View.VISIBLE
            4 -> View.VISIBLE
            5 -> View.VISIBLE
            else -> View.INVISIBLE
        }
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            if (currentStep > 0 && currentStep != 2) {
                viewPager.currentItem = currentStep - 1
            }
        }
    }

    private fun setupObservers() {
        registrationViewModel.isLoading.observe(this, Observer { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        })

        registrationViewModel.error.observe(this, Observer { errorMsg ->
            errorMsg?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                registrationViewModel.clearError()
            }
        })
    }

    fun goToNextStep() {
        if (currentStep < totalSteps - 1) {
            viewPager.currentItem = currentStep + 1
        }
    }

    fun finishRegistration() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
