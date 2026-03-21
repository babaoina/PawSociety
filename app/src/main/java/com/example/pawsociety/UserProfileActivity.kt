package com.example.pawsociety

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.pawsociety.api.ApiPost
import com.example.pawsociety.api.ApiUser
import com.example.pawsociety.data.repository.FollowRepository
import com.example.pawsociety.data.repository.PostRepository
import com.example.pawsociety.data.repository.ReportRepository  // 🔥 ADDED
import com.example.pawsociety.data.repository.UserRepository
import com.example.pawsociety.util.SessionManager
import kotlinx.coroutines.launch
import java.util.*

class UserProfileActivity : AppCompatActivity() {

    private lateinit var tvUsername: TextView
    private lateinit var btnBack: ImageView
    private lateinit var btnMenu: ImageView  // 🔥 ADDED
    private lateinit var profileImage: ImageView
    private lateinit var profileInitial: TextView
    private lateinit var profileBackground: View
    private lateinit var tvPostCount: TextView
    private lateinit var tvFollowerCount: TextView
    private lateinit var tvFollowingCount: TextView
    private lateinit var btnFollow: TextView
    private lateinit var btnMessage: TextView
    private lateinit var tvBio: TextView
    private lateinit var tvFullName: TextView
    private lateinit var tvUsernameBio: TextView
    private lateinit var tvLocationBio: TextView
    private lateinit var postsContainer: FrameLayout
    private lateinit var postsGrid: LinearLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var highlightsContainer: LinearLayout
    private lateinit var highlightsScroll: HorizontalScrollView
    private lateinit var scrollView: ScrollView

    private lateinit var sessionManager: SessionManager
    private val userRepository = UserRepository()
    private val postRepository = PostRepository()
    private lateinit var followRepository: FollowRepository
    private val reportRepository = ReportRepository()  // 🔥 ADDED

    private var targetUser: ApiUser? = null
    private var currentUser: ApiUser? = null
    private var isFollowing = false
    private var targetUserId: String = ""
    private var targetUserName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        sessionManager = SessionManager(this)
        currentUser = sessionManager.getCurrentUser()
        followRepository = FollowRepository()

        targetUserId = intent.getStringExtra("userId") ?: ""
        targetUserName = intent.getStringExtra("userName") ?: "User"

        println("👤 UserProfileActivity - Viewing profile: $targetUserName (UID: $targetUserId)")

        if (targetUserId.isEmpty()) {
            Toast.makeText(this, "User ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (targetUserId == currentUser?.firebaseUid) {
            startActivity(Intent(this, ProfileActivity::class.java))
            finish()
            return
        }

        initializeViews()
        setupClickListeners()
        loadUserData()
    }

    private fun initializeViews() {
        tvUsername = findViewById(R.id.tv_username)
        btnBack = findViewById(R.id.btn_back)
        btnMenu = findViewById(R.id.btn_menu)  // 🔥 ADDED
        profileImage = findViewById(R.id.profile_image)
        profileInitial = findViewById(R.id.profile_initial)
        profileBackground = findViewById(R.id.profile_circle_background)
        tvPostCount = findViewById(R.id.tv_post_count)
        tvFollowerCount = findViewById(R.id.tv_follower_count)
        tvFollowingCount = findViewById(R.id.tv_following_count)
        btnFollow = findViewById(R.id.btn_follow)
        btnMessage = findViewById(R.id.btn_message)
        tvBio = findViewById(R.id.tv_bio)
        tvFullName = findViewById(R.id.tv_full_name)
        tvUsernameBio = findViewById(R.id.tv_username_bio)
        tvLocationBio = findViewById(R.id.tv_location_bio)
        postsContainer = findViewById(R.id.posts_container)
        postsGrid = findViewById(R.id.posts_grid)
        emptyState = findViewById(R.id.empty_state)
        progressBar = findViewById(R.id.progress_bar)
        highlightsContainer = findViewById(R.id.highlights_container)
        highlightsScroll = findViewById(R.id.highlights_scroll)
        scrollView = findViewById(R.id.profile_scroll_view)

        tvUsername.text = targetUserName
        emptyState.visibility = View.GONE

        // 🔥 ADDED: Hide menu by default, will show if not current user
        btnMenu.visibility = View.GONE
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        // 🔥 ADDED: Menu button click listener
        btnMenu.setOnClickListener {
            showUserOptions()
        }

        btnFollow.setOnClickListener {
            if (currentUser == null) {
                Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isFollowing) {
                unfollowUser()
            } else {
                followUser()
            }
        }

        btnMessage.setOnClickListener {
            if (currentUser == null) {
                Toast.makeText(this, "Please login to send messages", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            targetUser?.let { user ->
                val intent = Intent(this, ChatActivity::class.java)
                intent.putExtra("receiverUid", user.firebaseUid)
                intent.putExtra("receiverUsername", user.username)
                startActivity(intent)
            }
        }

        findViewById<LinearLayout>(R.id.layout_posts)?.setOnClickListener {
            scrollView.post {
                scrollView.smoothScrollTo(0, postsContainer.top)
            }
        }

        findViewById<LinearLayout>(R.id.layout_followers)?.setOnClickListener {
            val intent = Intent(this, FollowersFollowingActivity::class.java)
            intent.putExtra("userId", targetUserId)
            intent.putExtra("userName", targetUserName)
            intent.putExtra("mode", "followers")
            startActivity(intent)
        }

        findViewById<LinearLayout>(R.id.layout_following)?.setOnClickListener {
            val intent = Intent(this, FollowersFollowingActivity::class.java)
            intent.putExtra("userId", targetUserId)
            intent.putExtra("userName", targetUserName)
            intent.putExtra("mode", "following")
            startActivity(intent)
        }
    }

    // 🔥 ADDED: Show user options menu
    private fun showUserOptions() {
        val options = arrayOf("Report User", "Cancel")

        AlertDialog.Builder(this)
            .setTitle("More Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showReportDialog()
                }
            }
            .show()
    }

    // 🔥 ADDED: Show report reasons dialog
    private fun showReportDialog() {
        val reportReasons = arrayOf(
            "Harassment",
            "Fake Account",
            "Inappropriate Profile",
            "Spam",
            "Impersonation",
            "Other"
        )

        AlertDialog.Builder(this)
            .setTitle("Report ${targetUserName}")
            .setItems(reportReasons) { _, which ->
                val reason = reportReasons[which].lowercase(Locale.getDefault()).replace(" ", "_")
                submitUserReport(reason)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // 🔥 ADDED: Submit report to admin backend
    private fun submitUserReport(reason: String) {
        val currentUser = currentUser ?: run {
            Toast.makeText(this, "Please login to report", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                Toast.makeText(this@UserProfileActivity, "Submitting report...", Toast.LENGTH_SHORT).show()

                val result = reportRepository.createReport(
                    reporterUid = currentUser.firebaseUid,
                    reason = reason,
                    reportedUid = targetUserId,
                    description = "Reported from user profile"
                )

                if (result.isSuccess) {
                    Toast.makeText(
                        this@UserProfileActivity,
                        "✅ User reported. Admin will review.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Failed to submit report"
                    Toast.makeText(this@UserProfileActivity, "❌ $error", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@UserProfileActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadUserData() {
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val userResult = userRepository.getUserByUid(targetUserId)

                if (userResult.isSuccess) {
                    targetUser = userResult.getOrNull()
                    updateUserUI()

                    checkFollowStatus()
                    loadFollowersCount()
                    loadUserPosts(targetUserId)
                    loadUserHighlights()
                } else {
                    Toast.makeText(this@UserProfileActivity, "User not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@UserProfileActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun updateUserUI() {
        targetUser?.let { user ->
            tvUsername.text = user.username

            // Set profile picture
            if (!user.profileImageUrl.isNullOrEmpty()) {
                val fullImageUrl = if (user.profileImageUrl.startsWith("http")) {
                    user.profileImageUrl
                } else {
                    "${com.example.pawsociety.api.ApiClient.FULL_BASE_URL}${user.profileImageUrl}"
                }

                profileImage.visibility = View.VISIBLE
                profileInitial.visibility = View.GONE

                Glide.with(this)
                    .load(fullImageUrl)
                    .circleCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_report_image)
                    .into(profileImage)
            } else {
                profileImage.visibility = View.GONE
                profileInitial.visibility = View.VISIBLE

                val color = generateColorFromUsername(user.username)
                val backgroundDrawable = ContextCompat.getDrawable(this, R.drawable.circle_solid_profile)
                backgroundDrawable?.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
                profileBackground.background = backgroundDrawable

                val firstLetter = if (user.username.isNotEmpty()) {
                    user.username.first().toString().uppercase(Locale.getDefault())
                } else {
                    "?"
                }
                profileInitial.text = firstLetter
            }

            // Set Instagram-style bio
            tvFullName.visibility = View.VISIBLE
            tvFullName.text = user.fullName

            tvUsernameBio.visibility = View.VISIBLE
            tvUsernameBio.text = "@${user.username}"

            tvBio.text = user.bio ?: ""

            if (!user.location.isNullOrEmpty()) {
                tvLocationBio.visibility = View.VISIBLE
                tvLocationBio.text = user.location
            } else {
                tvLocationBio.visibility = View.GONE
            }

            // 🔥 ADDED: Show menu button only for OTHER users (not current user)
            if (targetUserId != currentUser?.firebaseUid) {
                btnMenu.visibility = View.VISIBLE
            } else {
                btnMenu.visibility = View.GONE
            }
        }
    }

    private fun generateColorFromUsername(username: String): Int {
        val colors = listOf(
            "#FF6B35", "#4CAF50", "#2196F3", "#9C27B0",
            "#F44336", "#009688", "#FF9800", "#3F51B5"
        )
        val hash = Math.abs(username.hashCode())
        val index = hash % colors.size
        return Color.parseColor(colors[index])
    }

    private fun checkFollowStatus() {
        lifecycleScope.launch {
            try {
                val currentUser = currentUser ?: return@launch
                val result = followRepository.checkFollowStatus(currentUser.firebaseUid, targetUserId)

                if (result.isSuccess) {
                    isFollowing = result.getOrNull() ?: false
                    updateFollowButton()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun followUser() {
        lifecycleScope.launch {
            try {
                btnFollow.isEnabled = false
                btnFollow.text = "Following..."

                val currentUser = currentUser ?: return@launch
                val result = followRepository.followUser(currentUser.firebaseUid, targetUserId)

                if (result.isSuccess) {
                    isFollowing = true
                    updateFollowButton()
                    Toast.makeText(this@UserProfileActivity, "Following ${targetUser?.username}", Toast.LENGTH_SHORT).show()
                    loadFollowersCount()
                } else {
                    Toast.makeText(this@UserProfileActivity, "Failed to follow user", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@UserProfileActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                btnFollow.isEnabled = true
            }
        }
    }

    private fun unfollowUser() {
        lifecycleScope.launch {
            try {
                btnFollow.isEnabled = false
                btnFollow.text = "Unfollowing..."

                val currentUser = currentUser ?: return@launch
                val result = followRepository.unfollowUser(currentUser.firebaseUid, targetUserId)

                if (result.isSuccess) {
                    isFollowing = false
                    updateFollowButton()
                    Toast.makeText(this@UserProfileActivity, "Unfollowed ${targetUser?.username}", Toast.LENGTH_SHORT).show()
                    loadFollowersCount()
                } else {
                    Toast.makeText(this@UserProfileActivity, "Failed to unfollow user", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@UserProfileActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                btnFollow.isEnabled = true
            }
        }
    }

    private fun loadFollowersCount() {
        lifecycleScope.launch {
            try {
                val result = followRepository.getFollowersCount(targetUserId)
                if (result.isSuccess) {
                    tvFollowerCount.text = result.getOrNull().toString()
                }

                val followingResult = followRepository.getFollowingCount(targetUserId)
                if (followingResult.isSuccess) {
                    tvFollowingCount.text = followingResult.getOrNull().toString()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateFollowButton() {
        if (isFollowing) {
            btnFollow.text = "Following"
            btnFollow.setBackgroundResource(R.drawable.button_oval_white_border)
            btnFollow.setTextColor(Color.parseColor("#000000"))
        } else {
            btnFollow.text = "Follow"
            btnFollow.setBackgroundResource(R.drawable.button_oval_brown)
            btnFollow.setTextColor(Color.WHITE)
        }

        // Force layout refresh to keep both buttons same height
        btnFollow.post {
            btnFollow.requestLayout()
            btnMessage.requestLayout()
        }
    }

    private fun loadUserPosts(userId: String) {
        lifecycleScope.launch {
            try {
                val result = postRepository.getPosts(firebaseUid = userId)

                if (result.isSuccess) {
                    val posts = result.getOrNull()!!

                    if (posts.isNotEmpty()) {
                        postsGrid.removeAllViews()
                        postsGrid.visibility = View.VISIBLE
                        emptyState.visibility = View.GONE
                        createPostsGrid(posts)
                        tvPostCount.text = posts.size.toString()
                    } else {
                        tvPostCount.text = "0"
                        showEmptyState()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                showEmptyState()
            }
        }
    }

    private fun createPostsGrid(posts: List<ApiPost>) {
        try {
            postsGrid.removeAllViews()

            val colors = listOf(
                "#4CAF50", "#2196F3", "#FF9800",
                "#9C27B0", "#FF5722", "#00BCD4",
                "#E91E63", "#3F51B5", "#009688"
            )

            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val spacing = 2
            val columns = 3

            val itemSize = (screenWidth - (spacing * (columns - 1))) / columns

            val rows = (posts.size + columns - 1) / columns
            var postIndex = 0

            for (row in 0 until rows) {
                val rowLayout = LinearLayout(this)
                rowLayout.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                rowLayout.orientation = LinearLayout.HORIZONTAL

                for (col in 0 until columns) {
                    if (postIndex >= posts.size) {
                        val emptyContainer = FrameLayout(this)
                        val emptyParams = LinearLayout.LayoutParams(
                            itemSize,
                            itemSize
                        )
                        if (col > 0) {
                            emptyParams.leftMargin = spacing
                        }
                        emptyContainer.layoutParams = emptyParams
                        rowLayout.addView(emptyContainer)
                        continue
                    }

                    val squareContainer = FrameLayout(this)
                    val squareParams = LinearLayout.LayoutParams(
                        itemSize,
                        itemSize
                    )

                    if (col > 0) {
                        squareParams.leftMargin = spacing
                    }

                    squareContainer.layoutParams = squareParams

                    val post = posts[postIndex]
                    val postView = layoutInflater.inflate(R.layout.item_profile_post, squareContainer, false)
                    val postContent = postView.findViewById<TextView>(R.id.post_content)
                    val postImage = postView.findViewById<ImageView>(R.id.post_image)

                    // 🔥 Category badge
                    val categoryBadge = postView.findViewById<TextView>(R.id.category_badge)

                    // 🔥 Status badge
                    val statusBadge = postView.findViewById<TextView>(R.id.status_badge)

                    // ===== DETECT CATEGORY FOR BADGE =====
                    var category = when (post.category) {
                        "Dogs" -> "DOG"
                        "Cats" -> "CAT"
                        "Fish" -> "FISH"
                        "Birds" -> "BIRD"
                        else -> null
                    }

                    if (category == null && post.petType != null) {
                        val petTypeLower = post.petType.lowercase(Locale.getDefault())
                        category = when {
                            petTypeLower.contains("dog") ||
                                    petTypeLower.contains("aspin") -> "DOG"

                            petTypeLower.contains("cat") ||
                                    petTypeLower.contains("puspin") -> "CAT"

                            petTypeLower.contains("fish") ||
                                    petTypeLower.contains("goldfish") -> "FISH"

                            petTypeLower.contains("bird") ||
                                    petTypeLower.contains("parrot") -> "BIRD"

                            else -> null
                        }
                    }

                    // Set category badge if detected
                    if (category != null && categoryBadge != null) {
                        categoryBadge.text = category
                        categoryBadge.visibility = View.VISIBLE

                        when (category) {
                            "DOG" -> {
                                categoryBadge.setBackgroundResource(R.drawable.category_badge_rounded)
                                categoryBadge.background.setTint(Color.parseColor("#B88B4A"))
                            }
                            "CAT" -> {
                                categoryBadge.setBackgroundResource(R.drawable.category_badge_rounded)
                                categoryBadge.background.setTint(Color.parseColor("#FF9800"))
                            }
                            "FISH" -> {
                                categoryBadge.setBackgroundResource(R.drawable.category_badge_rounded)
                                categoryBadge.background.setTint(Color.parseColor("#00BCD4"))
                            }
                            "BIRD" -> {
                                categoryBadge.setBackgroundResource(R.drawable.category_badge_rounded)
                                categoryBadge.background.setTint(Color.parseColor("#2196F3"))
                            }
                        }
                        categoryBadge.setTextColor(Color.WHITE)
                    }

                    // ===== SET STATUS BADGE =====
                    if (statusBadge != null) {
                        when (post.status.lowercase(Locale.getDefault())) {
                            "lost" -> {
                                statusBadge.text = "LOST"
                                statusBadge.setBackgroundResource(R.drawable.status_badge_oval)
                                statusBadge.background.setTint(Color.parseColor("#F44336"))
                                statusBadge.visibility = View.VISIBLE
                            }
                            "found" -> {
                                statusBadge.text = "FOUND"
                                statusBadge.setBackgroundResource(R.drawable.status_badge_oval)
                                statusBadge.background.setTint(Color.parseColor("#4CAF50"))
                                statusBadge.visibility = View.VISIBLE
                            }
                            "adoption" -> {
                                statusBadge.text = "ADOPTION"
                                statusBadge.setBackgroundResource(R.drawable.status_badge_oval)
                                statusBadge.background.setTint(Color.parseColor("#2196F3"))
                                statusBadge.visibility = View.VISIBLE
                            }
                            else -> {
                                statusBadge.visibility = View.GONE
                            }
                        }
                        statusBadge.setTextColor(Color.WHITE)
                    }

                    if (!post.imageUrls.isNullOrEmpty() && post.imageUrls.isNotEmpty()) {
                        val imageUrl = post.imageUrls[0]
                        val fullImageUrl = if (imageUrl.startsWith("http")) {
                            imageUrl
                        } else {
                            "${com.example.pawsociety.api.ApiClient.FULL_BASE_URL}$imageUrl"
                        }

                        postImage.visibility = View.VISIBLE
                        postContent.visibility = View.GONE

                        Glide.with(this)
                            .load(fullImageUrl)
                            .centerCrop()
                            .override(itemSize, itemSize)
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .error(android.R.drawable.ic_menu_report_image)
                            .into(postImage)
                    } else {
                        postImage.visibility = View.GONE
                        postContent.visibility = View.VISIBLE

                        val emoji = when {
                            post.petType.contains("dog", ignoreCase = true) -> "🐶"
                            post.petType.contains("cat", ignoreCase = true) -> "🐱"
                            post.petType.contains("bird", ignoreCase = true) -> "🐦"
                            post.petType.contains("rabbit", ignoreCase = true) -> "🐰"
                            post.petType.contains("fish", ignoreCase = true) -> "🐟"
                            else -> "🐾"
                        }

                        postContent.text = "$emoji\n${post.petName}"
                        postContent.setBackgroundColor(Color.parseColor(colors[postIndex % colors.size]))
                        postContent.setTextColor(Color.WHITE)
                        postContent.textSize = 14f
                    }

                    val currentPosition = postIndex
                    postView.setOnClickListener {
                        val intent = Intent(this@UserProfileActivity, PostViewActivity::class.java)
                        intent.putExtra("all_posts", ArrayList(posts))
                        intent.putExtra("position", currentPosition)
                        startActivity(intent)
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    }

                    squareContainer.addView(postView)
                    rowLayout.addView(squareContainer)
                    postIndex++
                }

                if (row < rows - 1) {
                    val rowParams = rowLayout.layoutParams as LinearLayout.LayoutParams
                    rowParams.bottomMargin = spacing
                    rowLayout.layoutParams = rowParams
                }

                postsGrid.addView(rowLayout)
            }
        } catch (e: Exception) {
            println("❌ Error in createPostsGrid: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun loadUserHighlights() {
        highlightsContainer.removeAllViews()

        lifecycleScope.launch {
            try {
                val result = postRepository.getPosts(firebaseUid = targetUserId, limit = 10)

                if (result.isSuccess) {
                    val posts = result.getOrNull() ?: emptyList()

                    if (posts.isEmpty()) {
                        highlightsScroll.visibility = View.GONE
                        findViewById<TextView>(R.id.tv_highlights_title).visibility = View.GONE
                    } else {
                        findViewById<TextView>(R.id.tv_highlights_title).visibility = View.VISIBLE
                        highlightsScroll.visibility = View.VISIBLE
                        val highlights = posts.take(5)
                        for (post in highlights) {
                            addHighlightView(post)
                        }
                    }
                } else {
                    highlightsScroll.visibility = View.GONE
                    findViewById<TextView>(R.id.tv_highlights_title).visibility = View.GONE
                }
            } catch (e: Exception) {
                e.printStackTrace()
                highlightsScroll.visibility = View.GONE
                findViewById<TextView>(R.id.tv_highlights_title).visibility = View.GONE
            }
        }
    }

    private fun addHighlightView(post: ApiPost) {
        val highlightView = layoutInflater.inflate(R.layout.item_highlight, highlightsContainer, false)

        val circleBackground = highlightView.findViewById<View>(R.id.highlight_circle_background)
        val circleText = highlightView.findViewById<TextView>(R.id.highlight_circle_text)
        val titleText = highlightView.findViewById<TextView>(R.id.highlight_title)

        val emoji = when {
            post.petType.contains("dog", ignoreCase = true) -> "🐶"
            post.petType.contains("cat", ignoreCase = true) -> "🐱"
            post.petType.contains("bird", ignoreCase = true) -> "🐦"
            post.petType.contains("rabbit", ignoreCase = true) -> "🐰"
            post.petType.contains("fish", ignoreCase = true) -> "🐟"
            else -> "🐾"
        }

        val colors = listOf("#FF6B35", "#4CAF50", "#2196F3", "#9C27B0", "#F44336")
        val colorIndex = Math.abs(post.postId.hashCode()) % colors.size

        val backgroundDrawable = ContextCompat.getDrawable(this, R.drawable.circle_solid_highlight)
        backgroundDrawable?.setColorFilter(Color.parseColor(colors[colorIndex]), PorterDuff.Mode.SRC_ATOP)
        circleBackground.background = backgroundDrawable

        circleText.text = emoji
        titleText.text = post.petName

        highlightView.setOnClickListener {
            showPostPreview(post)
        }

        highlightsContainer.addView(highlightView)
    }

    private fun showPostPreview(post: ApiPost) {
        val message = """
            ${post.petName}
            Status: ${post.status}
            Location: ${post.location}
            Posted by: ${post.userName}
        """.trimIndent()

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showEmptyState() {
        postsGrid.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        val emptyText = emptyState.findViewById<TextView>(R.id.empty_text)
        emptyText.text = "No posts yet"
    }

    override fun onResume() {
        super.onResume()
        if (::followRepository.isInitialized) {
            checkFollowStatus()
            loadFollowersCount()
        }
    }
}