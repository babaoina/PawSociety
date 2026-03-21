package com.example.pawsociety.fragments

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.pawsociety.R
import com.example.pawsociety.RegisterWizardActivity
import com.example.pawsociety.viewmodels.RegistrationViewModel

class Step1EmailFragment : Fragment() {

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var eyeIconPassword: ImageView
    private lateinit var eyeIconConfirm: ImageView
    private lateinit var btnContinue: Button
    private lateinit var tvError: TextView
    private lateinit var tvPasswordError: TextView
    private lateinit var tvConfirmError: TextView
    private lateinit var tvPasswordHint: TextView

    private lateinit var viewModel: RegistrationViewModel
    private var verificationDialog: AlertDialog? = null
    private val handler = Handler(Looper.getMainLooper())
    private var checkRunnable: Runnable? = null

    companion object {
        fun newInstance(viewModel: RegistrationViewModel): Step1EmailFragment {
            val fragment = Step1EmailFragment()
            fragment.viewModel = viewModel
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_step1_email, container, false)

        etEmail = view.findViewById(R.id.et_email)
        etPassword = view.findViewById(R.id.et_password)
        etConfirmPassword = view.findViewById(R.id.et_confirm_password)
        eyeIconPassword = view.findViewById(R.id.eye_icon_password)
        eyeIconConfirm = view.findViewById(R.id.eye_icon_confirm)
        btnContinue = view.findViewById(R.id.btn_continue)
        tvError = view.findViewById(R.id.tv_error)
        tvPasswordError = view.findViewById(R.id.tv_password_error)
        tvConfirmError = view.findViewById(R.id.tv_confirm_error)
        tvPasswordHint = view.findViewById(R.id.tv_password_hint)

        // Initially disable button - FADED
        btnContinue.isEnabled = false
        btnContinue.alpha = 0.4f
        btnContinue.setTextColor(ContextCompat.getColor(requireContext(), R.color.white_50))
        btnContinue.setBackgroundResource(R.drawable.button_rounded_gray)

        setupEyeIcons()
        setupListeners()
        return view
    }

    private fun setupEyeIcons() {
        var isPasswordVisible = false
        var isConfirmVisible = false

        eyeIconPassword.setOnClickListener {
            val currentText = etPassword.text.toString()
            val cursorPosition = etPassword.selectionStart

            if (isPasswordVisible) {
                etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                eyeIconPassword.setImageResource(R.drawable.ic_eye_closed)
            } else {
                etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                eyeIconPassword.setImageResource(R.drawable.ic_eye_open)
            }

            isPasswordVisible = !isPasswordVisible
            etPassword.setText(currentText)
            etPassword.setSelection(cursorPosition.coerceAtMost(currentText.length))
        }

        eyeIconConfirm.setOnClickListener {
            val currentText = etConfirmPassword.text.toString()
            val cursorPosition = etConfirmPassword.selectionStart

            if (isConfirmVisible) {
                etConfirmPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                eyeIconConfirm.setImageResource(R.drawable.ic_eye_closed)
            } else {
                etConfirmPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                eyeIconConfirm.setImageResource(R.drawable.ic_eye_open)
            }

            isConfirmVisible = !isConfirmVisible
            etConfirmPassword.setText(currentText)
            etConfirmPassword.setSelection(cursorPosition.coerceAtMost(currentText.length))
        }
    }

    private fun setupListeners() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tvError.visibility = View.GONE
                tvPasswordError.visibility = View.GONE
                tvConfirmError.visibility = View.GONE
            }

            override fun afterTextChanged(s: Editable?) {
                validateInputsRealTime()
            }
        }

        etEmail.addTextChangedListener(textWatcher)
        etPassword.addTextChangedListener(textWatcher)
        etConfirmPassword.addTextChangedListener(textWatcher)

        btnContinue.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()
            val confirmPassword = etConfirmPassword.text.toString()

            if (validateInputs(email, password, confirmPassword)) {
                viewModel.setEmail(email)
                viewModel.setPassword(password)

                viewModel.register(
                    onSuccess = {
                        showVerificationDialog()
                    },
                    onError = { errorMsg ->
                        Toast.makeText(requireContext(), errorMsg, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    private fun validateInputsRealTime() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()

        val isEmailValid = email.isNotEmpty() &&
                android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()

        val isPasswordValid = password.length >= 6

        val isConfirmValid = password.isNotEmpty() &&
                confirmPassword.isNotEmpty() &&
                password == confirmPassword

        val allValid = isEmailValid && isPasswordValid && isConfirmValid

        if (allValid) {
            btnContinue.isEnabled = true
            btnContinue.alpha = 1.0f
            btnContinue.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            btnContinue.setBackgroundResource(R.drawable.button_rounded_brown)
        } else {
            btnContinue.isEnabled = false
            btnContinue.alpha = 0.4f
            btnContinue.setTextColor(ContextCompat.getColor(requireContext(), R.color.white_50))
            btnContinue.setBackgroundResource(R.drawable.button_rounded_gray)
        }
    }

    private fun validateInputs(email: String, password: String, confirmPassword: String): Boolean {
        var isValid = true

        if (email.isEmpty()) {
            tvError.text = "Email is required"
            tvError.visibility = View.VISIBLE
            isValid = false
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            tvError.text = "Please enter a valid email"
            tvError.visibility = View.VISIBLE
            isValid = false
        }

        if (password.isEmpty()) {
            tvPasswordError.text = "Password is required"
            tvPasswordError.visibility = View.VISIBLE
            isValid = false
        } else if (password.length < 6) {
            tvPasswordError.text = "Password must be at least 6 characters"
            tvPasswordError.visibility = View.VISIBLE
            isValid = false
        }

        if (confirmPassword.isEmpty()) {
            tvConfirmError.text = "Please confirm your password"
            tvConfirmError.visibility = View.VISIBLE
            isValid = false
        } else if (password != confirmPassword) {
            tvConfirmError.text = "Passwords do not match"
            tvConfirmError.visibility = View.VISIBLE
            isValid = false
        }

        return isValid
    }

    private fun showVerificationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_verify_email, null)
        val tvEmail = dialogView.findViewById<TextView>(R.id.tv_email)
        val btnResend = dialogView.findViewById<Button>(R.id.btn_resend)
        val tvStatus = dialogView.findViewById<TextView>(R.id.tv_status)

        tvEmail.text = "Verification sent to:\n${viewModel.email.value}"

        verificationDialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()

        verificationDialog?.show()

        checkRunnable = object : Runnable {
            override fun run() {
                viewModel.checkEmailVerification(
                    onVerified = {
                        tvStatus.text = "✓ Email verified! Proceeding..."
                        handler.removeCallbacks(checkRunnable!!)
                        verificationDialog?.dismiss()
                        (activity as? RegisterWizardActivity)?.goToNextStep()
                    },
                    onNotVerified = {
                        tvStatus.text = "Waiting for verification... Check your email and click the link."
                    }
                )
                handler.postDelayed(this, 2000)
            }
        }
        handler.post(checkRunnable!!)

        btnResend.setOnClickListener {
            btnResend.isEnabled = false
            tvStatus.text = "Sending..."

            viewModel.resendVerificationEmail(
                onSuccess = {
                    tvStatus.text = "✓ Verification email resent!"
                    btnResend.isEnabled = true
                },
                onError = { error ->
                    tvStatus.text = "✗ Error: $error"
                    btnResend.isEnabled = true
                }
            )
        }

        verificationDialog?.setOnDismissListener {
            handler.removeCallbacks(checkRunnable!!)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // ✅ FIXED: Safe cleanup with null checks
        handler.removeCallbacksAndMessages(null)
        checkRunnable?.let { handler.removeCallbacks(it) }
        checkRunnable = null
        verificationDialog?.dismiss()
        verificationDialog = null
    }
}