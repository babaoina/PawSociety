package com.example.pawsociety

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.example.pawsociety.api.ApiPost
import com.example.pawsociety.data.repository.BlockRepository
import com.example.pawsociety.data.repository.HidePostRepository
import com.example.pawsociety.data.repository.PostRepository
import com.example.pawsociety.data.repository.ReportRepository
import com.example.pawsociety.util.FCMTokenManager
import com.example.pawsociety.util.NotificationManager
import com.example.pawsociety.util.SessionManager
import com.example.pawsociety.util.SocketManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import android.app.Dialog
import kotlin.math.max
import android.os.Handler
import android.os.Looper


class HomeActivity : BaseNavigationActivity() {

    private lateinit var viewModel: HomeViewModel
    private lateinit var postsContainer: LinearLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var sessionManager: SessionManager
    private lateinit var notificationBadge: TextView
    private lateinit var bottomNav: View

    // Category Filter Variables - ADDED
    private lateinit var layoutAll: LinearLayout
    private lateinit var layoutDogs: LinearLayout
    private lateinit var layoutCats: LinearLayout
    private lateinit var layoutFish: LinearLayout
    private lateinit var layoutBirds: LinearLayout

    private lateinit var allCircle: FrameLayout
    private lateinit var dogCircle: FrameLayout
    private lateinit var catCircle: FrameLayout
    private lateinit var fishCircle: FrameLayout
    private lateinit var birdCircle: FrameLayout

    private lateinit var textAll: TextView
    private lateinit var textDogs: TextView
    private lateinit var textCats: TextView
    private lateinit var textFish: TextView
    private lateinit var textBirds: TextView

    private lateinit var chipDogs: ImageView
    private lateinit var chipCats: ImageView
    private lateinit var chipFish: ImageView
    private lateinit var chipBirds: ImageView

    private var currentCategory = "All"
    // End Category Filter Variables

    private val postRepository = PostRepository()
    private val blockRepository = BlockRepository()
    private val reportRepository = ReportRepository()
    private val hidePostRepository = HidePostRepository()
    private var currentUser: com.example.pawsociety.api.ApiUser? = null
    private var hiddenPostIds = mutableSetOf<String>()

    // Activity result launchers
    private val createPostLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val postCreated = result.data?.getBooleanExtra("post_created", false) ?: false
            val postCategory = result.data?.getStringExtra("post_category") ?: ""

            if (postCreated) {
                // Force refresh posts from server
                viewModel.forceRefreshPosts()

                Toast.makeText(this, "Post created! Refreshing feed...", Toast.LENGTH_SHORT).show()

                // If we know the category, highlight it after refresh
                if (postCategory.isNotEmpty()) {
                    // Small delay to let the refresh happen
                    Handler(Looper.getMainLooper()).postDelayed({
                        // Switch to the category of the new post
                        when (postCategory) {
                            "Dogs" -> {
                                currentCategory = "Dogs"
                                highlightCategory("Dogs")
                                viewModel.forceRefreshAndFilter("Dogs")
                            }
                            "Cats" -> {
                                currentCategory = "Cats"
                                highlightCategory("Cats")
                                viewModel.forceRefreshAndFilter("Cats")
                            }
                            "Fish" -> {
                                currentCategory = "Fish"
                                highlightCategory("Fish")
                                viewModel.forceRefreshAndFilter("Fish")
                            }
                            "Birds" -> {
                                currentCategory = "Birds"
                                highlightCategory("Birds")
                                viewModel.forceRefreshAndFilter("Birds")
                            }
                        }
                    }, 500) // 500ms delay to let refresh complete
                }

                // Scroll to top to see new post
                val scrollView = findViewById<ScrollView>(R.id.feed_scrollview)
                scrollView?.post {
                    scrollView?.smoothScrollTo(0, 0)
                }
            }
        }
    }

    private val editPostLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.forceRefreshAndFilter(currentCategory)
            Toast.makeText(this, "Post updated! Refreshing feed...", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Make app full screen with content behind system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Control status bar icon colors (dark icons for light background)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.show(android.view.WindowInsets.Type.statusBars())
            }
        } else {
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    )
        }

        // Add this test in onCreate to verify animation file exists
        try {
            assets.open("like.json").use {
                Log.d("ANIMATION_TEST", "✅ like.json found in assets!")
            }
        } catch (e: Exception) {
            Log.e("ANIMATION_TEST", "❌ like.json NOT found! Check assets folder")
            Toast.makeText(this, "Animation file missing!", Toast.LENGTH_LONG).show()
        }

        sessionManager = SessionManager(this)

        val currentUser = sessionManager.getCurrentUser()
        if (currentUser == null) {
            println("⚠️ No user session found, redirecting to login")
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        println("✅ User logged in: ${currentUser.username}")

        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        viewModel.setSessionManager(sessionManager)

        postsContainer = findViewById(R.id.posts_container)
        emptyState = findViewById(R.id.empty_state)
        progressBar = findViewById(R.id.progress_bar)
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
        notificationBadge = findViewById(R.id.notification_badge)
        bottomNav = findViewById(R.id.bottom_navigation)

        // Setup bottom navigation to handle system bar insets
        setupBottomNavigationInsets()

        // Initialize category filters - ADDED
        initCategoryFilters()

        val bigPlusButton = findViewById<LinearLayout>(R.id.big_plus_button)
        bigPlusButton?.setOnClickListener {
            // Use the launcher instead of just starting activity
            createPostLauncher.launch(Intent(this, CreatePostActivity::class.java))
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (postsContainer.childCount > 0) {
                val firstPost = postsContainer.getChildAt(0)
                val testButton = firstPost.findViewById<com.example.pawsociety.widget.LikeButton>(R.id.btn_like_lottie_view)
                testButton?.let {
                    Log.d("LIKE_TEST", "Testing animation on first post")
                    // Force animation
                    it.setLiked(true, animate = true)

                    // Revert after 2 seconds
                    Handler(Looper.getMainLooper()).postDelayed({
                        it.setLiked(false, animate = true)
                    }, 2000)
                }
            }
        }, 3000)

        requestNotificationPermission()
        FCMTokenManager.initialize(currentUser.firebaseUid)

        SocketManager.connect()
        currentUser?.let {
            SocketManager.joinUserRoom(it.firebaseUid)
            println("🟢 User ${it.username} is now ONLINE")
        }

        setupSocketListeners()

        swipeRefreshLayout.setColorSchemeColors(
            Color.parseColor("#7A4F2B"),
            Color.parseColor("#FF6B35"),
            Color.parseColor("#4CAF50")
        )
        swipeRefreshLayout.setOnRefreshListener {
            refreshData()
        }

        NotificationManager.setupNotificationBadge(
            context = this,
            badgeView = notificationBadge,
            userId = currentUser.firebaseUid,
            lifecycleOwner = this
        ) {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }

        NotificationManager.setupSocketNotifications(currentUser.firebaseUid, this)

        findViewById<ImageView>(R.id.btn_notifications)?.setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }

        setupObservers()
    }

    // 🔥 NEW: Method to refresh ALL data
    private fun refreshAllData() {
        // Force refresh posts from server (not cache)
        viewModel.forceRefreshAndFilter(currentCategory)

        // Reload current user data
        viewModel.loadCurrentUser()

        // Reload hidden posts
        loadHiddenPosts()

        // Restart notification polling
        val currentUser = sessionManager.getCurrentUser()
        currentUser?.let {
            NotificationManager.startPolling(it.firebaseUid, this)
        }
    }

    /**
     * Setup bottom navigation to automatically adjust to system navigation bar
     * Works with gesture nav, 2-button nav, and 3-button nav
     */
    private fun setupBottomNavigationInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
            val systemBarInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout() or
                        WindowInsetsCompat.Type.ime()
            )

            // Get the navigation bar insets specifically
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            // Apply padding based on system navigation bar height
            // This ensures the app's nav bar sits ON TOP of the system nav bar
            view.updatePadding(
                bottom = navBarInsets.bottom
            )

            // Log the insets for debugging
            Log.d("Insets", "System bars bottom: ${systemBarInsets.bottom}")
            Log.d("Insets", "Nav bars bottom: ${navBarInsets.bottom}")

            // Return the insets without consuming them
            insets
        }

        // Request insets to be dispatched
        bottomNav.requestApplyInsets()
    }


    // ADDED: Initialize category filters
    private fun initCategoryFilters() {
        layoutAll = findViewById(R.id.layout_all)
        layoutDogs = findViewById(R.id.layout_dogs)
        layoutCats = findViewById(R.id.layout_cats)
        layoutFish = findViewById(R.id.layout_fish)
        layoutBirds = findViewById(R.id.layout_birds)

        allCircle = findViewById(R.id.all_circle)
        dogCircle = findViewById(R.id.dog_circle)
        catCircle = findViewById(R.id.cat_circle)
        fishCircle = findViewById(R.id.fish_circle)
        birdCircle = findViewById(R.id.bird_circle)

        textAll = findViewById(R.id.text_all)
        textDogs = findViewById(R.id.text_dogs)
        textCats = findViewById(R.id.text_cats)
        textFish = findViewById(R.id.text_fish)
        textBirds = findViewById(R.id.text_birds)

        chipDogs = findViewById(R.id.chip_dogs)
        chipCats = findViewById(R.id.chip_cats)
        chipFish = findViewById(R.id.chip_fish)
        chipBirds = findViewById(R.id.chip_birds)

        setupCategoryClickListeners()
        highlightCategory("All")
    }

    // ADDED: Setup category click listeners
    private fun setupCategoryClickListeners() {
        layoutAll.setOnClickListener {
            // Always force refresh first, regardless of current category
            currentCategory = "All"
            highlightCategory("All")
            viewModel.forceRefreshAndFilter("All")  // This ALWAYS makes an API call
        }

        layoutDogs.setOnClickListener {
            // Always force refresh first, regardless of current category
            currentCategory = "Dogs"
            highlightCategory("Dogs")
            viewModel.forceRefreshAndFilter("Dogs")  // This ALWAYS makes an API call
        }

        layoutCats.setOnClickListener {
            // Always force refresh first, regardless of current category
            currentCategory = "Cats"
            highlightCategory("Cats")
            viewModel.forceRefreshAndFilter("Cats")  // This ALWAYS makes an API call
        }

        layoutFish.setOnClickListener {
            // Always force refresh first, regardless of current category
            currentCategory = "Fish"
            highlightCategory("Fish")
            viewModel.forceRefreshAndFilter("Fish")  // This ALWAYS makes an API call
        }

        layoutBirds.setOnClickListener {
            // Always force refresh first, regardless of current category
            currentCategory = "Birds"
            highlightCategory("Birds")
            viewModel.forceRefreshAndFilter("Birds")  // This ALWAYS makes an API call
        }
    }

    // ADDED: Highlight selected category
    private fun highlightCategory(category: String) {
        // Reset all circles to default (white with gray border)
        allCircle.background = ContextCompat.getDrawable(this, R.drawable.circle_category_default)
        dogCircle.background = ContextCompat.getDrawable(this, R.drawable.circle_category_default)
        catCircle.background = ContextCompat.getDrawable(this, R.drawable.circle_category_default)
        fishCircle.background = ContextCompat.getDrawable(this, R.drawable.circle_category_default)
        birdCircle.background = ContextCompat.getDrawable(this, R.drawable.circle_category_default)

        // Reset text colors to gray
        textAll.setTextColor(Color.parseColor("#666666"))
        textDogs.setTextColor(Color.parseColor("#666666"))
        textCats.setTextColor(Color.parseColor("#666666"))
        textFish.setTextColor(Color.parseColor("#666666"))
        textBirds.setTextColor(Color.parseColor("#666666"))

        // Reset icon colors to gray
        chipDogs.setColorFilter(Color.parseColor("#999999"))
        chipCats.setColorFilter(Color.parseColor("#999999"))
        chipFish.setColorFilter(Color.parseColor("#999999"))
        chipBirds.setColorFilter(Color.parseColor("#999999"))

        // For "All" text inside circle
        val allTextView = allCircle.getChildAt(0) as TextView
        allTextView.setTextColor(Color.parseColor("#7A4F2B")) // Keep "All" text brown by default

        // Highlight selected category
        when (category) {
            "All" -> {
                allCircle.background = ContextCompat.getDrawable(this, R.drawable.circle_category_selected)
                textAll.setTextColor(Color.parseColor("#7A4F2B"))
                allTextView.setTextColor(Color.parseColor("#7A4F2B"))
            }
            "Dogs" -> {
                dogCircle.background = ContextCompat.getDrawable(this, R.drawable.circle_category_selected)
                textDogs.setTextColor(Color.parseColor("#7A4F2B"))
                chipDogs.setColorFilter(Color.parseColor("#7A4F2B")) // Brown icon when selected
            }
            "Cats" -> {
                catCircle.background = ContextCompat.getDrawable(this, R.drawable.circle_category_selected)
                textCats.setTextColor(Color.parseColor("#7A4F2B"))
                chipCats.setColorFilter(Color.parseColor("#7A4F2B"))
            }
            "Fish" -> {
                fishCircle.background = ContextCompat.getDrawable(this, R.drawable.circle_category_selected)
                textFish.setTextColor(Color.parseColor("#7A4F2B"))
                chipFish.setColorFilter(Color.parseColor("#7A4F2B"))
            }
            "Birds" -> {
                birdCircle.background = ContextCompat.getDrawable(this, R.drawable.circle_category_selected)
                textBirds.setTextColor(Color.parseColor("#7A4F2B"))
                chipBirds.setColorFilter(Color.parseColor("#7A4F2B"))
            }
        }
    }

    private fun loadHiddenPosts() {
        val currentUser = sessionManager.getCurrentUser() ?: return

        lifecycleScope.launch {
            try {
                val result = hidePostRepository.getHiddenPosts(currentUser.firebaseUid)
                if (result.isSuccess) {
                    val hiddenPosts = result.getOrNull() ?: emptyList()
                    hiddenPostIds.clear()
                    hiddenPostIds.addAll(hiddenPosts.map { it.postId })
                    Log.d("HomeActivity", "Loaded ${hiddenPostIds.size} hidden post IDs")
                } else {
                    Log.e("HomeActivity", "Failed to load hidden posts: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e("HomeActivity", "Error loading hidden posts: ${e.message}")
            }
        }
    }

    private fun setupSocketListeners() {
        SocketManager.on("new-post") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as? JSONObject
                    if (data != null) {
                        val post = parsePostFromJson(data)
                        runOnUiThread {
                            Snackbar.make(
                                findViewById(android.R.id.content),
                                "New post from ${post.userName}",
                                Snackbar.LENGTH_LONG
                            ).setAction("View") {
                                val scrollView = findViewById<ScrollView>(R.id.feed_scrollview)
                                scrollView?.smoothScrollTo(0, 0)
                                Toast.makeText(this@HomeActivity, "New post added!", Toast.LENGTH_SHORT).show()
                            }.show()

                            viewModel.forceRefreshAndFilter(currentCategory)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("HomeActivity", "Error parsing new post: ${e.message}")
                }
            }
        }

        SocketManager.on("profile-updated") { args ->
            if (args.isNotEmpty()) {
                try {
                    runOnUiThread {
                        viewModel.forceRefreshAndFilter(currentCategory)
                    }
                } catch (e: Exception) {
                    Log.e("HomeActivity", "Error parsing profile update: ${e.message}")
                }
            }
        }

        SocketManager.on("post-liked") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as? JSONObject
                    val postId = data?.optString("postId", "")
                    val likesCount = data?.optInt("likesCount", 0)
                    val isLiked = data?.optBoolean("isLiked", false)

                    runOnUiThread {
                        updateLikeCountInFeed(postId, likesCount, isLiked)
                    }
                } catch (e: Exception) {
                    Log.e("HomeActivity", "Error parsing post like: ${e.message}")
                }
            }
        }
    }

    private fun updateLikeCountInFeed(postId: String?, likesCount: Int?, isLiked: Boolean? = null) {
        if (postId == null || likesCount == null) return

        for (i in 0 until postsContainer.childCount) {
            val postView = postsContainer.getChildAt(i)
            val viewPostId = postView.tag as? String

            if (viewPostId == postId) {
                val tvLikeCount = postView.findViewById<TextView>(R.id.tv_like_count)
                tvLikeCount?.text = likesCount.toString()

                // Add pop animation to like count
                tvLikeCount?.animate()?.scaleX(1.2f)?.scaleY(1.2f)?.setDuration(100)?.withEndAction {
                    tvLikeCount?.animate()?.scaleX(1.0f)?.scaleY(1.0f)?.setDuration(100)?.start()
                }?.start()

                val likeButton = postView.findViewById<com.example.pawsociety.widget.LikeButton>(R.id.btn_like_lottie_view)

                if (isLiked != null && likeButton != null) {
                    likeButton.setLiked(isLiked, animate = false)
                }
                break
            }
        }
    }

    private fun parsePostFromJson(json: JSONObject): ApiPost {
        return ApiPost(
            postId = json.optString("postId", ""),
            firebaseUid = json.optString("firebaseUid", ""),
            userName = json.optString("userName", ""),
            userImageUrl = json.optString("userImageUrl", ""),
            petName = json.optString("petName", ""),
            petType = json.optString("petType", ""),
            gender = json.optString("gender", "Unknown"),
            status = json.optString("status", ""),
            description = json.optString("description", ""),
            location = json.optString("location", ""),
            reward = json.optString("reward", ""),
            contactInfo = json.optString("contactInfo", ""),
            imageUrls = json.optJSONArray("imageUrls")?.let {
                (0 until it.length()).map { i -> it.getString(i) }
            } ?: emptyList(),
            likesCount = json.optInt("likesCount", 0),
            createdAt = json.optString("createdAt", "")
        )
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }

    private fun updateLikeCountsOnly(posts: List<ApiPost>) {
        for (post in posts) {
            for (i in 0 until postsContainer.childCount) {
                val postView = postsContainer.getChildAt(i)
                if (postView.tag == post.postId) {
                    val tvLikeCount = postView.findViewById<TextView>(R.id.tv_like_count)
                    tvLikeCount?.text = post.likesCount.toString()
                    break
                }
            }
        }
    }

    private fun setupObservers() {
        viewModel.currentUser.observe(this, Observer { user ->
            currentUser = user
            if (user == null) {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        })

        // FIXED: ALWAYS clear and recreate views when posts change
        viewModel.posts.observe(this, Observer { posts ->
            Log.d("HomeActivity", "📊 Received ${posts.size} posts for category: $currentCategory")

            // Check if this is just a like count update or a full refresh
            val isLikeUpdate = posts.size == postsContainer.childCount &&
                    posts.all { post ->
                        postsContainer.findViewWithTag<View>(post.postId) != null
                    }

            if (isLikeUpdate) {
                // Just update like counts without recreating views
                updateLikeCountsOnly(posts)
            } else {
                // ALWAYS clear all existing views first for major changes
                postsContainer.removeAllViews()

                if (posts.isEmpty()) {
                    postsContainer.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                    val emptyText = findViewById<TextView>(R.id.empty_state_text)
                    emptyText?.text = "No posts in $currentCategory"
                    Log.d("HomeActivity", "📊 Showing empty state for $currentCategory")
                } else {
                    postsContainer.visibility = View.VISIBLE
                    emptyState.visibility = View.GONE

                    // Filter out hidden posts
                    val visiblePosts = if (hiddenPostIds.isNotEmpty()) {
                        posts.filter { post ->
                            post.postId != null && !hiddenPostIds.contains(post.postId)
                        }
                    } else {
                        posts
                    }

                    Log.d("HomeActivity", "📊 Displaying ${visiblePosts.size} visible posts out of ${posts.size} total")

                    val currentLikeMap = viewModel.likeStatus.value ?: emptyMap()
                    val currentFavMap = viewModel.favoriteStatus.value ?: emptyMap()

                    // Create views for each post
                    for (post in visiblePosts) {
                        createPostView(post, currentLikeMap, currentFavMap)
                    }

                    // Force layout update
                    postsContainer.invalidate()
                    postsContainer.requestLayout()
                }
            }

            if (swipeRefreshLayout.isRefreshing) {
                swipeRefreshLayout.isRefreshing = false
            }
        })

        // Keep your existing likeStatus observer
        viewModel.likeStatus.observe(this, Observer { likeMap ->
            for (i in 0 until postsContainer.childCount) {
                val postView = postsContainer.getChildAt(i)
                val postId = postView.tag as? String ?: continue
                val isLiked = likeMap[postId] ?: false
                val likeButton = postView.findViewById<com.example.pawsociety.widget.LikeButton>(R.id.btn_like_lottie_view)
                if (likeButton?.isLiked != isLiked) {
                    likeButton?.setLiked(isLiked, animate = false)  // No animation when syncing
                }
            }
        })

        // Rest of your observers...
        viewModel.isLoading.observe(this, Observer { isLoading ->
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        })

        viewModel.error.observe(this, Observer { error ->
            if (error != null) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                viewModel.clearError()
                if (swipeRefreshLayout.isRefreshing) {
                    swipeRefreshLayout.isRefreshing = false
                }
            }
        })

        // Add this to setupObservers() - update favorite buttons when favoriteStatus changes
        viewModel.favoriteStatus.observe(this, Observer { favMap ->
            for (i in 0 until postsContainer.childCount) {
                val postView = postsContainer.getChildAt(i)
                val postId = postView.tag as? String ?: continue

                val isFav = favMap[postId] ?: false
                val btnFavorite = postView.findViewById<ImageView>(R.id.btn_favorite)

                if (btnFavorite != null) {
                    if (isFav) {
                        btnFavorite.setColorFilter(Color.parseColor("#B88B4A"), PorterDuff.Mode.SRC_ATOP)
                        btnFavorite.tag = "favorited"
                    } else {
                        btnFavorite.setColorFilter(Color.parseColor("#666666"), PorterDuff.Mode.SRC_ATOP)
                        btnFavorite.tag = "unfavorited"
                    }
                }
            }
        })

        viewModel.isOffline.observe(this, Observer { isOffline ->
            val offlineIndicator = findViewById<TextView>(R.id.tv_offline_indicator)
            offlineIndicator?.visibility = if (isOffline) View.VISIBLE else View.GONE
        })
    }

    private fun refreshData() {
        refreshAllData()
    }

    private fun displayPosts(posts: List<ApiPost>) {
        // This function is kept for compatibility but is no longer the primary display method
        // The observer now handles display directly
        postsContainer.removeAllViews()

        Log.d("HomeActivity", "📊 displayPosts called with ${posts.size} posts")

        if (posts.isEmpty()) {
            postsContainer.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
            return
        }

        // SAFETY: Filter out null posts and hidden posts
        val validPosts = posts.filterNotNull()

        val visiblePosts = if (hiddenPostIds.isNotEmpty()) {
            validPosts.filter { post ->
                post.postId != null && !hiddenPostIds.contains(post.postId)
            }
        } else {
            validPosts
        }

        Log.d("HomeActivity", "Total posts: ${validPosts.size}, Hidden: ${hiddenPostIds.size}, Visible: ${visiblePosts.size}")

        if (visiblePosts.isEmpty()) {
            postsContainer.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
            val emptyText = findViewById<TextView>(R.id.empty_state_text)
            emptyText?.text = "No posts in this category"
            return
        }

        postsContainer.visibility = View.VISIBLE
        emptyState.visibility = View.GONE

        val currentLikeMap = viewModel.likeStatus.value ?: emptyMap()
        val currentFavMap = viewModel.favoriteStatus.value ?: emptyMap()

        // Force recreate ALL views for fresh data
        for (post in visiblePosts) {
            createPostView(post, currentLikeMap, currentFavMap)
        }

        // Force layout update
        postsContainer.invalidate()
        postsContainer.requestLayout()
    }

    private fun createPostView(
        post: ApiPost,
        likeStatusMap: Map<String, Boolean>,
        favoriteStatusMap: Map<String, Boolean>
    ) {
        try {
            val inflater = LayoutInflater.from(this)
            val postView = inflater.inflate(R.layout.item_post, postsContainer, false)

            postView.tag = post.postId

            // ===== FIND VIEWS =====
            val profileIcon = postView.findViewById<TextView>(R.id.post_profile_icon)
            val profileImage = postView.findViewById<ImageView>(R.id.post_profile_image)
            val userNameText = postView.findViewById<TextView>(R.id.post_user_name)
            val userRoleText = postView.findViewById<TextView>(R.id.post_user_role)
            val locationText = postView.findViewById<TextView>(R.id.post_location)
            val postDate = postView.findViewById<TextView>(R.id.post_date)
            val btnMore = postView.findViewById<ImageView>(R.id.btn_more)

            // Image views
            val viewPager = postView.findViewById<ViewPager2>(R.id.post_view_pager)
            val singleImageView = postView.findViewById<ImageView>(R.id.post_image)
            val indicatorContainer = postView.findViewById<LinearLayout>(R.id.indicator_container)
            val noImagePlaceholder = postView.findViewById<LinearLayout>(R.id.no_image_placeholder)

            // Status badges
            val statusBadge = postView.findViewById<TextView>(R.id.post_status)
            val rewardBadge = postView.findViewById<TextView>(R.id.post_reward_badge)

            // Pet info
            val petTypeText = postView.findViewById<TextView>(R.id.post_pet_type)
            val petNameText = postView.findViewById<TextView>(R.id.post_pet_name)

            // Gender, Age, Weight
            val genderIcon = postView.findViewById<ImageView>(R.id.post_gender_icon)
            val genderText = postView.findViewById<TextView>(R.id.post_gender_text)
            val postAge = postView.findViewById<TextView>(R.id.post_age)
            val postWeight = postView.findViewById<TextView>(R.id.post_weight)

            // About section
            val descriptionText = postView.findViewById<TextView>(R.id.post_description)
            val btnMoreDescription = postView.findViewById<TextView>(R.id.btn_more_description)

            // Contact and reward
            val contactText = postView.findViewById<TextView>(R.id.post_contact)
            val rewardText = postView.findViewById<TextView>(R.id.post_reward)
            val rewardContainer = postView.findViewById<LinearLayout>(R.id.reward_container)

            // Action buttons
            val btnLikeGroup = postView.findViewById<LinearLayout>(R.id.btn_like_group)
            val likeButton = postView.findViewById<com.example.pawsociety.widget.LikeButton>(R.id.btn_like_lottie_view)
            val tvLikeCount = postView.findViewById<TextView>(R.id.tv_like_count)
            val btnMessageGroup = postView.findViewById<LinearLayout>(R.id.btn_message_group)
            val btnMessage = postView.findViewById<ImageView>(R.id.btn_message)
            val btnShareGroup = postView.findViewById<LinearLayout>(R.id.btn_share_group)
            val btnShare = postView.findViewById<ImageView>(R.id.btn_share)
            val btnFavoriteGroup = postView.findViewById<LinearLayout>(R.id.btn_favorite_group)
            val btnFavorite = postView.findViewById<ImageView>(R.id.btn_favorite)


            // ===== SET BASIC DATA (ALL NULL-SAFE) =====
            userNameText?.text = post.userName
            locationText?.text = post.location ?: "Unknown location"
            postDate?.text = getTimeAgo(post.createdAt)
            petNameText?.text = post.petName
            petTypeText?.text = post.petType
            descriptionText?.text = post.description
            tvLikeCount?.text = post.likesCount.toString()
            contactText?.text = "📞 ${post.contactInfo}"

            // ===== SET GENDER =====
            // Alternative if you're using text view for gender
            when (post.gender?.lowercase()) {
                "male" -> {
                    genderText?.text = "Male"
                    genderText?.setTextColor(Color.parseColor("#2196F3")) // Blue
                    genderIcon?.visibility = View.GONE
                }
                "female" -> {
                    genderText?.text = "Female"
                    genderText?.setTextColor(Color.parseColor("#E91E63")) // Pink
                    genderIcon?.visibility = View.GONE
                }
                else -> {
                    genderText?.text = post.gender ?: "Unknown"
                    genderText?.setTextColor(Color.parseColor("#666666")) // Gray
                    genderIcon?.visibility = View.GONE
                }
            }

            // ===== SET PROFILE ICON =====
            val firstLetter = if (post.userName.isNotEmpty()) {
                post.userName.first().toString().uppercase()
            } else {
                "?"
            }
            profileIcon?.text = firstLetter

            // ===== LOAD PROFILE IMAGE IF AVAILABLE =====
            if (!post.userImageUrl.isNullOrEmpty()) {
                val fullImageUrl = if (post.userImageUrl.startsWith("http")) {
                    post.userImageUrl
                } else {
                    "${com.example.pawsociety.api.ApiClient.FULL_BASE_URL}${post.userImageUrl}"
                }

                profileIcon?.visibility = View.GONE
                profileImage?.visibility = View.VISIBLE

                Glide.with(this@HomeActivity)
                    .load(fullImageUrl)
                    .circleCrop()
                    .placeholder(R.drawable.circle_solid_profile)
                    .error(R.drawable.circle_solid_profile)
                    .into(profileImage!!)
            }

            // ===== SET AGE AND WEIGHT =====
            if (post.age.isNullOrEmpty()) {
                postAge?.visibility = View.GONE
            } else {
                postAge?.visibility = View.VISIBLE
                postAge?.text = post.age
            }

            if (post.weight.isNullOrEmpty()) {
                postWeight?.visibility = View.GONE
            } else {
                postWeight?.visibility = View.VISIBLE
                postWeight?.text = post.weight
            }

            // ===== SET STATUS BADGE =====
            when (post.status) {
                "Lost" -> {
                    statusBadge?.setBackgroundResource(R.drawable.status_badge_lost)
                    statusBadge?.text = "LOST"
                    statusBadge?.setTextColor(Color.WHITE)

                    if (!post.reward.isNullOrEmpty()) {
                        val formattedReward = formatReward(post.reward)
                        val digitsOnly = post.reward.replace("[^0-9]".toRegex(), "")
                        val originalNumber = if (digitsOnly.isNotEmpty()) digitsOnly.toLong() else 0

                        rewardBadge?.text = if (originalNumber > 1000000) {
                            "Reward: ₱$formattedReward (max limit)"
                        } else {
                            "Reward: ₱$formattedReward"
                        }
                        rewardBadge?.visibility = View.VISIBLE

                        rewardText?.text = "Reward: ₱$formattedReward"
                        rewardText?.visibility = View.VISIBLE
                        rewardContainer?.visibility = View.VISIBLE
                    } else {
                        rewardBadge?.visibility = View.GONE
                        rewardText?.visibility = View.GONE
                        rewardContainer?.visibility = View.GONE
                    }
                }
                "Found" -> {
                    statusBadge?.setBackgroundResource(R.drawable.status_badge_found)
                    statusBadge?.text = "FOUND"
                    statusBadge?.setTextColor(Color.WHITE)
                    rewardBadge?.visibility = View.GONE
                    rewardText?.visibility = View.GONE
                    rewardContainer?.visibility = View.GONE
                }
                "Adoption" -> {
                    statusBadge?.setBackgroundResource(R.drawable.status_badge_adoption)
                    statusBadge?.text = "ADOPTION"
                    statusBadge?.setTextColor(Color.WHITE)
                    rewardBadge?.visibility = View.GONE
                    rewardText?.visibility = View.GONE
                    rewardContainer?.visibility = View.GONE
                }
            }

            // ===== HANDLE IMAGES =====
            if (!post.imageUrls.isNullOrEmpty() && post.imageUrls.isNotEmpty()) {
                if (post.imageUrls.size > 1) {
                    viewPager?.visibility = View.VISIBLE
                    singleImageView?.visibility = View.GONE
                    noImagePlaceholder?.visibility = View.GONE

                    val imageAdapter = PostImagePagerAdapter(post.imageUrls)
                    viewPager?.adapter = imageAdapter
                    viewPager?.offscreenPageLimit = 1

                    setupImageIndicators(indicatorContainer!!, post.imageUrls.size)
                    indicatorContainer?.visibility = View.VISIBLE

                    viewPager?.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                        override fun onPageSelected(position: Int) {
                            super.onPageSelected(position)
                            updateIndicatorSelection(indicatorContainer!!, position)
                        }
                    })

                    viewPager?.setOnClickListener {
                        openImageGallery(post.imageUrls, viewPager?.currentItem ?: 0)
                    }
                } else {
                    viewPager?.visibility = View.GONE
                    singleImageView?.visibility = View.VISIBLE
                    indicatorContainer?.visibility = View.GONE
                    noImagePlaceholder?.visibility = View.GONE

                    val imageUrl = post.imageUrls[0]
                    val fullImageUrl = if (imageUrl.startsWith("http")) {
                        imageUrl
                    } else {
                        "${com.example.pawsociety.api.ApiClient.FULL_BASE_URL}$imageUrl"
                    }

                    Glide.with(this)
                        .load(fullImageUrl)
                        .centerCrop()
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_report_image)
                        .into(singleImageView!!)

                    singleImageView?.setOnClickListener {
                        openImageGallery(post.imageUrls, 0)
                    }
                }
            } else {
                viewPager?.visibility = View.GONE
                singleImageView?.visibility = View.GONE
                indicatorContainer?.visibility = View.GONE
                noImagePlaceholder?.visibility = View.VISIBLE
            }

            // ===== HANDLE "MORE" BUTTON FOR LONG DESCRIPTION =====
            descriptionText?.maxLines = Integer.MAX_VALUE
            descriptionText?.ellipsize = null

            descriptionText?.post {
                val lineCount = descriptionText.lineCount
                if (lineCount > 2) {
                    descriptionText?.maxLines = 2
                    descriptionText?.ellipsize = TextUtils.TruncateAt.END
                    btnMoreDescription?.visibility = View.VISIBLE

                    btnMoreDescription?.tag = false
                    btnMoreDescription?.setOnClickListener {
                        val isCollapsed = btnMoreDescription?.tag as? Boolean ?: false
                        if (isCollapsed) {
                            descriptionText?.maxLines = Integer.MAX_VALUE
                            descriptionText?.ellipsize = null
                            btnMoreDescription?.text = "less"
                            btnMoreDescription?.tag = false
                        } else {
                            descriptionText?.maxLines = 2
                            descriptionText?.ellipsize = TextUtils.TruncateAt.END
                            btnMoreDescription?.text = "more"
                            btnMoreDescription?.tag = true
                        }
                    }
                } else {
                    btnMoreDescription?.visibility = View.GONE
                }
            }

            // ===== SET INITIAL LIKE STATE =====
            val isLiked = likeStatusMap[post.postId] ?: false
            likeButton?.setLiked(isLiked, animate = false)  // This should be false for initial state
            Log.d("LIKE_ANIMATION", "Home - Initial state for post ${post.postId}: $isLiked")

            // ===== SET INITIAL FAVORITE STATE =====
            val isFav = favoriteStatusMap[post.postId] ?: false
            if (isFav) {
                btnFavorite?.setColorFilter(Color.parseColor("#B88B4A"), PorterDuff.Mode.SRC_ATOP)
                btnFavorite?.tag = "favorited"
            } else {
                btnFavorite?.setColorFilter(Color.parseColor("#666666"), PorterDuff.Mode.SRC_ATOP)
                btnFavorite?.tag = "unfavorited"
            }

            // ===== FAVORITE BUTTON CLICK HANDLER =====
            btnFavoriteGroup?.setOnClickListener {
                val user = currentUser
                if (user == null) {
                    Toast.makeText(this, "Please login to favorite", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val isFav = btnFavorite?.tag == "favorited"

                // Toggle immediately for better UX
                if (isFav) {
                    btnFavorite?.setColorFilter(Color.parseColor("#666666"), PorterDuff.Mode.SRC_ATOP)
                    btnFavorite?.tag = "unfavorited"
                } else {
                    btnFavorite?.setColorFilter(Color.parseColor("#B88B4A"), PorterDuff.Mode.SRC_ATOP)
                    btnFavorite?.tag = "favorited"
                }

                viewModel.toggleFavorite(post, isFav)
            }

            // ===== CLICK LISTENERS =====

            // Profile click
            profileIcon?.setOnClickListener {
                try {
                    val user = currentUser
                    if (user?.firebaseUid == post.firebaseUid) {
                        val intent = Intent(this@HomeActivity, ProfileActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        startActivity(intent)
                    } else {
                        val intent = Intent(this@HomeActivity, UserProfileActivity::class.java)
                        intent.putExtra("userId", post.firebaseUid)
                        intent.putExtra("userName", post.userName)
                        startActivity(intent)
                    }
                } catch (e: Exception) {
                    println("❌ Error opening profile: ${e.message}")
                }
            }

            // Username click
            userNameText?.setOnClickListener {
                try {
                    val user = currentUser
                    if (user?.firebaseUid == post.firebaseUid) {
                        val intent = Intent(this@HomeActivity, ProfileActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        startActivity(intent)
                    } else {
                        val intent = Intent(this@HomeActivity, UserProfileActivity::class.java)
                        intent.putExtra("userId", post.firebaseUid)
                        intent.putExtra("userName", post.userName)
                        startActivity(intent)
                    }
                } catch (e: Exception) {
                    println("❌ Error opening profile: ${e.message}")
                }
            }

            // More options click
            btnMore?.setOnClickListener {
                showPostOptions(post, likeButton!!)
            }

            // ===== LIKE BUTTON CLICK HANDLER =====
            btnLikeGroup?.setOnClickListener {
                Log.d("LIKE_CLICK", "Like button clicked! isEnabled=${btnLikeGroup?.isEnabled}")
                val user = currentUser
                if (user == null) {
                    Toast.makeText(this@HomeActivity, "Please login to like posts", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (btnLikeGroup?.isEnabled == false) return@setOnClickListener
                btnLikeGroup?.isEnabled = false

                val wasAlreadyLiked = likeButton?.isLiked ?: false

                // DEBUG LOG
                Log.d("LIKE_ANIMATION", "Home - Before: isLiked=$wasAlreadyLiked, setting to ${!wasAlreadyLiked} with animate=true")

                // IMPORTANT: Force animation to play
                if (likeButton != null) {
                    // First make sure the animation view is visible
                    likeButton.setLiked(!wasAlreadyLiked, animate = true)
                }

                // Update count immediately
                val currentCount = tvLikeCount?.text.toString().toIntOrNull() ?: 0
                tvLikeCount?.text = (if (!wasAlreadyLiked) currentCount + 1 else (currentCount - 1).coerceAtLeast(0)).toString()

                // Call ViewModel
                viewModel.toggleLike(post, wasAlreadyLiked)

                Handler(Looper.getMainLooper()).postDelayed({
                    btnLikeGroup?.isEnabled = true
                }, 1000)
            }

            // Share button click
            btnShareGroup?.setOnClickListener {
                sharePost(post)
            }

            // ===== FIXED FAVORITE BUTTON CLICK HANDLER =====
            btnFavoriteGroup?.setOnClickListener {
                val user = currentUser
                if (user == null) {
                    Toast.makeText(this, "Please login to favorite", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val isFav = btnFavorite?.tag == "favorited"

                // Toggle immediately for better UX
                if (isFav) {
                    btnFavorite?.setColorFilter(Color.parseColor("#666666"), PorterDuff.Mode.SRC_ATOP)
                    btnFavorite?.tag = "unfavorited"
                } else {
                    btnFavorite?.setColorFilter(Color.parseColor("#B88B4A"), PorterDuff.Mode.SRC_ATOP)
                    btnFavorite?.tag = "favorited"
                }

                // Call ViewModel to toggle favorite
                viewModel.toggleFavorite(post, isFav)
            }

            // Message button - FIXED LINE 755
            val user = currentUser
            if (post.firebaseUid == user?.firebaseUid) {
                btnMessageGroup?.visibility = View.GONE
            } else {
                btnMessageGroup?.visibility = View.VISIBLE
                btnMessageGroup?.setOnClickListener {
                    val intent = Intent(this, ChatActivity::class.java)
                    intent.putExtra("receiverUid", post.firebaseUid)
                    intent.putExtra("receiverUsername", post.userName)
                    startActivity(intent)
                }
            }

            postsContainer.addView(postView)
        } catch (e: Exception) {
            println("❌ Error creating post view: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun setupImageIndicators(container: LinearLayout, count: Int) {
        container.removeAllViews()
        for (i in 0 until count) {
            val indicatorView = layoutInflater.inflate(R.layout.item_image_indicator, container, false)
            val dot = indicatorView.findViewById<View>(R.id.indicator_dot)
            dot.isSelected = (i == 0)
            container.addView(indicatorView)
        }
    }

    private fun updateIndicatorSelection(container: LinearLayout, position: Int) {
        for (i in 0 until container.childCount) {
            val indicatorView = container.getChildAt(i)
            val dot = indicatorView.findViewById<View>(R.id.indicator_dot)
            dot.isSelected = (i == position)
        }
    }

    private fun openImageGallery(imageUrls: List<String>?, position: Int) {
        if (imageUrls.isNullOrEmpty()) return

        val intent = Intent(this, ImageGalleryActivity::class.java)
        intent.putStringArrayListExtra("image_urls", ArrayList(imageUrls))
        intent.putExtra("current_position", position)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun formatReward(reward: String): String {
        return try {
            val digitsOnly = reward.replace("[^0-9]".toRegex(), "")
            if (digitsOnly.isEmpty()) return reward
            var number = digitsOnly.toLong()
            if (number > 1000000) {
                number = 1000000
            }
            String.format("%,d", number)
        } catch (e: Exception) {
            reward
        }
    }


    private fun getTimeAgo(dateTime: String): String {
        return try {
            var date: Date? = null
            try {
                val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault())
                isoFormat.timeZone = TimeZone.getTimeZone("UTC")
                date = isoFormat.parse(dateTime)
            } catch (e: Exception) {
                try {
                    val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    date = format.parse(dateTime)
                } catch (e2: Exception) {
                }
            }

            val now = Date()
            if (date != null) {
                val diff = now.time - date.time
                val seconds = diff / 1000
                val minutes = seconds / 60
                val hours = minutes / 60
                val days = hours / 24

                when {
                    days > 0 -> "${days}d ago"
                    hours > 0 -> "${hours}h ago"
                    minutes > 0 -> "${minutes}m ago"
                    else -> "Just now"
                }
            } else {
                "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun showPostOptions(post: ApiPost, likeButton: com.example.pawsociety.widget.LikeButton) {
        try {
            val dialogView = layoutInflater.inflate(R.layout.post_menu_instagram, null)

            val menuUsername = dialogView.findViewById<TextView>(R.id.menu_username)
            val menuPostInfo = dialogView.findViewById<TextView>(R.id.menu_post_info)
            val btnClose = dialogView.findViewById<TextView>(R.id.btn_close)

            val optionAddFavorites = dialogView.findViewById<LinearLayout>(R.id.option_add_favorites)
            val ivFavoriteIcon = dialogView.findViewById<ImageView>(R.id.iv_favorite_icon)
            val tvFavoriteText = dialogView.findViewById<TextView>(R.id.tv_favorite_text)

            val optionFollow = dialogView.findViewById<LinearLayout>(R.id.option_follow)
            val ivFollowIcon = dialogView.findViewById<ImageView>(R.id.iv_follow_icon)
            val tvFollowText = dialogView.findViewById<TextView>(R.id.tv_follow_text)

            val optionWhySeeing = dialogView.findViewById<LinearLayout>(R.id.option_why_seeing)
            val optionHide = dialogView.findViewById<LinearLayout>(R.id.option_hide)
            val optionAboutAccount = dialogView.findViewById<LinearLayout>(R.id.option_about_account)

            val optionEdit = dialogView.findViewById<LinearLayout>(R.id.option_edit)
            val ivEditIcon = dialogView.findViewById<ImageView>(R.id.iv_edit_icon)
            val tvEditText = dialogView.findViewById<TextView>(R.id.tv_edit_text)

            val optionBlock = dialogView.findViewById<LinearLayout>(R.id.option_block)
            val ivBlockIcon = dialogView.findViewById<ImageView>(R.id.iv_block_icon)
            val tvBlockText = dialogView.findViewById<TextView>(R.id.tv_block_text)

            val optionReport = dialogView.findViewById<LinearLayout>(R.id.option_report)
            val optionDelete = dialogView.findViewById<LinearLayout>(R.id.option_delete)

            menuUsername?.text = post.userName
            menuPostInfo?.text = "${post.petName} • ${post.status}"

            viewModel.favoriteStatus.value?.let { favMap ->
                val isFav = favMap[post.postId] ?: false
                if (isFav) {
                    tvFavoriteText?.text = "Remove from favorites"
                    ivFavoriteIcon?.setColorFilter(Color.parseColor("#B88B4A"), PorterDuff.Mode.SRC_ATOP)
                } else {
                    tvFavoriteText?.text = "Add to favorites"
                    ivFavoriteIcon?.setColorFilter(Color.parseColor("#000000"), PorterDuff.Mode.SRC_ATOP)
                }
            }

            val user = currentUser
            val isFollowing = viewModel.checkFollowStatus(post.firebaseUid)

            val isOwnPost = (user != null && post.firebaseUid == user.firebaseUid)

            val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
            dialog.setContentView(dialogView)

            dialog.window?.apply {
                setGravity(android.view.Gravity.BOTTOM)
                setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setBackgroundDrawable(null)
            }

            btnClose?.setOnClickListener {
                dialog.dismiss()
            }

            if (isOwnPost) {
                optionEdit?.visibility = View.VISIBLE
                optionEdit?.setOnClickListener {
                    openEditPost(post)
                    dialog.dismiss()
                }

                optionDelete?.visibility = View.VISIBLE
                optionDelete?.setOnClickListener {
                    showDeleteConfirmation(post)
                    dialog.dismiss()
                }

                optionFollow?.visibility = View.GONE
                optionBlock?.visibility = View.GONE
            } else {
                optionEdit?.visibility = View.GONE
                optionDelete?.visibility = View.GONE

                optionFollow?.visibility = View.VISIBLE
                if (isFollowing) {
                    tvFollowText?.text = "Unfollow"
                    ivFollowIcon?.setImageResource(R.drawable.delete)
                    ivFollowIcon?.setColorFilter(Color.parseColor("#F44336"), PorterDuff.Mode.SRC_ATOP)
                } else {
                    tvFollowText?.text = "Follow"
                    ivFollowIcon?.setImageResource(R.drawable.add)
                    ivFollowIcon?.setColorFilter(Color.parseColor("#000000"), PorterDuff.Mode.SRC_ATOP)
                }

                optionFollow?.setOnClickListener {
                    val isFollowing = viewModel.checkFollowStatus(post.firebaseUid)
                    viewModel.toggleFollow(post.firebaseUid, isFollowing)
                    dialog.dismiss()
                }

                optionBlock?.visibility = View.VISIBLE
                optionBlock?.setOnClickListener {
                    showBlockConfirmation(post)
                    dialog.dismiss()
                }
            }

            optionAddFavorites?.setOnClickListener {
                val isFav = viewModel.favoriteStatus.value?.get(post.postId) ?: false
                viewModel.toggleFavorite(post, isFav)
                dialog.dismiss()
            }

            optionWhySeeing?.setOnClickListener {
                showWhySeeingDialog(post)
                dialog.dismiss()
            }

            optionHide?.setOnClickListener {
                showHideConfirmation(post)
                dialog.dismiss()
            }

            optionAboutAccount?.setOnClickListener {
                showAboutAccountDialog(post)
                dialog.dismiss()
            }

            optionReport?.setOnClickListener {
                showReportDialog(post)
                dialog.dismiss()
            }

            dialog.show()
        } catch (e: Exception) {
            println("❌ Error showing post options: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "Menu not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showHideConfirmation(post: ApiPost) {
        AlertDialog.Builder(this)
            .setTitle("Hide Post")
            .setMessage("This post will be hidden from your feed. You can unhide it later in Settings.")
            .setPositiveButton("Hide") { _, _ ->
                hidePost(post)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun hidePost(post: ApiPost) {
        val currentUser = sessionManager.getCurrentUser() ?: return

        lifecycleScope.launch {
            val hideRepository = HidePostRepository()
            val result = hideRepository.hidePost(currentUser.firebaseUid, post.postId)

            if (result.isSuccess) {
                Toast.makeText(this@HomeActivity, "Post hidden", Toast.LENGTH_SHORT).show()
                hiddenPostIds.add(post.postId)
                viewModel.forceRefreshAndFilter(currentCategory)
            } else {
                val error = result.exceptionOrNull()
                Toast.makeText(this@HomeActivity, "Failed to hide post: ${error?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun removePostFromFeed(postId: String) {
        for (i in 0 until postsContainer.childCount) {
            val postView = postsContainer.getChildAt(i)
            val viewPostId = postView.tag as? String

            if (viewPostId == postId) {
                postsContainer.removeViewAt(i)
                if (postsContainer.childCount == 0) {
                    postsContainer.visibility = View.GONE
                    emptyState.visibility = View.VISIBLE
                    Toast.makeText(this, "All posts are hidden", Toast.LENGTH_SHORT).show()
                }
                break
            }
        }
    }

    private fun showBlockConfirmation(post: ApiPost) {
        AlertDialog.Builder(this)
            .setTitle("Block User")
            .setMessage("Are you sure you want to block ${post.userName}? You will no longer see their posts and they cannot interact with you.")
            .setPositiveButton("Block") { _, _ ->
                blockUser(post.firebaseUid, post.userName)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun blockUser(userId: String, userName: String) {
        val currentUser = currentUser ?: return

        lifecycleScope.launch {
            val result = blockRepository.blockUser(currentUser.firebaseUid, userId)

            if (result.isSuccess) {
                Toast.makeText(this@HomeActivity, "Blocked $userName", Toast.LENGTH_SHORT).show()
                viewModel.forceRefreshAndFilter(currentCategory)
            } else {
                Toast.makeText(this@HomeActivity, "Failed to block user", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showUndoSnackbar(post: ApiPost) {
        try {
            val view = findViewById<View>(android.R.id.content)
            val snackbar = Snackbar.make(view, "Post hidden", Snackbar.LENGTH_LONG)
            snackbar.setAction("Undo") {
                Toast.makeText(this, "Post restored", Toast.LENGTH_SHORT).show()
            }
            snackbar.show()
        } catch (e: Exception) {
            println("❌ Error showing snackbar: ${e.message}")
        }
    }

    private fun showWhySeeingDialog(post: ApiPost) {
        try {
            val message = """
                You're seeing this post because:
                
                • You follow @${post.userName}
                • This post has high engagement
                • Similar content you've liked before
            """.trimIndent()

            AlertDialog.Builder(this)
                .setTitle("Why you're seeing this post")
                .setMessage(message)
                .setPositiveButton("Got it", null)
                .show()
        } catch (e: Exception) {
            println("❌ Error showing why seeing dialog: ${e.message}")
        }
    }

    private fun showAboutAccountDialog(post: ApiPost) {
        try {
            val message = """
                About @${post.userName}

                📝 This account posts about pets and rescue stories.
                📍 Location information not available
            """.trimIndent()

            AlertDialog.Builder(this)
                .setTitle("About this account")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        } catch (e: Exception) {
            println("❌ Error showing about dialog: ${e.message}")
        }
    }

    private fun showReportDialog(post: ApiPost) {
        val options = arrayOf("Spam", "Inappropriate", "False information", "Scam", "Harassment", "Other")

        AlertDialog.Builder(this)
            .setTitle("Report Post")
            .setItems(options) { _, which ->
                submitReport(post, options[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submitReport(post: ApiPost, reason: String) {
        val currentUser = currentUser ?: return

        lifecycleScope.launch {
            val result = reportRepository.createReport(
                reporterUid = currentUser.firebaseUid,
                reason = reason.lowercase().replace(" ", "_"),
                postId = post.postId,
                description = "Reported for: $reason"
            )

            if (result.isSuccess) {
                Toast.makeText(this@HomeActivity, "Report submitted. Thank you for helping keep PawSociety safe!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@HomeActivity, "Failed to submit report", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openEditPost(post: ApiPost) {
        val intent = Intent(this, EditPostActivity::class.java)
        intent.putExtra("post", post)
        editPostLauncher.launch(intent)
    }

    private fun showDeleteConfirmation(post: ApiPost) {
        AlertDialog.Builder(this)
            .setTitle("Delete Post")
            .setMessage("Are you sure you want to delete this post?")
            .setPositiveButton("Delete") { _, _ ->
                val user = currentUser
                if (user != null) {
                    viewModel.deletePost(post.postId)
                } else {
                    Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sharePost(post: ApiPost) {
        try {
            val shareText = """
                Check out this pet on PawSociety!
                
                ${post.petName} - ${post.status}
                Posted by: ${post.userName}
                Location: ${post.location}
                
                ${post.description}
                
                Contact: ${post.contactInfo}
            """.trimIndent()

            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText)
            startActivity(Intent.createChooser(shareIntent, "Share post"))
        } catch (e: Exception) {
            println("❌ Error sharing post: ${e.message}")
            Toast.makeText(this, "Sharing not available", Toast.LENGTH_SHORT).show()
        }
    }

    // Add this temporarily to see what's happening
    private fun debugPostCount() {
        Log.d("HomeActivity", "🔍 Container has ${postsContainer.childCount} views")
        for (i in 0 until postsContainer.childCount) {
            val view = postsContainer.getChildAt(i)
            val tag = view.tag as? String ?: "no tag"
            Log.d("HomeActivity", "   View $i: tag=$tag")
        }
    }

    override fun onResume() {
        super.onResume()
        // Don't just load from cache - force refresh!
        viewModel.forceRefreshAndFilter(currentCategory)  // Use force refresh
        viewModel.loadCurrentUser()
        loadHiddenPosts()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::notificationBadge.isInitialized) {
            NotificationManager.cleanup(notificationBadge)
        }
        SocketManager.disconnect()
    }

    companion object {
        private const val CREATE_POST_REQUEST = 1001
        private const val EDIT_POST_REQUEST = 1002
    }
}