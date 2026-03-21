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
import com.example.pawsociety.data.repository.SettingsRepository
import com.example.pawsociety.util.FCMTokenManager
import com.example.pawsociety.util.NotificationManager
import com.example.pawsociety.util.SessionManager
import com.example.pawsociety.util.SocketManager
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import android.net.Uri

class HomeActivity : BaseNavigationActivity() {

    private lateinit var viewModel: HomeViewModel
    private lateinit var postsContainer: LinearLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout

    private lateinit var notificationBadge: TextView
    private lateinit var bottomNav: View

    private val settingsRepository = SettingsRepository()
    private var maintenanceJob: Job? = null

    // Category Filter Variables
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
                Toast.makeText(this, "Post created! Refreshing feed...", Toast.LENGTH_SHORT).show()

                // Multiple refreshes to ensure post appears
                viewModel.forceRefreshPosts()

                Handler(Looper.getMainLooper()).postDelayed({
                    viewModel.forceRefreshPosts()
                }, 500)

                Handler(Looper.getMainLooper()).postDelayed({
                    viewModel.forceRefreshPosts()
                }, 1000)

                if (postCategory.isNotEmpty()) {
                    Handler(Looper.getMainLooper()).postDelayed({
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
                    }, 500)
                }

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

        WindowCompat.setDecorFitsSystemWindows(window, false)

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

        try {
            assets.open("like.json").use {
                Log.d("ANIMATION_TEST", "✅ like.json found in assets!")
            }
        } catch (e: Exception) {
            Log.e("ANIMATION_TEST", "❌ like.json NOT found! Check assets folder")
            Toast.makeText(this, "Animation file missing!", Toast.LENGTH_LONG).show()
        }


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

        setupBottomNavigationInsets()
        initCategoryFilters()

        val bigPlusButton = findViewById<LinearLayout>(R.id.big_plus_button)
        bigPlusButton?.setOnClickListener {
            createPostLauncher.launch(Intent(this, CreatePostActivity::class.java))
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (postsContainer.childCount > 0) {
                val firstPost = postsContainer.getChildAt(0)
                val testButton = firstPost.findViewById<com.example.pawsociety.widget.LikeButton>(R.id.btn_like_lottie_view)
                testButton?.let {
                    Log.d("LIKE_TEST", "Testing animation on first post")
                    it.setLiked(true, animate = true)
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

    private fun startMaintenanceChecker() {
        maintenanceJob?.cancel()

        maintenanceJob = lifecycleScope.launch {
            while (true) {
                try {
                    delay(30000)
                    println("🔍 Checking maintenance mode from HomeActivity...")
                    val isMaintenance = settingsRepository.isMaintenanceMode()
                    println("🚦 Maintenance mode from HomeActivity: $isMaintenance")

                    if (isMaintenance) {
                        println("🚧 Maintenance mode detected! Showing dialog...")
                        val message = settingsRepository.getMaintenanceMessage()

                        runOnUiThread {
                            try {
                                if (!isFinishing) {
                                    showMaintenanceDialog(message)
                                }
                            } catch (e: Exception) {
                                println("❌ Error showing dialog: ${e.message}")
                            }
                        }
                        break
                    }
                } catch (e: CancellationException) {
                    println("👋 Maintenance checker cancelled")
                    break
                } catch (e: Exception) {
                    println("❌ Error checking maintenance: ${e.message}")
                    e.printStackTrace()
                    delay(60000)
                }
            }
        }
    }

    private fun stopMaintenanceChecker() {
        maintenanceJob?.cancel()
        maintenanceJob = null
        println("🛑 Maintenance checker stopped")
    }

    private fun showMaintenanceDialog(message: String) {
        try {
            println("📱 Showing maintenance dialog with message: $message")

            val dialog = AlertDialog.Builder(this)
                .setTitle("Maintenance Mode")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("OK") { _, _ ->
                    println("✅ OK clicked, going to MaintenanceActivity")
                    val intent = Intent(this, MaintenanceActivity::class.java)
                    intent.putExtra("message", message)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
                .create()

            dialog.show()
            println("✅ Dialog shown successfully")
        } catch (e: Exception) {
            println("❌ Error creating dialog: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun refreshAllData() {
        viewModel.forceRefreshAndFilter(currentCategory)
        viewModel.loadCurrentUser()
        loadHiddenPosts()

        val currentUser = sessionManager.getCurrentUser()
        currentUser?.let {
            NotificationManager.startPolling(it.firebaseUid, this)
        }
    }

    private fun setupBottomNavigationInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
            val systemBarInsets = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout() or
                        WindowInsetsCompat.Type.ime()
            )

            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            view.updatePadding(
                bottom = navBarInsets.bottom
            )

            Log.d("Insets", "System bars bottom: ${systemBarInsets.bottom}")
            Log.d("Insets", "Nav bars bottom: ${navBarInsets.bottom}")

            insets
        }

        bottomNav.requestApplyInsets()
    }

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

    private fun setupCategoryClickListeners() {
        layoutAll.setOnClickListener {
            currentCategory = "All"
            highlightCategory("All")
            viewModel.forceRefreshAndFilter("All")
        }

        layoutDogs.setOnClickListener {
            currentCategory = "Dogs"
            highlightCategory("Dogs")
            viewModel.forceRefreshAndFilter("Dogs")
        }

        layoutCats.setOnClickListener {
            currentCategory = "Cats"
            highlightCategory("Cats")
            viewModel.forceRefreshAndFilter("Cats")
        }

        layoutFish.setOnClickListener {
            currentCategory = "Fish"
            highlightCategory("Fish")
            viewModel.forceRefreshAndFilter("Fish")
        }

        layoutBirds.setOnClickListener {
            currentCategory = "Birds"
            highlightCategory("Birds")
            viewModel.forceRefreshAndFilter("Birds")
        }
    }

    private fun highlightCategory(category: String) {
        allCircle.background = ContextCompat.getDrawable(this, R.drawable.circle_category_default)
        dogCircle.background = ContextCompat.getDrawable(this, R.drawable.circle_category_default)
        catCircle.background = ContextCompat.getDrawable(this, R.drawable.circle_category_default)
        fishCircle.background = ContextCompat.getDrawable(this, R.drawable.circle_category_default)
        birdCircle.background = ContextCompat.getDrawable(this, R.drawable.circle_category_default)

        textAll.setTextColor(Color.parseColor("#666666"))
        textDogs.setTextColor(Color.parseColor("#666666"))
        textCats.setTextColor(Color.parseColor("#666666"))
        textFish.setTextColor(Color.parseColor("#666666"))
        textBirds.setTextColor(Color.parseColor("#666666"))

        chipDogs.setColorFilter(Color.parseColor("#999999"))
        chipCats.setColorFilter(Color.parseColor("#999999"))
        chipFish.setColorFilter(Color.parseColor("#999999"))
        chipBirds.setColorFilter(Color.parseColor("#999999"))

        val allTextView = allCircle.getChildAt(0) as TextView
        allTextView.setTextColor(Color.parseColor("#7A4F2B"))

        when (category) {
            "All" -> {
                allCircle.background = ContextCompat.getDrawable(this, R.drawable.circle_category_selected)
                textAll.setTextColor(Color.parseColor("#7A4F2B"))
                allTextView.setTextColor(Color.parseColor("#7A4F2B"))
            }
            "Dogs" -> {
                dogCircle.background = ContextCompat.getDrawable(this, R.drawable.circle_category_selected)
                textDogs.setTextColor(Color.parseColor("#7A4F2B"))
                chipDogs.setColorFilter(Color.parseColor("#7A4F2B"))
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

    // 🔥 FIXED: Setup socket listeners with strict message filtering
    private fun setupSocketListeners() {
        // Listen for new posts only - IGNORE anything that looks like a message
        SocketManager.on("new-post") { args ->
            if (args.isNotEmpty()) {
                try {
                    val data = args[0] as? JSONObject
                    if (data != null) {
                        // 🔥 STRICT CHECK: Only process if it has petName (messages don't have this)
                        if (data.has("petName") && !data.optString("petName").isNullOrEmpty()) {
                            val post = parsePostFromJson(data)
                            runOnUiThread {
                                // Double-check this isn't a message
                                if (post.petName.isNotEmpty() &&
                                    !post.petName.contains("message", ignoreCase = true) &&
                                    !post.description.contains("message", ignoreCase = true)) {

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
                        } else {
                            Log.d("HomeActivity", "⚠️ Ignoring non-post socket event (missing petName)")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("HomeActivity", "Error parsing new post: ${e.message}")
                }
            }
        }

        // Add these socket listeners for real-time updates
        SocketManager.on("new-message") { args ->
            runOnUiThread {
                // Increment badge when new message arrives
                val currentCount = if (inboxBadge.visibility == View.VISIBLE) {
                    inboxBadge.text.toString().toIntOrNull() ?: 0
                } else {
                    0
                }
                updateInboxBadge(currentCount + 1)
            }
        }

        SocketManager.on("new-message-request") { args ->
            runOnUiThread {
                val currentCount = if (inboxBadge.visibility == View.VISIBLE) {
                    inboxBadge.text.toString().toIntOrNull() ?: 0
                } else {
                    0
                }
                updateInboxBadge(currentCount + 1)
            }
        }

        // Listen for profile updates
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

        // Listen for post likes
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

        // 🔥 IMPORTANT: We DO NOT listen for these message events:
        // - "new-message"
        // - "new-message-request"
        // - "chat-message"
        // This prevents messages from appearing in the Home feed
    }

    private fun updateLikeCountInFeed(postId: String?, likesCount: Int?, isLiked: Boolean? = null) {
        if (postId == null || likesCount == null) return

        for (i in 0 until postsContainer.childCount) {
            val postView = postsContainer.getChildAt(i)
            val viewPostId = postView.tag as? String

            if (viewPostId == postId) {
                val tvLikeCount = postView.findViewById<TextView>(R.id.tv_like_count)
                tvLikeCount?.text = likesCount.toString()

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
            age = json.optString("age", ""),
            weight = json.optString("weight", ""),
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

            // Add this inside setupObservers() function
            viewModel.unreadCount.observe(this) { count ->
                try {
                    val inboxBadge = findViewById<TextView>(R.id.inbox_badge)
                    if (inboxBadge != null) {
                        if (count > 0) {
                            inboxBadge.text = if (count > 9) "9+" else count.toString()
                            inboxBadge.visibility = View.VISIBLE

                            // Add a little pop animation
                            inboxBadge.animate()
                                .scaleX(1.3f)
                                .scaleY(1.3f)
                                .setDuration(200)
                                .withEndAction {
                                    inboxBadge.animate()
                                        .scaleX(1.0f)
                                        .scaleY(1.0f)
                                        .setDuration(100)
                                        .start()
                                }
                                .start()

                            Log.d("HomeActivity", "🔴 Inbox badge updated: $count")
                        } else {
                            inboxBadge.visibility = View.GONE
                            Log.d("HomeActivity", "⚪ Inbox badge hidden")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("HomeActivity", "Error updating inbox badge: ${e.message}")
                }
            }
        })

        viewModel.posts.observe(this, Observer { posts ->
            Log.d("HomeActivity", "📊 Received ${posts.size} posts for category: $currentCategory")

            val isLikeUpdate = posts.size == postsContainer.childCount &&
                    posts.all { post ->
                        postsContainer.findViewWithTag<View>(post.postId) != null
                    }

            if (isLikeUpdate) {
                updateLikeCountsOnly(posts)
            } else {
                // 🔥 FIXED: Clear and recreate ALL views when posts change
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

                    // 🔥 FIXED: Sort posts to show newest first (MOST RECENT AT TOP)
                    val sortedPosts = visiblePosts.sortedByDescending { it.createdAt }

                    Log.d("HomeActivity", "📊 Sorted posts - newest first:")
                    sortedPosts.forEachIndexed { index, post ->
                        Log.d("HomeActivity", "   [$index] ${post.petName} - Created: ${post.createdAt}")
                    }

                    // Create views for each post - adding in normal order will put newest at top
                    // because we're iterating from newest to oldest
                    for (post in sortedPosts) {
                        createPostView(post, currentLikeMap, currentFavMap)
                    }

                    postsContainer.invalidate()
                    postsContainer.requestLayout()

                    // Debug the final order
                    debugPostOrder()
                }
            }

            if (swipeRefreshLayout.isRefreshing) {
                swipeRefreshLayout.isRefreshing = false
            }
        })

        viewModel.likeStatus.observe(this, Observer { likeMap ->
            for (i in 0 until postsContainer.childCount) {
                val postView = postsContainer.getChildAt(i)
                val postId = postView.tag as? String ?: continue
                val isLiked = likeMap[postId] ?: false
                val likeButton = postView.findViewById<com.example.pawsociety.widget.LikeButton>(R.id.btn_like_lottie_view)
                if (likeButton?.isLiked != isLiked) {
                    likeButton?.setLiked(isLiked, animate = false)
                }
            }
        })

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

        // 🔥 FIXED: Sort posts to show newest first
        val sortedPosts = visiblePosts.sortedByDescending { it.createdAt }

        // Create views for each post
        for (post in sortedPosts) {
            createPostView(post, currentLikeMap, currentFavMap)
        }

        postsContainer.invalidate()
        postsContainer.requestLayout()
    }



    private fun createPostView(
        post: ApiPost,
        likeStatusMap: Map<String, Boolean>,
        favoriteStatusMap: Map<String, Boolean>
    ) {
        try {
            // 🔥 FIXED: Don't filter out posts based on age/weight being empty
            // Only block if it's definitely a message
            if (post.postId.startsWith("msg_")) {
                Log.w("HomeActivity", "⚠️ Skipping message: ${post.postId}")
                return
            }

            // If it has a pet name, it's a valid post - ALWAYS SHOW IT
            if (post.petName.isNullOrEmpty()) {
                Log.w("HomeActivity", "⚠️ Skipping post with no pet name: ${post.postId}")
                return
            }

            // Log that we're showing the post
            Log.d("HomeActivity", "✅ Showing post: ${post.petName} - ${post.postId}")

            val inflater = LayoutInflater.from(this)
            val postView = inflater.inflate(R.layout.item_post, postsContainer, false)

            postView.tag = post.postId

            // ===== FIND ALL VIEWS - WITH SAFE NULL CHECKS =====
            val profileIcon = postView.findViewById<TextView>(R.id.post_profile_icon)
            val profileImage = postView.findViewById<ImageView>(R.id.post_profile_image)
            val userNameText = postView.findViewById<TextView>(R.id.post_user_name)
            val locationText = postView.findViewById<TextView>(R.id.post_location)
            val postDate = postView.findViewById<TextView>(R.id.post_date)
            val btnMore = postView.findViewById<ImageView>(R.id.btn_more)
            val userRole = postView.findViewById<TextView>(R.id.post_user_role)  // ← I-ADD ITO

            val viewPager = postView.findViewById<ViewPager2>(R.id.post_view_pager)
            val singleImageView = postView.findViewById<ImageView>(R.id.post_image)
            val indicatorContainer = postView.findViewById<LinearLayout>(R.id.indicator_container)
            val noImagePlaceholder = postView.findViewById<LinearLayout>(R.id.no_image_placeholder)

            val statusBadge = postView.findViewById<TextView>(R.id.post_status)
            val rewardBadge = postView.findViewById<TextView>(R.id.post_reward_badge)

            // 🔥 IMPORTANT: These are now inside the new RelativeLayout
            val petTypeText = postView.findViewById<TextView>(R.id.post_pet_type)
            val categoryBadge = postView.findViewById<TextView>(R.id.post_category_badge)

            val petNameText = postView.findViewById<TextView>(R.id.post_pet_name)

            val genderIcon = postView.findViewById<ImageView>(R.id.post_gender_icon)
            val genderText = postView.findViewById<TextView>(R.id.post_gender_text)
            val postAge = postView.findViewById<TextView>(R.id.post_age)
            val postWeight = postView.findViewById<TextView>(R.id.post_weight)

            val descriptionText = postView.findViewById<TextView>(R.id.post_description)
            val btnMoreDescription = postView.findViewById<TextView>(R.id.btn_more_description)

            val rewardText = postView.findViewById<TextView>(R.id.post_reward)
            val rewardContainer = postView.findViewById<LinearLayout>(R.id.reward_container)

            val btnLikeGroup = postView.findViewById<LinearLayout>(R.id.btn_like_group)
            val likeButton = postView.findViewById<com.example.pawsociety.widget.LikeButton>(R.id.btn_like_lottie_view)
            val tvLikeCount = postView.findViewById<TextView>(R.id.tv_like_count)
            val btnMessageGroup = postView.findViewById<LinearLayout>(R.id.btn_message_group)
            val btnMessage = postView.findViewById<ImageView>(R.id.btn_message)
            val btnShareGroup = postView.findViewById<LinearLayout>(R.id.btn_share_group)
            val btnShare = postView.findViewById<ImageView>(R.id.btn_share)
            val btnFavoriteGroup = postView.findViewById<LinearLayout>(R.id.btn_favorite_group)
            val btnFavorite = postView.findViewById<ImageView>(R.id.btn_favorite)

            val inboxBadge = findViewById<TextView>(R.id.inbox_badge)

            // ===== MAKE PHONE NUMBER CLICKABLE =====
            val btnCallContainer = postView.findViewById<LinearLayout>(R.id.btn_call_container)
            val contactText = postView.findViewById<TextView>(R.id.post_contact)
            val ivCallIcon = postView.findViewById<ImageView>(R.id.iv_call_icon)

            // Extract digits from contact info
            val rawContact = post.contactInfo ?: ""
            val phoneDigits = rawContact.replace("[^0-9]".toRegex(), "")

            if (phoneDigits.isNotEmpty()) {
                btnCallContainer?.visibility = View.VISIBLE

                // 🔥 ILAGAY DITO - Para pantayin ang height
                // Set icon size only, remove minimumHeight
                ivCallIcon?.layoutParams?.height = 18.dp
                ivCallIcon?.layoutParams?.width = 18.dp
                ivCallIcon?.requestLayout()

                btnCallContainer?.setOnClickListener {
                    try {
                        val intent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:$phoneDigits")
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this@HomeActivity, "Cannot make call", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                btnCallContainer?.visibility = View.GONE  // Hide if no number
            }

            // When you have new messages, show the badge
            fun updateInboxBadge(count: Int) {
                if (count > 0) {
                    inboxBadge.text = if (count > 9) "9+" else count.toString()
                    inboxBadge.visibility = View.VISIBLE
                } else {
                    inboxBadge.visibility = View.GONE
                }
            }

            // ===== SET USER ROLE WITH PET NAME =====
            val petOwnerText = if (!post.petName.isNullOrEmpty()) {
                "${post.petName}'s Owner"
            } else {
                "Pet Owner"
            }
            userRole?.text = petOwnerText

            // ===== SET BASIC DATA WITH NULL SAFETY =====
            userNameText?.text = post.userName ?: "Unknown"
            locationText?.text = post.location ?: "Unknown location"
            postDate?.text = getTimeAgo(post.createdAt)
            petNameText?.text = post.petName ?: "Unnamed Pet"
            petTypeText?.text = post.petType ?: "Unknown breed"
            descriptionText?.text = post.description ?: ""
            tvLikeCount?.text = post.likesCount.toString()

            // Set contact text - numbers only, no emoji
            val cleanContact = post.contactInfo?.replace("[^0-9]".toRegex(), "") ?: ""
            contactText?.text = if (cleanContact.isNotEmpty()) cleanContact else "No contact"

            // ===== SET CATEGORY BADGE - FALLBACK VERSION =====
            try {
                if (categoryBadge != null) {
                    // First try to get from post.category
                    var category = when (post.category) {
                        "Dogs" -> "DOG"
                        "Cats" -> "CAT"
                        "Fish" -> "FISH"
                        "Birds" -> "BIRD"
                        else -> null
                    }

                    // If no category, try to detect from pet type
                    if (category == null) {
                        val petTypeLower = post.petType.lowercase()
                        category = when {
                            // DOG detection - including unknown/mixed
                            petTypeLower.contains("dog") ||
                                    petTypeLower.contains("aspin") ||
                                    petTypeLower.contains("shih") ||
                                    petTypeLower.contains("labrador") ||
                                    petTypeLower.contains("golden") ||
                                    petTypeLower.contains("german") ||
                                    petTypeLower.contains("poodle") ||
                                    petTypeLower.contains("chow") ||
                                    petTypeLower.contains("pug") ||
                                    petTypeLower.contains("beagle") ||
                                    petTypeLower.contains("dachshund") ||
                                    petTypeLower.contains("rottweiler") ||
                                    petTypeLower.contains("pomeranian") ||
                                    petTypeLower.contains("husky") ||
                                    petTypeLower.contains("corgi") ||
                                    petTypeLower.contains("maltese") ||
                                    petTypeLower.contains("chihuahua") ||
                                    petTypeLower.contains("pitbull") ||
                                    petTypeLower.contains("bulldog") ||
                                    petTypeLower.contains("boxer") ||
                                    petTypeLower.contains("shiba") ||
                                    petTypeLower.contains("akita") ||
                                    petTypeLower.contains("samoyed") ||
                                    petTypeLower.contains("cocker") ||
                                    petTypeLower.contains("doberman") ||
                                    petTypeLower.contains("great dane") ||
                                    petTypeLower.contains("saint bernard") ||
                                    petTypeLower.contains("siberian") ||
                                    petTypeLower.contains("jack russell") ||
                                    petTypeLower.contains("border collie") ||
                                    petTypeLower.contains("australian shepherd") ||
                                    petTypeLower.contains("bichon") ||
                                    petTypeLower.contains("unknown dog") ||  // ← Specific detection
                                    petTypeLower.contains("other dog") -> "DOG"

                            // CAT detection - including unknown/mixed
                            petTypeLower.contains("cat") ||
                                    petTypeLower.contains("puspin") ||
                                    petTypeLower.contains("persian") ||
                                    petTypeLower.contains("siamese") ||
                                    petTypeLower.contains("maine coon") ||
                                    petTypeLower.contains("bengal") ||
                                    petTypeLower.contains("sphynx") ||
                                    petTypeLower.contains("ragdoll") ||
                                    petTypeLower.contains("british shorthair") ||
                                    petTypeLower.contains("scottish fold") ||
                                    petTypeLower.contains("abyssinian") ||
                                    petTypeLower.contains("burmese") ||
                                    petTypeLower.contains("russian blue") ||
                                    petTypeLower.contains("norwegian forest") ||
                                    petTypeLower.contains("birman") ||
                                    petTypeLower.contains("oriental shorthair") ||
                                    petTypeLower.contains("devon rex") ||
                                    petTypeLower.contains("cornish rex") ||
                                    petTypeLower.contains("himalayan") ||
                                    petTypeLower.contains("american shorthair") ||
                                    petTypeLower.contains("exotic shorthair") ||
                                    petTypeLower.contains("unknown cat") ||  // ← Specific detection
                                    petTypeLower.contains("other cat") -> "CAT"

                            // FISH detection - including unknown/mixed
                            petTypeLower.contains("fish") ||
                                    petTypeLower.contains("goldfish") ||
                                    petTypeLower.contains("betta") ||
                                    petTypeLower.contains("guppy") ||
                                    petTypeLower.contains("molly") ||
                                    petTypeLower.contains("platy") ||
                                    petTypeLower.contains("swordtail") ||
                                    petTypeLower.contains("angelfish") ||
                                    petTypeLower.contains("discus") ||
                                    petTypeLower.contains("oscar") ||
                                    petTypeLower.contains("cichlid") ||
                                    petTypeLower.contains("koi") ||
                                    petTypeLower.contains("tetra") ||
                                    petTypeLower.contains("barb") ||
                                    petTypeLower.contains("corydoras") ||
                                    petTypeLower.contains("plecostomus") ||
                                    petTypeLower.contains("danio") ||
                                    petTypeLower.contains("rainbowfish") ||
                                    petTypeLower.contains("killifish") ||
                                    petTypeLower.contains("arowana") ||
                                    petTypeLower.contains("flowerhorn") ||
                                    petTypeLower.contains("parrot fish") ||
                                    petTypeLower.contains("gourami") ||
                                    petTypeLower.contains("unknown fish") ||  // ← Specific detection
                                    petTypeLower.contains("other fish") -> "FISH"

                            // BIRD detection - including unknown/mixed
                            petTypeLower.contains("bird") ||
                                    petTypeLower.contains("parrot") ||
                                    petTypeLower.contains("macaw") ||
                                    petTypeLower.contains("lovebird") ||
                                    petTypeLower.contains("parakeet") ||
                                    petTypeLower.contains("budgie") ||
                                    petTypeLower.contains("cockatiel") ||
                                    petTypeLower.contains("african grey") ||
                                    petTypeLower.contains("canary") ||
                                    petTypeLower.contains("finch") ||
                                    petTypeLower.contains("conure") ||
                                    petTypeLower.contains("amazon") ||
                                    petTypeLower.contains("eclectus") ||
                                    petTypeLower.contains("pigeon") ||
                                    petTypeLower.contains("dove") ||
                                    petTypeLower.contains("quaker") ||
                                    petTypeLower.contains("senegal") ||
                                    petTypeLower.contains("cockatoo") ||
                                    petTypeLower.contains("mynah") ||
                                    petTypeLower.contains("java sparrow") ||
                                    petTypeLower.contains("zebra finch") ||
                                    petTypeLower.contains("gouldian finch") ||
                                    petTypeLower.contains("ringneck") ||
                                    petTypeLower.contains("unknown bird") ||  // ← Specific detection
                                    petTypeLower.contains("other bird") -> "BIRD"

                            else -> null
                        }
                    }

                    if (category != null) {
                        categoryBadge.text = category
                        categoryBadge.visibility = View.VISIBLE

                        // Set background color and style based on category
                        when (category) {
                            "DOG" -> {
                                categoryBadge.setBackgroundResource(R.drawable.category_badge_rounded)
                                categoryBadge.background.setTint(Color.parseColor("#B88B4A"))
                                categoryBadge.setTextColor(Color.WHITE)
                            }
                            "CAT" -> {
                                categoryBadge.setBackgroundResource(R.drawable.category_badge_rounded)
                                categoryBadge.background.setTint(Color.parseColor("#FF9800"))
                                categoryBadge.setTextColor(Color.WHITE)
                            }
                            "FISH" -> {
                                categoryBadge.setBackgroundResource(R.drawable.category_badge_rounded)
                                categoryBadge.background.setTint(Color.parseColor("#00BCD4"))
                                categoryBadge.setTextColor(Color.WHITE)
                            }
                            "BIRD" -> {
                                categoryBadge.setBackgroundResource(R.drawable.category_badge_rounded)
                                categoryBadge.background.setTint(Color.parseColor("#2196F3"))
                                categoryBadge.setTextColor(Color.WHITE)
                            }
                        }

                        // Add padding using your working dp extension
                        categoryBadge.setPadding(12.dp, 4.dp, 12.dp, 4.dp)

                        Log.d("HomeActivity", "✅ Set category badge: $category for post: ${post.petName}")
                    } else {
                        categoryBadge.visibility = View.GONE
                        Log.d("HomeActivity", "ℹ️ No category detected for post: ${post.petName}")
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeActivity", "Error setting category badge", e)
            }

            // ===== SET GENDER =====
            when (post.gender?.lowercase()) {
                "male" -> {
                    genderText?.text = "Male"
                    genderText?.setTextColor(Color.parseColor("#2196F3"))
                    genderIcon?.visibility = View.GONE
                }
                "female" -> {
                    genderText?.text = "Female"
                    genderText?.setTextColor(Color.parseColor("#E91E63"))
                    genderIcon?.visibility = View.GONE
                }
                else -> {
                    genderText?.text = post.gender ?: "Unknown"
                    genderText?.setTextColor(Color.parseColor("#666666"))
                    genderIcon?.visibility = View.GONE
                }
            }

            // ===== SET PROFILE ICON =====
            val firstLetter = if (!post.userName.isNullOrEmpty()) {
                post.userName.first().toString().uppercase()
            } else {
                "?"
            }
            profileIcon?.text = firstLetter

            // ===== LOAD PROFILE IMAGE IF AVAILABLE =====
            if (!post.userImageUrl.isNullOrEmpty()) {
                try {
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
                } catch (e: Exception) {
                    Log.e("HomeActivity", "Error loading profile image: ${e.message}")
                    // Keep showing icon
                    profileIcon?.visibility = View.VISIBLE
                    profileImage?.visibility = View.GONE
                }
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

                        rewardText?.text = "₱$formattedReward"
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
                try {
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
                } catch (e: Exception) {
                    Log.e("HomeActivity", "Error loading post images: ${e.message}")
                    viewPager?.visibility = View.GONE
                    singleImageView?.visibility = View.GONE
                    noImagePlaceholder?.visibility = View.VISIBLE
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
            likeButton?.setLiked(isLiked, animate = false)

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

            btnMore?.setOnClickListener {
                showPostOptions(post, likeButton!!)
            }

            btnLikeGroup?.setOnClickListener {
                val user = currentUser
                if (user == null) {
                    Toast.makeText(this@HomeActivity, "Please login to like posts", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (btnLikeGroup?.isEnabled == false) return@setOnClickListener
                btnLikeGroup?.isEnabled = false

                val wasAlreadyLiked = likeButton?.isLiked ?: false

                if (likeButton != null) {
                    likeButton.setLiked(!wasAlreadyLiked, animate = true)
                }

                val currentCount = tvLikeCount?.text.toString().toIntOrNull() ?: 0
                tvLikeCount?.text = (if (!wasAlreadyLiked) currentCount + 1 else (currentCount - 1).coerceAtLeast(0)).toString()

                viewModel.toggleLike(post, wasAlreadyLiked)

                Handler(Looper.getMainLooper()).postDelayed({
                    btnLikeGroup?.isEnabled = true
                }, 1000)
            }

            btnShareGroup?.setOnClickListener {
                sharePost(post)
            }

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

            // 🔥 IMPORTANT: Add view at the END of container
            postsContainer.addView(postView)
            Log.d("HomeActivity", "✅ Added post to feed: ${post.petName}")

        } catch (e: Exception) {
            Log.e("HomeActivity", "❌ CRITICAL ERROR creating post view: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun debugPostOrder() {
        Log.d("HomeActivity", "📋 FINAL POST ORDER IN FEED (top to bottom):")
        for (i in 0 until postsContainer.childCount) {
            val postView = postsContainer.getChildAt(i)
            val postId = postView.tag as? String ?: "unknown"
            val petName = postView.findViewById<TextView>(R.id.post_pet_name)?.text ?: "unknown"
            Log.d("HomeActivity", "   Position $i: $petName - $postId")
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

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

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

    override fun onResume() {
        super.onResume()
        startMaintenanceChecker()

        viewModel.forceRefreshAndFilter(currentCategory)
        viewModel.loadCurrentUser()
        viewModel.loadInboxCounts() // 🔥 ADD THIS LINE
        loadHiddenPosts()

        lifecycleScope.launch {
            try {
                println("🔍 Force checking maintenance mode on resume...")
                val isMaintenance = settingsRepository.isMaintenanceMode()
                println("🚦 Maintenance mode on resume: $isMaintenance")

                if (isMaintenance) {
                    val message = settingsRepository.getMaintenanceMessage()
                    runOnUiThread {
                        showMaintenanceDialog(message)
                    }
                }
            } catch (e: Exception) {
                println("❌ Error checking maintenance on resume: ${e.message}")
            }
        }
    }



    override fun onPause() {
        super.onPause()
        stopMaintenanceChecker()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMaintenanceChecker()
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