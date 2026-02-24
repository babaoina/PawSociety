package com.example.pawsociety

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
import com.example.pawsociety.data.repository.PostRepository
import com.example.pawsociety.data.repository.UserRepository
import com.example.pawsociety.util.SessionManager
import kotlinx.coroutines.launch

class UserProfileActivity : AppCompatActivity() {

    private lateinit var tvUsername: TextView
    private lateinit var btnBack: ImageView
    private lateinit var profileImage: ImageView
    private lateinit var profileInitial: TextView
    private lateinit var profileBackground: View
    private lateinit var tvPostCount: TextView
    private lateinit var tvFollowerCount: TextView
    private lateinit var tvFollowingCount: TextView
    private lateinit var btnFollow: TextView
    private lateinit var btnMessage: TextView
    private lateinit var tvBio: TextView
    private lateinit var postsContainer: FrameLayout
    private lateinit var postsGrid: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var highlightsContainer: LinearLayout
    private lateinit var highlightsScroll: HorizontalScrollView
    private lateinit var scrollView: ScrollView

    private lateinit var sessionManager: SessionManager
    private val userRepository = UserRepository()
    private val postRepository = PostRepository()

    private var targetUser: ApiUser? = null
    private var currentUser: ApiUser? = null
    private var isFollowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile)

        sessionManager = SessionManager(this)
        currentUser = sessionManager.getCurrentUser()

        // Get target user data from intent
        val userId = intent.getStringExtra("userId") ?: ""
        val userName = intent.getStringExtra("userName") ?: "User"

        if (userId.isEmpty() || userId == currentUser?.firebaseUid) {
            // If it's current user, go to ProfileActivity
            startActivity(Intent(this, ProfileActivity::class.java))
            finish()
            return
        }

        initializeViews()
        setupClickListeners()
        loadUserData(userId)
    }

    private fun initializeViews() {
        tvUsername = findViewById(R.id.tv_username)
        btnBack = findViewById(R.id.btn_back)
        profileImage = findViewById(R.id.profile_image)
        profileInitial = findViewById(R.id.profile_initial)
        profileBackground = findViewById(R.id.profile_circle_background)
        tvPostCount = findViewById(R.id.tv_post_count)
        tvFollowerCount = findViewById(R.id.tv_follower_count)
        tvFollowingCount = findViewById(R.id.tv_following_count)
        btnFollow = findViewById(R.id.btn_follow)
        btnMessage = findViewById(R.id.btn_message)
        tvBio = findViewById(R.id.tv_bio)
        postsContainer = findViewById(R.id.posts_container)
        postsGrid = findViewById(R.id.posts_grid)
        progressBar = findViewById(R.id.progress_bar)
        highlightsContainer = findViewById(R.id.highlights_container)
        highlightsScroll = findViewById(R.id.highlights_scroll)
        scrollView = findViewById(R.id.profile_scroll_view)
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
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

        // Message button click
        btnMessage.setOnClickListener {
            if (currentUser == null) {
                Toast.makeText(this, "Please login to send messages", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            targetUser?.let { user ->
                val intent = Intent(this, ChatActivity::class.java)
                intent.putExtra("receiverUid", user.firebaseUid)
                intent.putExtra("receiverUsername", user.username)
                intent.putExtra("receiverProfileImage", user.profileImageUrl ?: "")
                startActivity(intent)
            }
        }

        // Posts click - scroll to grid
        findViewById<LinearLayout>(R.id.layout_posts)?.setOnClickListener {
            scrollView.post {
                scrollView.smoothScrollTo(0, postsContainer.top)
            }
        }

        // Followers click
        findViewById<LinearLayout>(R.id.layout_followers)?.setOnClickListener {
            val intent = Intent(this, FollowersFollowingActivity::class.java)
            intent.putExtra("userId", targetUser?.firebaseUid ?: return@setOnClickListener)
            intent.putExtra("userName", targetUser?.username ?: "User")
            intent.putExtra("mode", "followers")
            startActivity(intent)
        }

        // Following click
        findViewById<LinearLayout>(R.id.layout_following)?.setOnClickListener {
            val intent = Intent(this, FollowersFollowingActivity::class.java)
            intent.putExtra("userId", targetUser?.firebaseUid ?: return@setOnClickListener)
            intent.putExtra("userName", targetUser?.username ?: "User")
            intent.putExtra("mode", "following")
            startActivity(intent)
        }

        // Tab clicks
        findViewById<LinearLayout>(R.id.tab_posts).setOnClickListener {
            // Scroll to posts grid
            scrollView.post {
                scrollView.smoothScrollTo(0, postsContainer.top)
            }
        }

        findViewById<LinearLayout>(R.id.tab_favorites).setOnClickListener {
            Toast.makeText(this, "No favorites to show", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadUserData(userId: String) {
        progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                // Load user data
                val userResult = userRepository.getUserByUid(userId)

                if (userResult.isSuccess) {
                    targetUser = userResult.getOrNull()
                    updateUserUI()

                    // Check if current user follows this user
                    checkFollowStatus()

                    // Load user's posts
                    loadUserPosts(userId)

                    // Load user's highlights
                    loadUserHighlights(userId)
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

    private fun loadUserHighlights(userId: String) {
        highlightsContainer.removeAllViews()

        lifecycleScope.launch {
            try {
                // Get user's posts to use as highlights
                val result = postRepository.getPosts(firebaseUid = userId, limit = 10)

                if (result.isSuccess) {
                    val posts = result.getOrNull() ?: emptyList()

                    if (posts.isEmpty()) {
                        // Hide highlights section if no posts
                        highlightsScroll.visibility = View.GONE
                    } else {
                        highlightsScroll.visibility = View.VISIBLE

                        // Show first 5 posts as highlights
                        val highlights = posts.take(5)
                        for (post in highlights) {
                            addHighlightView(highlightsContainer, post)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                highlightsScroll.visibility = View.GONE
            }
        }
    }

    private fun addHighlightView(container: LinearLayout, post: ApiPost) {
        val highlightView = layoutInflater.inflate(R.layout.item_highlight, container, false)

        val circleBackground = highlightView.findViewById<View>(R.id.highlight_circle_background)
        val circleText = highlightView.findViewById<TextView>(R.id.highlight_circle_text)
        val titleText = highlightView.findViewById<TextView>(R.id.highlight_title)

        // Set highlight based on post data
        val emoji = when {
            post.petType.contains("dog", ignoreCase = true) -> "🐶"
            post.petType.contains("cat", ignoreCase = true) -> "🐱"
            post.petType.contains("bird", ignoreCase = true) -> "🐦"
            post.petType.contains("rabbit", ignoreCase = true) -> "🐰"
            post.petType.contains("fish", ignoreCase = true) -> "🐟"
            else -> "🐾"
        }

        // Generate color based on post ID
        val colors = listOf("#FF6B35", "#4CAF50", "#2196F3", "#9C27B0", "#F44336")
        val colorIndex = Math.abs(post.postId.hashCode()) % colors.size

        val backgroundDrawable = ContextCompat.getDrawable(this, R.drawable.circle_solid_highlight)
        backgroundDrawable?.setColorFilter(Color.parseColor(colors[colorIndex]), PorterDuff.Mode.SRC_ATOP)
        circleBackground.background = backgroundDrawable

        circleText.text = emoji
        titleText.text = post.petName

        highlightView.setOnClickListener {
            // Show highlight details or open the post
            showPostPreview(post)
        }

        container.addView(highlightView)
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
                    user.username.first().toString().uppercase()
                } else {
                    "?"
                }
                profileInitial.text = firstLetter
            }

            // Set bio
            val location = user.location ?: ""
            val bio = user.bio ?: ""

            tvBio.text = when {
                location.isNotEmpty() && bio.isNotEmpty() -> "$location\n$bio"
                bio.isNotEmpty() -> bio
                location.isNotEmpty() -> location
                else -> "No bio yet"
            }
        }
    }

    private fun checkFollowStatus() {
        lifecycleScope.launch {
            try {
                // TODO: Implement API call to check follow status
                // For now, just simulate
                isFollowing = false
                updateFollowButton()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun followUser() {
        lifecycleScope.launch {
            try {
                // TODO: Implement API call to follow user
                // Show loading
                btnFollow.isEnabled = false
                btnFollow.text = "Following..."

                // Simulate API call
                kotlinx.coroutines.delay(500)

                isFollowing = true
                updateFollowButton()

                Toast.makeText(this@UserProfileActivity, "Following ${targetUser?.username}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@UserProfileActivity, "Failed to follow user", Toast.LENGTH_SHORT).show()
            } finally {
                btnFollow.isEnabled = true
            }
        }
    }

    private fun unfollowUser() {
        lifecycleScope.launch {
            try {
                // TODO: Implement API call to unfollow user
                btnFollow.isEnabled = false
                btnFollow.text = "Unfollowing..."

                kotlinx.coroutines.delay(500)

                isFollowing = false
                updateFollowButton()

                Toast.makeText(this@UserProfileActivity, "Unfollowed ${targetUser?.username}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@UserProfileActivity, "Failed to unfollow user", Toast.LENGTH_SHORT).show()
            } finally {
                btnFollow.isEnabled = true
            }
        }
    }

    private fun updateFollowButton() {
        if (isFollowing) {
            btnFollow.text = "Following"
            btnFollow.setBackgroundColor(Color.parseColor("#4CAF50")) // Green
            btnFollow.setTextColor(Color.WHITE)
        } else {
            btnFollow.text = "Follow"
            btnFollow.setBackgroundColor(Color.parseColor("#7A4F2B")) // Brown
            btnFollow.setTextColor(Color.WHITE)
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
                        createPostsGrid(posts)

                        // Update post count
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
        val colors = listOf(
            "#4CAF50", "#2196F3", "#FF9800",
            "#9C27B0", "#FF5722", "#00BCD4",
            "#E91E63", "#3F51B5", "#009688"
        )

        val rows = (posts.size + 2) / 3
        val spacing = 4

        for (row in 0 until rows) {
            val rowLayout = LinearLayout(this)
            rowLayout.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            rowLayout.orientation = LinearLayout.HORIZONTAL

            for (col in 0 until 3) {
                val postIndex = row * 3 + col

                val squareContainer = FrameLayout(this)
                val squareParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )

                squareParams.setMargins(
                    if (col == 0) 0 else spacing,
                    0,
                    0,
                    if (row < rows - 1) spacing else 0
                )

                squareContainer.layoutParams = squareParams

                squareContainer.post {
                    val width = squareContainer.width
                    squareContainer.layoutParams.height = width
                    squareContainer.requestLayout()
                }

                if (postIndex < posts.size) {
                    val post = posts[postIndex]
                    val postView = layoutInflater.inflate(R.layout.item_profile_post, squareContainer, false)
                    val postContent = postView.findViewById<TextView>(R.id.post_content)
                    val postImage = postView.findViewById<ImageView>(R.id.post_image)

                    if (!post.imageUrls.isNullOrEmpty() && post.imageUrls.isNotEmpty()) {
                        val imageUrl = post.imageUrls[0]
                        val fullImageUrl = if (imageUrl.startsWith("http")) {
                            imageUrl
                        } else {
                            "${com.example.pawsociety.api.ApiClient.FULL_BASE_URL}$imageUrl"
                        }

                        postImage?.visibility = View.VISIBLE
                        Glide.with(this)
                            .load(fullImageUrl)
                            .centerCrop()
                            .placeholder(android.R.drawable.ic_menu_gallery)
                            .into(postImage)
                    } else {
                        postImage?.visibility = View.GONE
                        postContent.text = "${getPetEmoji(post.petType)}\n${post.petName}"
                        postContent.setBackgroundColor(Color.parseColor(colors[postIndex % colors.size]))
                    }

                    postView.setOnClickListener {
                        showPostPreview(post)
                    }

                    squareContainer.addView(postView)
                } else {
                    val emptyView = View(this)
                    emptyView.layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    emptyView.setBackgroundColor(Color.parseColor("#F0F0F0"))
                    squareContainer.addView(emptyView)
                }

                rowLayout.addView(squareContainer)
            }

            postsGrid.addView(rowLayout)
        }
    }

    private fun getPetEmoji(petType: String): String {
        return when {
            petType.contains("dog", ignoreCase = true) -> "🐶"
            petType.contains("cat", ignoreCase = true) -> "🐱"
            petType.contains("bird", ignoreCase = true) -> "🐦"
            petType.contains("rabbit", ignoreCase = true) -> "🐰"
            petType.contains("fish", ignoreCase = true) -> "🐟"
            else -> "🐾"
        }
    }

    private fun showPostPreview(post: ApiPost) {
        // Show post details (similar to HomeActivity)
        val message = """
            ${post.petName}
            Status: ${post.status}
            Location: ${post.location}
        """.trimIndent()

        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showEmptyState() {
        postsGrid.removeAllViews()
        postsGrid.visibility = View.VISIBLE

        val emptyView = TextView(this)
        emptyView.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        emptyView.gravity = android.view.Gravity.CENTER
        emptyView.text = "No posts yet"
        emptyView.setTextColor(Color.parseColor("#999999"))
        emptyView.textSize = 16f
        emptyView.setPadding(0, 100, 0, 100)
        postsGrid.addView(emptyView)
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
}