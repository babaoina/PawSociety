package com.example.pawsociety.utils

import android.view.View
import androidx.viewpager2.widget.ViewPager2

class FadePageTransformer : ViewPager2.PageTransformer {

    override fun transformPage(page: View, position: Float) {
        page.apply {
            val absPosition = Math.abs(position)

            when {
                absPosition >= 1 -> {
                    // Page is far off screen
                    alpha = 0f
                }
                absPosition == 0f -> {
                    // Page is current
                    alpha = 1f
                }
                else -> {
                    // Page is between -1 and 1 (being scrolled)
                    alpha = 1f - absPosition
                }
            }
        }
    }
}