package com.example.pawsociety.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.pawsociety.fragments.*
import com.example.pawsociety.viewmodels.RegistrationViewModel

class RegistrationPagerAdapter(
    fa: FragmentActivity,
    private val viewModel: RegistrationViewModel
) : FragmentStateAdapter(fa) {

    override fun getItemCount(): Int = 6  // 6 steps total

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> Step1EmailFragment.newInstance(viewModel)
            1 -> Step2WelcomeFragment.newInstance(viewModel)
            2 -> Step3NameFragment.newInstance(viewModel)
            3 -> Step4UsernameFragment.newInstance(viewModel)  // NEW
            4 -> Step4MobileFragment.newInstance(viewModel)    // Now Step5
            5 -> Step5PhotoFragment.newInstance(viewModel)     // Now Step6
            else -> Step1EmailFragment.newInstance(viewModel)
        }
    }
}