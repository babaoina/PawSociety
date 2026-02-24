package com.example.pawsociety

import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

abstract class BaseNavigationActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)

        // Set default colors to gray
        try {
            val bottomNav = findViewById<LinearLayout>(R.id.bottom_navigation)
            bottomNav?.setBackgroundColor(Color.WHITE)

            val navHomeIcon = findViewById<ImageView>(R.id.nav_home_icon)
            val navHomeText = findViewById<TextView>(R.id.nav_home_text)
            val navInboxIcon = findViewById<ImageView>(R.id.nav_inbox_icon)
            val navInboxText = findViewById<TextView>(R.id.nav_inbox_text)
            val navFindIcon = findViewById<ImageView>(R.id.nav_find_icon)
            val navFindText = findViewById<TextView>(R.id.nav_find_text)
            val navProfileIcon = findViewById<ImageView>(R.id.nav_profile_icon)
            val navProfileText = findViewById<TextView>(R.id.nav_profile_text)

            // Set all to gray
            val grayColor = "#666666"
            navHomeIcon?.setColorFilter(Color.parseColor(grayColor))
            navHomeText?.setTextColor(Color.parseColor(grayColor))
            navInboxIcon?.setColorFilter(Color.parseColor(grayColor))
            navInboxText?.setTextColor(Color.parseColor(grayColor))
            navFindIcon?.setColorFilter(Color.parseColor(grayColor))
            navFindText?.setTextColor(Color.parseColor(grayColor))
            navProfileIcon?.setColorFilter(Color.parseColor(grayColor))
            navProfileText?.setTextColor(Color.parseColor(grayColor))

        } catch (e: Exception) {
            e.printStackTrace()
        }

        setupNavigationBar()
    }

    private fun setupNavigationBar() {
        try {
            // Set click listeners for all navigation buttons
            findViewById<View>(R.id.nav_home)?.setOnClickListener {
                if (this !is HomeActivity) {
                    navigateTo(HomeActivity::class.java)
                }
            }

            findViewById<View>(R.id.nav_inbox)?.setOnClickListener {
                if (this !is InboxActivity) {
                    navigateTo(InboxActivity::class.java)
                }
            }

            findViewById<View>(R.id.nav_find)?.setOnClickListener {
                if (this !is FindActivity) {
                    navigateTo(FindActivity::class.java)
                }
            }

            findViewById<View>(R.id.nav_profile)?.setOnClickListener {
                if (this !is ProfileActivity) {
                    navigateTo(ProfileActivity::class.java)
                }
            }

            findViewById<View>(R.id.nav_paw_post)?.setOnClickListener {
                val intent = Intent(this, CreatePostActivity::class.java)
                startActivity(intent)
                overridePendingTransition(0, 0)
            }

            // Highlight the current tab
            highlightCurrentTab()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun navigateTo(activityClass: Class<*>) {
        val intent = Intent(this, activityClass)
        startActivity(intent)
        overridePendingTransition(0, 0)
        finish()
    }

    private fun highlightCurrentTab() {
        resetAllTabs()

        val highlightColor = "#B88B4A"  // Warm caramel brown for highlight

        when (this) {
            is HomeActivity -> {
                findViewById<ImageView>(R.id.nav_home_icon)?.setColorFilter(Color.parseColor(highlightColor))
                findViewById<TextView>(R.id.nav_home_text)?.setTextColor(Color.parseColor(highlightColor))
            }
            is InboxActivity -> {
                findViewById<ImageView>(R.id.nav_inbox_icon)?.setColorFilter(Color.parseColor(highlightColor))
                findViewById<TextView>(R.id.nav_inbox_text)?.setTextColor(Color.parseColor(highlightColor))
            }
            is FindActivity -> {
                findViewById<ImageView>(R.id.nav_find_icon)?.setColorFilter(Color.parseColor(highlightColor))
                findViewById<TextView>(R.id.nav_find_text)?.setTextColor(Color.parseColor(highlightColor))
            }
            is ProfileActivity -> {
                findViewById<ImageView>(R.id.nav_profile_icon)?.setColorFilter(Color.parseColor(highlightColor))
                findViewById<TextView>(R.id.nav_profile_text)?.setTextColor(Color.parseColor(highlightColor))
            }
        }
    }

    private fun resetAllTabs() {
        val defaultColor = "#666666"  // Gray

        findViewById<ImageView>(R.id.nav_home_icon)?.setColorFilter(Color.parseColor(defaultColor))
        findViewById<TextView>(R.id.nav_home_text)?.setTextColor(Color.parseColor(defaultColor))
        findViewById<ImageView>(R.id.nav_inbox_icon)?.setColorFilter(Color.parseColor(defaultColor))
        findViewById<TextView>(R.id.nav_inbox_text)?.setTextColor(Color.parseColor(defaultColor))
        findViewById<ImageView>(R.id.nav_find_icon)?.setColorFilter(Color.parseColor(defaultColor))
        findViewById<TextView>(R.id.nav_find_text)?.setTextColor(Color.parseColor(defaultColor))
        findViewById<ImageView>(R.id.nav_profile_icon)?.setColorFilter(Color.parseColor(defaultColor))
        findViewById<TextView>(R.id.nav_profile_text)?.setTextColor(Color.parseColor(defaultColor))
    }
}