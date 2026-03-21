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
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.pawsociety.R
import com.example.pawsociety.RegisterWizardActivity
import com.example.pawsociety.viewmodels.RegistrationViewModel

class Step3NameFragment : Fragment() {

    private lateinit var etLastName: EditText
    private lateinit var etFirstName: EditText
    private lateinit var etMiddleInitial: EditText
    private lateinit var btnContinue: Button
    private lateinit var tvLastNameError: TextView
    private lateinit var tvFirstNameError: TextView
    private lateinit var tvMiddleError: TextView
    private lateinit var viewModel: RegistrationViewModel

    companion object {
        fun newInstance(viewModel: RegistrationViewModel): Step3NameFragment {
            val fragment = Step3NameFragment()
            fragment.viewModel = viewModel
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_step3_name, container, false)

        etLastName = view.findViewById(R.id.et_last_name)
        etFirstName = view.findViewById(R.id.et_first_name)
        etMiddleInitial = view.findViewById(R.id.et_middle_initial)
        btnContinue = view.findViewById(R.id.btn_continue)
        tvLastNameError = view.findViewById(R.id.tv_last_name_error)
        tvFirstNameError = view.findViewById(R.id.tv_first_name_error)
        tvMiddleError = view.findViewById(R.id.tv_middle_error)

        // Initially disable button
        btnContinue.isEnabled = false
        btnContinue.alpha = 0.4f
        btnContinue.setTextColor(ContextCompat.getColor(requireContext(), R.color.white_50))
        btnContinue.setBackgroundResource(R.drawable.button_rounded_gray)

        // If Google Sign In, pre-fill name fields
        try {
            val activity = activity as? RegisterWizardActivity
            if (activity?.isGoogleSignIn == true) {
                val googleName = activity.googleName ?: ""
                if (googleName.isNotEmpty()) {
                    val nameParts = googleName.split(" ")
                    if (nameParts.isNotEmpty()) {
                        // First name is the first part
                        etFirstName.setText(nameParts[0])
                        // Last name is the rest
                        if (nameParts.size > 1) {
                            val lastName = nameParts.drop(1).joinToString(" ")
                            etLastName.setText(lastName)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setupListeners()
        return view
    }

    private fun setupListeners() {
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Clear errors when typing
                tvLastNameError.visibility = View.GONE
                tvFirstNameError.visibility = View.GONE
                tvMiddleError.visibility = View.GONE
            }

            override fun afterTextChanged(s: Editable?) {
                validateInputsRealTime()
            }
        }

        etLastName.addTextChangedListener(textWatcher)
        etFirstName.addTextChangedListener(textWatcher)
        etMiddleInitial.addTextChangedListener(textWatcher)

        btnContinue.setOnClickListener {
            try {
                val lastName = etLastName.text.toString().trim()
                val firstName = etFirstName.text.toString().trim()
                val middleInitial = etMiddleInitial.text.toString().trim()

                if (validateInputs(lastName, firstName, middleInitial)) {
                    // Save to ViewModel
                    viewModel.setLastName(lastName)
                    viewModel.setFirstName(firstName)
                    viewModel.setMiddleInitial(middleInitial)

                    // Go to next step
                    (activity as? RegisterWizardActivity)?.goToNextStep()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun validateInputsRealTime() {
        try {
            val lastName = etLastName.text.toString().trim()
            val firstName = etFirstName.text.toString().trim()

            val allValid = lastName.isNotEmpty() && firstName.isNotEmpty()

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
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun validateInputs(lastName: String, firstName: String, middleInitial: String): Boolean {
        var isValid = true

        // Validate last name
        if (lastName.isEmpty()) {
            tvLastNameError.text = "Last name is required"
            tvLastNameError.visibility = View.VISIBLE
            isValid = false
        } else if (lastName.length < 2) {
            tvLastNameError.text = "Last name must be at least 2 characters"
            tvLastNameError.visibility = View.VISIBLE
            isValid = false
        } else if (!lastName.matches(Regex("^[a-zA-Z\\s.-]+$"))) {
            tvLastNameError.text = "Only letters, spaces, dots, and hyphens allowed"
            tvLastNameError.visibility = View.VISIBLE
            isValid = false
        }

        // Validate first name
        if (firstName.isEmpty()) {
            tvFirstNameError.text = "First name is required"
            tvFirstNameError.visibility = View.VISIBLE
            isValid = false
        } else if (firstName.length < 2) {
            tvFirstNameError.text = "First name must be at least 2 characters"
            tvFirstNameError.visibility = View.VISIBLE
            isValid = false
        } else if (!firstName.matches(Regex("^[a-zA-Z\\s.-]+$"))) {
            tvFirstNameError.text = "Only letters, spaces, dots, and hyphens allowed"
            tvFirstNameError.visibility = View.VISIBLE
            isValid = false
        }

        // Validate middle initial (optional - only validate if provided)
        if (middleInitial.isNotEmpty()) {
            if (middleInitial.length != 1) {
                tvMiddleError.text = "Middle initial must be 1 letter"
                tvMiddleError.visibility = View.VISIBLE
                isValid = false
            } else if (!middleInitial[0].isLetter()) {
                tvMiddleError.text = "Must be a letter"
                tvMiddleError.visibility = View.VISIBLE
                isValid = false
            }
        }

        return isValid
    }
}