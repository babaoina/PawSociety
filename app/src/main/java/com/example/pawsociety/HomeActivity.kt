package com.example.pawsociety

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.example.pawsociety.api.ApiComment
import com.example.pawsociety.api.ApiPost
import com.example.pawsociety.data.repository.*
import com.example.pawsociety.util.*
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HomeActivity : BaseNavigationActivity() {

    private lateinit var viewModel: HomeViewModel
    private lateinit var postsContainer: LinearLayout
    private lateinit var emptyState: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var sessionManager: SessionManager
    private lateinit var notificationBadge: TextView
    private val postRepository = PostRepository()
    private val commentRepository = CommentRepository()
    private val blockRepository = BlockRepository()
    private val reportRepository = ReportRepository()
    private var currentUser: com.example.pawsociety.api.ApiUser? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

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

        // Request notification permission for Android 13+
        requestNotificationPermission()

        // Initialize FCM and save token
        FCMTokenManager.initialize(currentUser.firebaseUid)

        // Connect to socket and set online status
        SocketManager.connect()
        currentUser?.let {
            SocketManager.joinUserRoom(it.firebaseUid)
            println("🟢 User ${it.username} is now ONLINE")
        }

        // Setup SwipeRefreshLayout
        swipeRefreshLayout.setColorSchemeColors(
            Color.parseColor("#7A4F2B"),
            Color.parseColor("#FF6B35"),
            Color.parseColor("#4CAF50")
        )
        swipeRefreshLayout.setOnRefreshListener {
            refreshData()
        }

        // Setup notification badge
        NotificationManager.setupNotificationBadge(
            context = this,
            badgeView = notificationBadge,
            userId = currentUser.firebaseUid,
            lifecycleOwner = this
        ) {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }

        // Setup socket for real-time notifications
        NotificationManager.setupSocketNotifications(currentUser.firebaseUid, this)

        setupObservers()

        findViewById<ImageView>(R.id.btn_notifications)?.setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }
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

    private fun setupObservers() {
        viewModel.currentUser.observe(this, Observer { user ->
            currentUser = user
            if (user == null) {
                startActivity(Intent(this, LoginActivity::class.java))
                finish()
            }
        })

        viewModel.posts.observe(this, Observer { posts ->
            displayPosts(posts)
            if (swipeRefreshLayout.isRefreshing) {
                swipeRefreshLayout.isRefreshing = false
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

        viewModel.isOffline.observe(this, Observer { isOffline ->
            val offlineIndicator = findViewById<TextView>(R.id.tv_offline_indicator)
            offlineIndicator.visibility = if (isOffline) View.VISIBLE else View.GONE
        })
    }

    private fun refreshData() {
        viewModel.loadPosts()
        viewModel.loadCurrentUser()

        val currentUser = sessionManager.getCurrentUser()
        currentUser?.let {
            NotificationManager.startPolling(it.firebaseUid, this)
        }
    }

    private fun displayPosts(posts: List<ApiPost>) {
        postsContainer.removeAllViews()

        if (posts.isEmpty()) {
            postsContainer.visibility = View.GONE
            emptyState.visibility = View.VISIBLE
            return
        }

        postsContainer.visibility = View.VISIBLE
        emptyState.visibility = View.GONE

        for (post in posts) {
            createPostView(post)
        }
    }

    private fun createPostView(post: ApiPost) {
        try {
            val inflater = LayoutInflater.from(this)
            val postView = inflater.inflate(R.layout.item_post, postsContainer, false)

            // ===== HEADER VIEWS =====
            val profileIcon = postView.findViewById<TextView>(R.id.post_profile_icon)
            val userNameText = postView.findViewById<TextView>(R.id.post_user_name)
            val locationText = postView.findViewById<TextView>(R.id.post_location)
            val postDate = postView.findViewById<TextView>(R.id.post_date)
            val btnMore = postView.findViewById<ImageView>(R.id.btn_more)

            // ===== IMAGE CAROUSEL VIEWS =====
            val viewPager = postView.findViewById<ViewPager2>(R.id.post_view_pager)
            val singleImageView = postView.findViewById<ImageView>(R.id.post_image)
            val indicatorContainer = postView.findViewById<LinearLayout>(R.id.indicator_container)
            val noImagePlaceholder = postView.findViewById<LinearLayout>(R.id.no_image_placeholder)
            val statusBadge = postView.findViewById<TextView>(R.id.post_status)
            val rewardBadge = postView.findViewById<TextView>(R.id.post_reward_badge)

            // ===== PET INFO VIEWS =====
            val petNameText = postView.findViewById<TextView>(R.id.post_pet_name)
            val petTypeText = postView.findViewById<TextView>(R.id.post_pet_type)
            val descriptionText = postView.findViewById<TextView>(R.id.post_description)
            val btnMoreDescription = postView.findViewById<TextView>(R.id.btn_more_description)
            val contactText = postView.findViewById<TextView>(R.id.post_contact)
            val rewardText = postView.findViewById<TextView>(R.id.post_reward)

            // ===== ACTION BUTTONS =====
            val btnLikeGroup = postView.findViewById<LinearLayout>(R.id.btn_like_group)
            val btnLike = postView.findViewById<ImageView>(R.id.btn_like)
            val tvLikeCount = postView.findViewById<TextView>(R.id.tv_like_count)

            val btnCommentGroup = postView.findViewById<LinearLayout>(R.id.btn_comment_group)
            val btnComment = postView.findViewById<ImageView>(R.id.btn_comment)
            val tvCommentCount = postView.findViewById<TextView>(R.id.tv_comment_count)

            val btnShareGroup = postView.findViewById<LinearLayout>(R.id.btn_share_group)
            val btnFavoriteGroup = postView.findViewById<LinearLayout>(R.id.btn_favorite_group)
            val btnFavorite = postView.findViewById<ImageView>(R.id.btn_favorite)
            val btnMessageGroup = postView.findViewById<LinearLayout>(R.id.btn_message_group)

            // ===== SET BASIC DATA =====
            userNameText.text = post.userName
            locationText.text = post.location ?: "Unknown location"
            postDate.text = getTimeAgo(post.createdAt)
            petNameText.text = post.petName
            petTypeText.text = "• ${post.petType}"
            descriptionText.text = post.description
            tvLikeCount.text = post.likesCount.toString()
            tvCommentCount.text = post.commentsCount.toString()
            contactText.text = "📞 ${post.contactInfo}"

            // ===== SET PROFILE ICON =====
            val firstLetter = if (post.userName.isNotEmpty()) {
                post.userName.first().toString().uppercase()
            } else {
                "?"
            }
            profileIcon.text = firstLetter

            // ===== SET STATUS BADGE =====
            when (post.status) {
                "Lost" -> {
                    statusBadge.setBackgroundResource(R.drawable.status_badge_lost)
                    statusBadge.text = "LOST"
                    statusBadge.setTextColor(Color.WHITE)

                    if (!post.reward.isNullOrEmpty()) {
                        val formattedReward = formatReward(post.reward)
                        val digitsOnly = post.reward.replace("[^0-9]".toRegex(), "")
                        val originalNumber = if (digitsOnly.isNotEmpty()) digitsOnly.toLong() else 0

                        rewardBadge.text = if (originalNumber > 1000000) {
                            "💰 Reward: $formattedReward (max limit)"
                        } else {
                            "💰 Reward: $formattedReward"
                        }
                        rewardBadge.visibility = View.VISIBLE

                        rewardText.text = "💰 Reward: $formattedReward"
                        rewardText.visibility = View.VISIBLE
                    } else {
                        rewardBadge.visibility = View.GONE
                        rewardText.visibility = View.GONE
                    }
                }
                "Found" -> {
                    statusBadge.setBackgroundResource(R.drawable.status_badge_found)
                    statusBadge.text = "FOUND"
                    statusBadge.setTextColor(Color.WHITE)
                    rewardBadge.visibility = View.GONE
                    rewardText.visibility = View.GONE
                }
                "Adoption" -> {
                    statusBadge.setBackgroundResource(R.drawable.status_badge_adoption)
                    statusBadge.text = "ADOPTION"
                    statusBadge.setTextColor(Color.WHITE)
                    rewardBadge.visibility = View.GONE
                    rewardText.visibility = View.GONE
                }
            }

            // ===== HANDLE IMAGES (CAROUSEL FOR MULTIPLE IMAGES) =====
// REPLACE THIS ENTIRE BLOCK IN YOUR createPostView FUNCTION
            if (!post.imageUrls.isNullOrEmpty() && post.imageUrls.isNotEmpty()) {
                if (post.imageUrls.size > 1) {
                    // Multiple images - use ViewPager carousel
                    viewPager.visibility = View.VISIBLE
                    singleImageView.visibility = View.GONE
                    noImagePlaceholder.visibility = View.GONE

                    // Setup ViewPager adapter
                    val imageAdapter = PostImagePagerAdapter(post.imageUrls)
                    viewPager.adapter = imageAdapter

                    // IMPORTANT: Set offscreen page limit to avoid reload issues
                    viewPager.offscreenPageLimit = 1

                    // Setup indicator dots
                    setupImageIndicators(indicatorContainer, post.imageUrls.size)
                    indicatorContainer.visibility = View.VISIBLE

                    // Remove any existing callbacks to avoid multiple registrations
                    viewPager.unregisterOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {})

                    // Update indicator on page change
                    viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                        override fun onPageSelected(position: Int) {
                            super.onPageSelected(position)
                            updateIndicatorSelection(indicatorContainer, position)
                        }
                    })

                    // Click on image to open gallery
                    viewPager.setOnClickListener {
                        openImageGallery(post.imageUrls, viewPager.currentItem)
                    }
                } else {
                    // Single image - use ImageView
                    viewPager.visibility = View.GONE
                    singleImageView.visibility = View.VISIBLE
                    indicatorContainer.visibility = View.GONE
                    noImagePlaceholder.visibility = View.GONE

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
                        .into(singleImageView)

                    singleImageView.setOnClickListener {
                        openImageGallery(post.imageUrls, 0)
                    }
                }
            } else {
                // No images - show placeholder
                viewPager.visibility = View.GONE
                singleImageView.visibility = View.GONE
                indicatorContainer.visibility = View.GONE
                noImagePlaceholder.visibility = View.VISIBLE
            }

            // ===== HANDLE "MORE" BUTTON FOR LONG DESCRIPTION =====
            descriptionText.maxLines = Integer.MAX_VALUE
            descriptionText.ellipsize = null

            descriptionText.post {
                val lineCount = descriptionText.lineCount
                if (lineCount > 2) {
                    descriptionText.maxLines = 2
                    descriptionText.ellipsize = TextUtils.TruncateAt.END
                    btnMoreDescription.visibility = View.VISIBLE

                    btnMoreDescription.tag = false
                    btnMoreDescription.setOnClickListener {
                        val isCollapsed = btnMoreDescription.tag as Boolean
                        if (isCollapsed) {
                            descriptionText.maxLines = Integer.MAX_VALUE
                            descriptionText.ellipsize = null
                            btnMoreDescription.text = "less"
                            btnMoreDescription.tag = false
                        } else {
                            descriptionText.maxLines = 2
                            descriptionText.ellipsize = TextUtils.TruncateAt.END
                            btnMoreDescription.text = "more"
                            btnMoreDescription.tag = true
                        }
                    }
                } else {
                    btnMoreDescription.visibility = View.GONE
                }
            }

            // ===== OBSERVE LIKE STATUS =====
            viewModel.likeStatus.observe(this, Observer { likeMap ->
                val isLiked = likeMap[post.postId]
                if (isLiked == true) {
                    btnLike.setColorFilter(Color.parseColor("#B88B4A"), PorterDuff.Mode.SRC_ATOP)
                    btnLike.tag = "liked"
                } else {
                    btnLike.setColorFilter(Color.parseColor("#000000"), PorterDuff.Mode.SRC_ATOP)
                    btnLike.tag = "unliked"
                }
            })

            // ===== OBSERVE FAVORITE STATUS =====
            viewModel.favoriteStatus.observe(this, Observer { favMap ->
                val isFav = favMap[post.postId]
                if (isFav == true) {
                    btnFavorite.setColorFilter(Color.parseColor("#B88B4A"), PorterDuff.Mode.SRC_ATOP)
                    btnFavorite.tag = "favorited"
                } else {
                    btnFavorite.setColorFilter(Color.parseColor("#666666"), PorterDuff.Mode.SRC_ATOP)
                    btnFavorite.tag = "unfavorited"
                }
            })

            // ===== CLICK LISTENERS =====

            // Profile click - view user profile
            profileIcon.setOnClickListener {
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

            // Username click - view user profile
            userNameText.setOnClickListener {
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
            btnMore.setOnClickListener {
                showPostOptions(post, btnLike)
            }

            // Like button click
            btnLikeGroup.setOnClickListener {
                val user = currentUser
                if (user == null) {
                    Toast.makeText(this, "Please login to like posts", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val isLiked = btnLike.tag == "liked"
                viewModel.toggleLike(post, isLiked)
            }

            // Comment button click
            btnCommentGroup.setOnClickListener {
                showCommentsDialog(post)
            }

            // Share button click
            btnShareGroup.setOnClickListener {
                sharePost(post)
            }

            // Favorite button click
            btnFavoriteGroup.setOnClickListener {
                val user = currentUser
                if (user == null) {
                    Toast.makeText(this, "Please login to favorite", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val isFav = btnFavorite.tag == "favorited"
                viewModel.toggleFavorite(post, isFav)
            }

            // Message button - only show if NOT your own post
            val user = currentUser
            if (post.firebaseUid == user?.firebaseUid) {
                btnMessageGroup.visibility = View.GONE
            } else {
                btnMessageGroup.visibility = View.VISIBLE
                btnMessageGroup.setOnClickListener {
                    val intent = Intent(this, ChatActivity::class.java)
                    intent.putExtra("receiverUid", post.firebaseUid)
                    intent.putExtra("receiverUsername", post.userName)
                    startActivity(intent)
                }
            }

            postsContainer.addView(postView)
        } catch (e: Exception) {
            println("❌ Error creating post view: ${e.message}")
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

    private fun showCommentsDialog(post: ApiPost) {
        try {
            val dialogView = layoutInflater.inflate(R.layout.dialog_comments, null)

            val postIcon = dialogView.findViewById<TextView>(R.id.comment_post_icon)
            val postUser = dialogView.findViewById<TextView>(R.id.comment_post_user)
            val postCaption = dialogView.findViewById<TextView>(R.id.comment_post_caption)
            val commentsRecyclerView = dialogView.findViewById<RecyclerView>(R.id.comments_recycler_view)
            val btnClose = dialogView.findViewById<TextView>(R.id.btn_close_comments)
            val commentInput = dialogView.findViewById<EditText>(R.id.comment_input)
            val btnPostComment = dialogView.findViewById<TextView>(R.id.btn_post_comment)

            if (commentsRecyclerView == null) {
                Toast.makeText(this, "Comments UI error", Toast.LENGTH_SHORT).show()
                return
            }

            val emoji = when {
                post.petType.contains("dog", ignoreCase = true) -> "🐶"
                post.petType.contains("cat", ignoreCase = true) -> "🐱"
                post.petType.contains("bird", ignoreCase = true) -> "🐦"
                post.petType.contains("rabbit", ignoreCase = true) -> "🐰"
                post.petType.contains("fish", ignoreCase = true) -> "🐟"
                else -> "🐾"
            }

            postIcon?.text = emoji
            postIcon?.background = ContextCompat.getDrawable(this, R.drawable.circle_solid_profile)

            postUser?.text = post.userName
            postCaption?.text = "${post.petName} • ${post.status}\n${post.description}"

            commentsRecyclerView.layoutManager = LinearLayoutManager(this)

            loadCommentsForRecyclerView(commentsRecyclerView, post.postId)

            val dialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
            dialog.setContentView(dialogView)

            dialog.window?.apply {
                setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundDrawableResource(android.R.color.transparent)
            }

            btnClose?.setOnClickListener {
                dialog.dismiss()
            }

            btnPostComment?.setOnClickListener {
                val text = commentInput?.text.toString().trim()
                if (text.isNotEmpty()) {
                    val user = currentUser
                    if (user == null) {
                        Toast.makeText(this, "Please login to comment", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    viewModel.addComment(post.postId, text)
                    commentInput?.text?.clear()
                    Toast.makeText(this, "Comment posted!", Toast.LENGTH_SHORT).show()
                    loadCommentsForRecyclerView(commentsRecyclerView, post.postId)
                } else {
                    Toast.makeText(this, "Please enter a comment", Toast.LENGTH_SHORT).show()
                }
            }

            dialog.show()
        } catch (e: Exception) {
            println("❌ Error showing comments: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "Comments not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadCommentsForRecyclerView(recyclerView: RecyclerView, postId: String) {
        println("💬 Loading comments for post: $postId")

        lifecycleScope.launch {
            try {
                val result = commentRepository.getComments(postId)

                if (result.isSuccess) {
                    val apiComments = result.getOrNull()!!
                    println("✅ Loaded ${apiComments.size} comments for post $postId")

                    val user = currentUser
                    val adapter = CommentsAdapter(
                        apiComments,
                        user?.firebaseUid ?: "",
                        { commentId ->
                            if (user != null) {
                                lifecycleScope.launch {
                                    commentRepository.likeComment(commentId, user.firebaseUid)
                                    loadCommentsForRecyclerView(recyclerView, postId)
                                }
                            }
                        },
                        { comment ->
                            showCommentOptions(comment)
                        },
                        { userId, userName ->
                            try {
                                if (user?.firebaseUid == userId) {
                                    val intent = Intent(this@HomeActivity, ProfileActivity::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                    startActivity(intent)
                                } else {
                                    val intent = Intent(this@HomeActivity, UserProfileActivity::class.java)
                                    intent.putExtra("userId", userId)
                                    intent.putExtra("userName", userName)
                                    startActivity(intent)
                                }
                            } catch (e: Exception) {
                                println("❌ Error opening profile from comment: ${e.message}")
                            }
                        }
                    )
                    recyclerView.adapter = adapter
                } else {
                    println("❌ Failed to load comments")
                    val adapter = CommentsAdapter(emptyList(), currentUser?.firebaseUid ?: "", {}, {}, { _, _ -> })
                    recyclerView.adapter = adapter
                }
            } catch (e: Exception) {
                println("❌ Error loading comments: ${e.message}")
                e.printStackTrace()
                val adapter = CommentsAdapter(emptyList(), currentUser?.firebaseUid ?: "", {}, {}, { _, _ -> })
                recyclerView.adapter = adapter
            }
        }
    }

    private fun showCommentOptions(comment: ApiComment) {
        try {
            val dialogView = layoutInflater.inflate(R.layout.dialog_comment_options, null)

            val btnClose = dialogView.findViewById<TextView>(R.id.btn_close)
            val optionReport = dialogView.findViewById<LinearLayout>(R.id.option_report)
            val optionDelete = dialogView.findViewById<LinearLayout>(R.id.option_delete)

            val user = currentUser
            if (user != null && comment.firebaseUid == user.firebaseUid) {
                optionDelete?.visibility = View.VISIBLE
            } else {
                optionDelete?.visibility = View.GONE
            }

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

            optionReport?.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Report Comment")
                    .setMessage("Are you sure you want to report this comment?")
                    .setPositiveButton("Report") { _, _ ->
                        reportComment(comment)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                dialog.dismiss()
            }

            optionDelete?.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Delete Comment")
                    .setMessage("Are you sure you want to delete this comment?")
                    .setPositiveButton("Delete") { _, _ ->
                        val user = currentUser
                        if (user != null) {
                            lifecycleScope.launch {
                                try {
                                    val result = commentRepository.deleteComment(comment.commentId, user.firebaseUid)
                                    if (result.isSuccess) {
                                        Toast.makeText(this@HomeActivity, "Comment deleted", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(this@HomeActivity, "Failed to delete comment", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(this@HomeActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                dialog.dismiss()
            }

            dialog.show()
        } catch (e: Exception) {
            println("❌ Error showing comment options: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "Menu not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun reportComment(comment: ApiComment) {
        val user = currentUser ?: return

        lifecycleScope.launch {
            val result = reportRepository.createReport(
                reporterUid = user.firebaseUid,
                reason = "inappropriate",
                commentId = comment.commentId,
                description = "Comment reported"
            )

            if (result.isSuccess) {
                Toast.makeText(this@HomeActivity, "Comment reported", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@HomeActivity, "Failed to report comment", Toast.LENGTH_SHORT).show()
            }
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

    private fun showPostOptions(post: ApiPost, btnLike: ImageView?) {
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

            // New options
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

            // Check if this is the user's own post
            val isOwnPost = (user != null && post.firebaseUid == user.firebaseUid)

            // Create dialog
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
                // Show Edit option for own posts
                optionEdit?.visibility = View.VISIBLE
                optionEdit?.setOnClickListener {
                    openEditPost(post)
                    dialog.dismiss()
                }

                // Show Delete option for own posts
                optionDelete?.visibility = View.VISIBLE
                optionDelete?.setOnClickListener {
                    showDeleteConfirmation(post)
                    dialog.dismiss()
                }

                // Hide Follow option for own posts
                optionFollow?.visibility = View.GONE
                // Hide Block option for own posts
                optionBlock?.visibility = View.GONE
            } else {
                // Hide Edit and Delete for other users' posts
                optionEdit?.visibility = View.GONE
                optionDelete?.visibility = View.GONE

                // Show Follow option for other users
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

                // Show Block option for other users
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
                AlertDialog.Builder(this)
                    .setTitle("Hide this post?")
                    .setMessage("You won't see this post again in your feed.")
                    .setPositiveButton("Hide") { _, _ ->
                        Toast.makeText(this, "Post hidden", Toast.LENGTH_SHORT).show()
                        showUndoSnackbar(post)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
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
                // Refresh posts to hide blocked user's content
                viewModel.loadPosts(true)
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
        startActivityForResult(intent, EDIT_POST_REQUEST)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EDIT_POST_REQUEST && resultCode == RESULT_OK) {
            // Refresh posts
            viewModel.loadPosts(true)
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadPosts()
        viewModel.loadCurrentUser()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::notificationBadge.isInitialized) {
            NotificationManager.cleanup(notificationBadge)
        }
        // Disconnect socket when activity is destroyed
        SocketManager.disconnect()
    }

    companion object {
        private const val EDIT_POST_REQUEST = 1001
    }
}