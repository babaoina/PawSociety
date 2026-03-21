package com.example.pawsociety.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.pawsociety.R
import com.example.pawsociety.RegisterWizardActivity
import com.example.pawsociety.viewmodels.RegistrationViewModel

class Step2WelcomeFragment : Fragment() {

    private lateinit var viewModel: RegistrationViewModel

    companion object {
        fun newInstance(viewModel: RegistrationViewModel): Step2WelcomeFragment {
            val fragment = Step2WelcomeFragment()
            fragment.viewModel = viewModel
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_step2_welcome, container, false)

        val btnContinue = view.findViewById<Button>(R.id.btn_continue)

        // Always enabled - full brown
        btnContinue.isEnabled = true
        btnContinue.alpha = 1.0f
        btnContinue.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        btnContinue.setBackgroundResource(R.drawable.button_rounded_brown)

        btnContinue.setOnClickListener {
            (activity as? RegisterWizardActivity)?.goToNextStep()
        }

        return view
    }
}