package com.example.pawsociety

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.example.pawsociety.util.InboxBadgeManager
import com.example.pawsociety.util.SessionManager
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.example.pawsociety.HomeViewModel
import androidx.lifecycle.ViewModelProvider

abstract class BaseNavigationActivity : AppCompatActivity() {

    protected lateinit var bottomNavigation: View
    protected lateinit var inboxBadge: TextView
    protected lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = SessionManager(this)
    }

    override fun setContentView(layoutResID: Int) {
        super.setContentView(layoutResID)

        try {
            bottomNavigation = findViewById(R.id.bottom_navigation)
            inboxBadge = findViewById(R.id.inbox_badge)

            setupBottomNavigationInsets()
            setupNavigationBar()
            setupInboxBadge()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupBottomNavigationInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(bottomNavigation) { view, insets ->
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navBarInsets.bottom)
            insets
        }
        bottomNavigation.requestApplyInsets()
    }

    private fun setupInboxBadge() {
        val currentUser = sessionManager.getCurrentUser()
        if (currentUser != null) {
            try {
                inboxBadge = findViewById(R.id.inbox_badge)
                InboxBadgeManager.initialize(currentUser.firebaseUid, this)
                InboxBadgeManager.registerBadge(inboxBadge)

                // Load count directly without viewModel
                lifecycleScope.launch {
                    delay(500)
                    val result = com.example.pawsociety.data.repository.ChatRepository().getConversations(currentUser.firebaseUid)
                    if (result.isSuccess) {
                        val response = result.getOrNull()!!
                        val totalUnread = (response.messages?.sumOf { it.unreadCount } ?: 0) +
                                (response.requests?.size ?: 0)
                        InboxBadgeManager.updateBadgeManually(totalUnread)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupNavigationBar() {
        try {
            resetAllTabs()

            findViewById<View>(R.id.nav_home)?.setOnClickListener {
                if (this !is HomeActivity) {
                    navigateTo(HomeActivity::class.java)
                }
            }

            findViewById<View>(R.id.nav_inbox)?.setOnClickListener {
                if (this !is InboxActivity) {
                    navigateTo(InboxActivity::class.java)
                } else {
                    // If already in inbox, mark as read when opened
                    updateInboxBadge(0)
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
        val highlightColor = "#B88B4A"

        when (this) {
            is HomeActivity -> {
                findViewById<ImageView>(R.id.nav_home_icon)?.setColorFilter(Color.parseColor(highlightColor))
                findViewById<TextView>(R.id.nav_home_text)?.setTextColor(Color.parseColor(highlightColor))
            }
            is InboxActivity -> {
                findViewById<ImageView>(R.id.nav_inbox_icon)?.setColorFilter(Color.parseColor(highlightColor))
                findViewById<TextView>(R.id.nav_inbox_text)?.setTextColor(Color.parseColor(highlightColor))
                // Clear badge when viewing inbox
                updateInboxBadge(0)
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

        // 🔥 ADD THIS - Global offline observer for all pages
        try {
            val viewModel = androidx.lifecycle.ViewModelProvider(this)[HomeViewModel::class.java]

            viewModel.isOffline.observe(this) { isOffline ->
                val offlineIndicator = findViewById<TextView>(R.id.tv_offline_indicator)
                offlineIndicator?.visibility = if (isOffline) View.VISIBLE else View.GONE

                // Optional: Dim the bottom nav when offline
                bottomNavigation.alpha = if (isOffline) 0.6f else 1.0f
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resetAllTabs() {
        val defaultColor = "#666666"

        findViewById<ImageView>(R.id.nav_home_icon)?.setColorFilter(Color.parseColor(defaultColor))
        findViewById<TextView>(R.id.nav_home_text)?.setTextColor(Color.parseColor(defaultColor))
        findViewById<ImageView>(R.id.nav_inbox_icon)?.setColorFilter(Color.parseColor(defaultColor))
        findViewById<TextView>(R.id.nav_inbox_text)?.setTextColor(Color.parseColor(defaultColor))
        findViewById<ImageView>(R.id.nav_find_icon)?.setColorFilter(Color.parseColor(defaultColor))
        findViewById<TextView>(R.id.nav_find_text)?.setTextColor(Color.parseColor(defaultColor))
        findViewById<ImageView>(R.id.nav_profile_icon)?.setColorFilter(Color.parseColor(defaultColor))
        findViewById<TextView>(R.id.nav_profile_text)?.setTextColor(Color.parseColor(defaultColor))
        findViewById<ImageView>(R.id.nav_paw_icon)?.setColorFilter(null)
    }

    fun updateInboxBadge(count: Int) {
        InboxBadgeManager.updateBadgeManually(count)
    }

    override fun onResume() {
        super.onResume()
        try {
            inboxBadge = findViewById(R.id.inbox_badge)
            InboxBadgeManager.registerBadge(inboxBadge)

            val currentUser = sessionManager.getCurrentUser()
            currentUser?.let { user ->
                lifecycleScope.launch {
                    val result = com.example.pawsociety.data.repository.ChatRepository().getConversations(user.firebaseUid)
                    if (result.isSuccess) {
                        val response = result.getOrNull()!!
                        val totalUnread = (response.messages?.sumOf { it.unreadCount } ?: 0) +
                                (response.requests?.size ?: 0)
                        InboxBadgeManager.updateBadgeManually(totalUnread)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPause() {
        super.onPause()
        // Unregister badge when activity pauses
        if (::inboxBadge.isInitialized) {
            InboxBadgeManager.unregisterBadge(inboxBadge)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up only when the last activity is destroyed
        if (isFinishing && !isChangingConfigurations) {
            InboxBadgeManager.cleanup()
        }
    }
}