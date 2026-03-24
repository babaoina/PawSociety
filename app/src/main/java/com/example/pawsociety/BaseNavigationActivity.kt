package com.example.pawsociety

import android.content.Intent
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
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
import com.example.pawsociety.util.FirebaseAuthHelper
import com.example.pawsociety.util.InboxBadgeManager
import com.example.pawsociety.util.SessionManager
import com.example.pawsociety.util.SocketManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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
                animateNavTabPress(
                    container = findViewById(R.id.nav_home),
                    icon = findViewById(R.id.nav_home_icon),
                    label = findViewById(R.id.nav_home_text)
                )
                if (this !is HomeActivity) {
                    findViewById<View>(R.id.nav_home)?.postDelayed({
                        navigateTo(HomeActivity::class.java)
                    }, 110)
                }
            }

            findViewById<View>(R.id.nav_inbox)?.setOnClickListener {
                animateNavTabPress(
                    container = findViewById(R.id.nav_inbox),
                    icon = findViewById(R.id.nav_inbox_icon),
                    label = findViewById(R.id.nav_inbox_text)
                )
                if (this !is InboxActivity) {
                    findViewById<View>(R.id.nav_inbox)?.postDelayed({
                        navigateTo(InboxActivity::class.java)
                    }, 110)
                } else {
                    // If already in inbox, mark as read when opened
                    findViewById<View>(R.id.nav_inbox)?.postDelayed({
                        updateInboxBadge(0)
                    }, 110)
                }
            }

            findViewById<View>(R.id.nav_find)?.setOnClickListener {
                animateNavTabPress(
                    container = findViewById(R.id.nav_find),
                    icon = findViewById(R.id.nav_find_icon),
                    label = findViewById(R.id.nav_find_text)
                )
                if (this !is FindActivity) {
                    findViewById<View>(R.id.nav_find)?.postDelayed({
                        navigateTo(FindActivity::class.java)
                    }, 110)
                }
            }

            findViewById<View>(R.id.nav_profile)?.setOnClickListener {
                animateNavTabPress(
                    container = findViewById(R.id.nav_profile),
                    icon = findViewById(R.id.nav_profile_icon),
                    label = findViewById(R.id.nav_profile_text)
                )
                if (this !is ProfileActivity) {
                    findViewById<View>(R.id.nav_profile)?.postDelayed({
                        navigateTo(ProfileActivity::class.java)
                    }, 110)
                }
            }

            findViewById<View>(R.id.nav_paw_post)?.setOnClickListener {
                animatePawPress()
                findViewById<View>(R.id.nav_paw_post)?.postDelayed({
                    val intent = Intent(this, CreatePostActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                }, 140)
            }

            findViewById<View>(R.id.fab_gps_map)?.setOnClickListener {
                if (this !is NearbyPetsMapActivity) {
                    val intent = Intent(this, NearbyPetsMapActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(0, 0)
                }
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
        val defaultColor = "#666666"

        when (this) {
            is HomeActivity -> {
                animateTabSelection(
                    icon = findViewById(R.id.nav_home_icon),
                    label = findViewById(R.id.nav_home_text),
                    fromColor = defaultColor,
                    toColor = highlightColor
                )
            }
            is InboxActivity -> {
                animateTabSelection(
                    icon = findViewById(R.id.nav_inbox_icon),
                    label = findViewById(R.id.nav_inbox_text),
                    fromColor = defaultColor,
                    toColor = highlightColor
                )
                // Clear badge when viewing inbox
                updateInboxBadge(0)
            }
            is FindActivity -> {
                animateTabSelection(
                    icon = findViewById(R.id.nav_find_icon),
                    label = findViewById(R.id.nav_find_text),
                    fromColor = defaultColor,
                    toColor = highlightColor
                )
            }
            is ProfileActivity -> {
                animateTabSelection(
                    icon = findViewById(R.id.nav_profile_icon),
                    label = findViewById(R.id.nav_profile_text),
                    fromColor = defaultColor,
                    toColor = highlightColor
                )
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

    private fun animateNavTabPress(container: View?, icon: ImageView?, label: TextView?) {
        container ?: return

        container.animate()
            .scaleX(0.94f)
            .scaleY(0.94f)
            .translationY((-3).toFloat())
            .setDuration(70)
            .withEndAction {
                container.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(140)
                    .start()
            }
            .start()

        icon?.let { navIcon ->
            navIcon.animate()
                .scaleX(1.16f)
                .scaleY(1.16f)
                .setDuration(80)
                .withEndAction {
                    navIcon.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(140)
                        .start()
                }
                .start()
        }

        label?.let { navLabel ->
            navLabel.animate()
                .translationY((-2).toFloat())
                .setDuration(80)
                .withEndAction {
                    navLabel.animate()
                        .translationY(0f)
                        .setDuration(140)
                        .start()
                }
                .start()
        }
    }

    private fun animatePawPress() {
        val pawContainer = findViewById<View>(R.id.nav_paw_post) ?: return
        val pawIcon = findViewById<ImageView>(R.id.nav_paw_icon)

        pawContainer.animate()
            .scaleX(0.9f)
            .scaleY(0.9f)
            .translationY((-4).toFloat())
            .setDuration(75)
            .withEndAction {
                pawContainer.animate()
                    .scaleX(1.14f)
                    .scaleY(1.14f)
                    .translationY((-10).toFloat())
                    .setDuration(120)
                    .withEndAction {
                        pawContainer.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .translationY(0f)
                            .setDuration(160)
                            .start()
                    }
                    .start()
            }
            .start()

        pawIcon?.animate()
            ?.rotationBy(6f)
            ?.setDuration(90)
            ?.withEndAction {
                pawIcon.animate()
                    .rotation(0f)
                    .setDuration(180)
                    .start()
            }
            ?.start()
    }

    private fun animateTabSelection(icon: ImageView?, label: TextView?, fromColor: String, toColor: String) {
        val start = Color.parseColor(fromColor)
        val end = Color.parseColor(toColor)
        val animator = ValueAnimator.ofObject(ArgbEvaluator(), start, end)

        animator.duration = 180
        animator.addUpdateListener { valueAnimator ->
            val color = valueAnimator.animatedValue as Int
            icon?.setColorFilter(color)
            label?.setTextColor(color)
        }
        animator.start()

        icon?.animate()
            ?.scaleX(1.14f)
            ?.scaleY(1.14f)
            ?.setDuration(90)
            ?.withEndAction {
                icon.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(140)
                    .start()
            }
            ?.start()
    }

    fun updateInboxBadge(count: Int) {
        InboxBadgeManager.updateBadgeManually(count)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            if (sessionManager.isLoggedIn() && !FirebaseAuthHelper.isUserValid()) {
                sessionManager.clearSession()
                SocketManager.disconnect()

                val intent = Intent(this@BaseNavigationActivity, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    putExtra("force_logout", true)
                    putExtra("logout_reason", "Your account is no longer available.")
                }
                startActivity(intent)
                finish()
                return@launch
            }
        }
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
