package com.example.pawsociety.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.pawsociety.R
import com.example.pawsociety.RegisterWizardActivity
import com.example.pawsociety.viewmodels.RegistrationViewModel

class Step4UsernameFragment : Fragment() {

    private lateinit var etUsername: EditText
    private lateinit var btnContinue: Button
    private lateinit var tvError: TextView
    private lateinit var tvHint: TextView
    private lateinit var tvCharCount: TextView
    private lateinit var viewModel: RegistrationViewModel

    companion object {
        fun newInstance(viewModel: RegistrationViewModel): Step4UsernameFragment {
            val fragment = Step4UsernameFragment()
            fragment.viewModel = viewModel
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_step4_username, container, false)

        etUsername = view.findViewById(R.id.et_username)
        btnContinue = view.findViewById(R.id.btn_continue)
        tvError = view.findViewById(R.id.tv_error)
        tvHint = view.findViewById(R.id.tv_hint)
        tvCharCount = view.findViewById(R.id.tv_char_count)

        val email = viewModel.email.value ?: ""
        val suggestedUsername = email.substringBefore("@")
        etUsername.hint = "e.g., ${suggestedUsername}123"
        etUsername.setText("")

        // Initially disable button - FADED
        btnContinue.isEnabled = false
        btnContinue.alpha = 0.4f
        btnContinue.setTextColor(ContextCompat.getColor(requireContext(), R.color.white_50))
        btnContinue.setBackgroundResource(R.drawable.button_rounded_gray)

        setupListeners()
        return view
    }

    private fun setupListeners() {
        etUsername.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tvError.visibility = View.GONE
            }

            override fun afterTextChanged(s: Editable?) {
                val input = s?.toString() ?: ""

                if (input.length > 20) {
                    val trimmed = input.substring(0, 20)
                    etUsername.removeTextChangedListener(this)
                    etUsername.setText(trimmed)
                    etUsername.setSelection(trimmed.length)
                    etUsername.addTextChangedListener(this)
                    return
                }

                val specialCount = input.count { !it.isLetterOrDigit() }
                if (specialCount > 3 && input.isNotEmpty()) {
                    if (!input.last().isLetterOrDigit()) {
                        val trimmed = input.dropLast(1)
                        etUsername.removeTextChangedListener(this)
                        etUsername.setText(trimmed)
                        etUsername.setSelection(trimmed.length)
                        etUsername.addTextChangedListener(this)
                    }
                }

                tvCharCount.text = "${etUsername.text.length}/20"

                when {
                    etUsername.text.length > 20 ->
                        tvCharCount.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                    etUsername.text.length >= 3 ->
                        tvCharCount.setTextColor(resources.getColor(android.R.color.holo_green_dark))
                    else ->
                        tvCharCount.setTextColor(resources.getColor(android.R.color.darker_gray))
                }

                validateInputsRealTime()
            }
        })

        btnContinue.setOnClickListener {
            val username = etUsername.text.toString().trim()

            if (validateUsername(username)) {
                viewModel.setUsername(username)
                (activity as? RegisterWizardActivity)?.goToNextStep()
            }
        }
    }

    private fun validateInputsRealTime() {
        val username = etUsername.text.toString().trim()
        val isValid = username.length in 3..20

        if (isValid) {
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

    private fun validateUsername(username: String): Boolean {
        val specialCharCount = username.count { !it.isLetterOrDigit() }

        return when {
            username.isEmpty() -> {
                tvError.text = "Username is required"
                tvError.visibility = View.VISIBLE
                false
            }
            username.length < 3 -> {
                tvError.text = "Username must be at least 3 characters"
                tvError.visibility = View.VISIBLE
                false
            }
            username.length > 20 -> {
                tvError.text = "Username must be less than 20 characters"
                tvError.visibility = View.VISIBLE
                false
            }
            specialCharCount < 1 -> {
                tvError.text = "Username must contain at least 1 special character (!@#$%^&*)"
                tvError.visibility = View.VISIBLE
                false
            }
            specialCharCount > 3 -> {
                tvError.text = "Maximum 3 special characters only"
                tvError.visibility = View.VISIBLE
                false
            }
            username.isNotEmpty() && !username[0].isLetterOrDigit() -> {
                tvError.text = "Username cannot start with special character"
                tvError.visibility = View.VISIBLE
                false
            }
            username.contains(Regex("[^a-zA-Z0-9]{2,}")) -> {
                tvError.text = "No consecutive special characters allowed"
                tvError.visibility = View.VISIBLE
                false
            }
            else -> true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}