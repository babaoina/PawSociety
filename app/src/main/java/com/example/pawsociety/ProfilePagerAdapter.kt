package com.example.pawsociety

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ProfilePagerAdapter(fa: FragmentActivity, private val userId: String) : FragmentStateAdapter(fa) {

    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> ProfileTabFragment.newInstance("New", userId)
            1 -> ProfileTabFragment.newInstance("My Pets", userId)
            2 -> ProfileTabFragment.newInstance("Favorites", userId)
            3 -> ProfileTabFragment.newInstance("Rescues", userId)
            else -> ProfileTabFragment.newInstance("", userId)
        }
    }
}