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

class Step4MobileFragment : Fragment() {

    private lateinit var etMobile: EditText
    private lateinit var btnContinue: Button
    private lateinit var tvError: TextView
    private lateinit var viewModel: RegistrationViewModel

    companion object {
        fun newInstance(viewModel: RegistrationViewModel): Step4MobileFragment {
            val fragment = Step4MobileFragment()
            fragment.viewModel = viewModel
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_step4_mobile, container, false)

        etMobile = view.findViewById(R.id.et_mobile)
        btnContinue = view.findViewById(R.id.btn_continue)
        tvError = view.findViewById(R.id.tv_error)

        // Initially disable button - FADED
        btnContinue.isEnabled = false
        btnContinue.alpha = 0.4f
        btnContinue.setTextColor(ContextCompat.getColor(requireContext(), R.color.white_50))
        btnContinue.setBackgroundResource(R.drawable.button_rounded_gray)

        setupListeners()
        return view
    }

    private fun setupListeners() {
        etMobile.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                tvError.visibility = View.GONE
            }
            override fun afterTextChanged(s: Editable?) {
                val input = s.toString()
                val digits = input.filter { it.isDigit() }
                if (digits != input) {
                    etMobile.removeTextChangedListener(this)
                    etMobile.setText(digits)
                    etMobile.setSelection(digits.length)
                    etMobile.addTextChangedListener(this)
                }
                validateInputsRealTime()
            }
        })

        btnContinue.setOnClickListener {
            val mobile = etMobile.text.toString().trim()

            if (validateMobile(mobile)) {
                viewModel.setMobile(mobile)
                (activity as? RegisterWizardActivity)?.goToNextStep()
            }
        }
    }

    private fun validateInputsRealTime() {
        val mobile = etMobile.text.toString().trim()
        val isValid = mobile.length == 11 && mobile.startsWith("09")

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

    private fun validateMobile(mobile: String): Boolean {
        return when {
            mobile.isEmpty() -> {
                tvError.text = "Mobile number is required"
                tvError.visibility = View.VISIBLE
                false
            }
            mobile.length != 11 -> {
                tvError.text = "Mobile number must be exactly 11 digits"
                tvError.visibility = View.VISIBLE
                false
            }
            !mobile.startsWith("09") -> {
                tvError.text = "Mobile number must start with 09"
                tvError.visibility = View.VISIBLE
                false
            }
            else -> true
        }
    }
}