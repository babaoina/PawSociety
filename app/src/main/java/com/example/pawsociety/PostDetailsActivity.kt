package com.example.pawsociety

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.pawsociety.api.ApiComment
import com.example.pawsociety.api.ApiPost
import com.example.pawsociety.data.repository.CommentRepository
import com.example.pawsociety.data.repository.FavoriteRepository
import com.example.pawsociety.data.repository.PostRepository
import com.example.pawsociety.util.SessionManager
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class PostDetailsActivity : AppCompatActivity() {

    private lateinit var viewModel: HomeViewModel
    private lateinit var sessionManager: SessionManager
    private lateinit var post: ApiPost

    // Repositories
    private val postRepository = PostRepository()
    private val commentRepository = CommentRepository()
    private val favoriteRepository = FavoriteRepository()

    // Views
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var pageIndicator: TextView
    private lateinit var btnBack: ImageView
    private lateinit var btnMore: TextView
    private lateinit var profileIcon: TextView
    private lateinit var profileImage: ImageView
    private lateinit var userName: TextView
    private lateinit var postLocation: TextView
    private lateinit var postTime: TextView
    private lateinit var petName: TextView
    private lateinit var postStatus: TextView
    private lateinit var postReward: TextView
    private lateinit var postDescription: TextView
    private lateinit var btnMoreDescription: TextView
    private lateinit var postContact: TextView
    private lateinit var btnLike: LinearLayout
    private lateinit var btnLikeIcon: ImageView
    private lateinit var tvLikeCount: TextView

    // REMOVED: btnComment and related views

    private lateinit var btnShare: LinearLayout
    private lateinit var btnFavorite: LinearLayout
    private lateinit var btnFavoriteIcon: ImageView

    // CHAT BUTTON - Directs to chat
    private lateinit var btnChat: Button

    private lateinit var commentsRecyclerView: RecyclerView
    private lateinit var commentInput: EditText
    private lateinit var btnPostComment: ImageView
    private lateinit var progressBar: ProgressBar
    private lateinit var indicatorContainer: LinearLayout
    private lateinit var imageCarouselContainer: FrameLayout
    private lateinit var singleImageView: ImageView
    private lateinit var noImagePlaceholder: LinearLayout

    private var currentUser: com.example.pawsociety.api.ApiUser? = null
    private var isLiked = false
    private var isFavorited = false
    private var commentsList = mutableListOf<ApiComment>()
    private lateinit var commentsAdapter: CommentsAdapter

    companion object {
        private const val EDIT_POST_REQUEST = 1002
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post_details)

        sessionManager = SessionManager(this)
        currentUser = sessionManager.getCurrentUser()

        // Get post from intent
        post = intent.getSerializableExtra("post") as? ApiPost ?: run {
            // Try to get by postId if post object is missing
            val postId = intent.getStringExtra("postId")
            if (postId != null) {
                // You might need to fetch the post from repository here
                Toast.makeText(this, "Loading post...", Toast.LENGTH_SHORT).show()
                // For now, just finish
                finish()
                return
            }
            Toast.makeText(this, "Post not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Debug log
        println("📱 PostDetailsActivity - Loading post: ${post.postId} with ${post.commentsCount} comments")

        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        viewModel.setSessionManager(sessionManager)

        initializeViews()
        setupClickListeners()
        loadPostData()
        loadComments()  // This will now load ALL comments
        checkLikeStatus()
        checkFavoriteStatus()
    }

    private fun initializeViews() {
        viewPager = findViewById(R.id.post_view_pager)
        tabLayout = findViewById(R.id.tab_layout)
        pageIndicator = findViewById(R.id.page_indicator)
        btnBack = findViewById(R.id.btn_back)
        btnMore = findViewById(R.id.btn_more)
        profileIcon = findViewById(R.id.post_profile_icon)
        profileImage = findViewById(R.id.post_profile_image)
        userName = findViewById(R.id.post_user_name)
        postLocation = findViewById(R.id.post_location)
        postTime = findViewById(R.id.post_time)
        petName = findViewById(R.id.post_pet_name)
        postStatus = findViewById(R.id.post_status)
        postReward = findViewById(R.id.post_reward)
        postDescription = findViewById(R.id.post_description)
        btnMoreDescription = findViewById(R.id.btn_more_description)
        postContact = findViewById(R.id.post_contact)
        btnLike = findViewById(R.id.btn_like)
        btnLikeIcon = findViewById(R.id.btn_like_icon)
        tvLikeCount = findViewById(R.id.tv_like_count)

        // COMMENT BUTTON REMOVED

        btnShare = findViewById(R.id.btn_share)
        btnFavorite = findViewById(R.id.btn_favorite)
        btnFavoriteIcon = findViewById(R.id.btn_favorite_icon)

        // CHAT BUTTON
        btnChat = findViewById(R.id.btn_chat)

        commentsRecyclerView = findViewById(R.id.comments_recycler_view)
        commentInput = findViewById(R.id.comment_input)
        btnPostComment = findViewById(R.id.btn_post_comment)
        progressBar = findViewById(R.id.progress_bar)
        indicatorContainer = findViewById(R.id.indicator_container)
        imageCarouselContainer = findViewById(R.id.image_carousel_container)
        singleImageView = findViewById(R.id.post_image)
        noImagePlaceholder = findViewById(R.id.no_image_placeholder)

        // Setup comments RecyclerView
        commentsRecyclerView.layoutManager = LinearLayoutManager(this)
        commentsAdapter = CommentsAdapter(
            commentsList,
            currentUser?.firebaseUid ?: "",
            { commentId -> likeComment(commentId) },
            { comment -> showCommentOptions(comment) },
            { userId, userName -> openUserProfile(userId, userName) }
        )
        commentsRecyclerView.adapter = commentsAdapter

        // Set initial data
        userName.text = post.userName
        postLocation.text = post.location ?: "No location"
        postTime.text = getTimeAgo(post.createdAt)
        petName.text = post.petName
        postStatus.text = post.status
        postDescription.text = post.description
        postContact.text = "📞 ${post.contactInfo}"
        tvLikeCount.text = post.likesCount.toString()

        // Set profile icon (show text initially)
        val firstLetter = if (post.userName.isNotEmpty()) {
            post.userName.first().toString().uppercase()
        } else {
            "?"
        }
        profileIcon.text = firstLetter
        profileIcon.visibility = View.VISIBLE
        profileImage.visibility = View.GONE

        // Setup image carousel
        setupImageCarousel()

        // Set status color and reward
        when (post.status) {
            "LOST" -> {
                postStatus.setBackgroundResource(R.drawable.status_badge_lost)
                postStatus.setTextColor(Color.WHITE)
                if (!post.reward.isNullOrEmpty()) {
                    val formattedReward = formatReward(post.reward ?: "")
                    postReward.text = "💰 Reward: $formattedReward"
                    postReward.visibility = View.VISIBLE
                } else {
                    postReward.visibility = View.GONE
                }
            }
            "FOUND" -> {
                postStatus.setBackgroundResource(R.drawable.status_badge_found)
                postStatus.setTextColor(Color.WHITE)
                postReward.visibility = View.GONE
            }
            "ADOPTION" -> {
                postStatus.setBackgroundResource(R.drawable.status_badge_adoption)
                postStatus.setTextColor(Color.WHITE)
                postReward.visibility = View.GONE
            }
        }

        // Handle long description
        postDescription.post {
            val lineCount = postDescription.lineCount
            if (lineCount > 3) {
                postDescription.maxLines = 3
                postDescription.ellipsize = TextUtils.TruncateAt.END
                btnMoreDescription.visibility = View.VISIBLE
                btnMoreDescription.tag = false
            } else {
                btnMoreDescription.visibility = View.GONE
            }
        }

        // Show/hide chat button based on whether it's user's own post
        if (post.firebaseUid == currentUser?.firebaseUid) {
            btnChat.visibility = View.GONE
        } else {
            btnChat.visibility = View.VISIBLE
        }
    }

    private fun setupImageCarousel() {
        val imageUrls = post.imageUrls
        if (!imageUrls.isNullOrEmpty() && imageUrls.isNotEmpty()) {
            if (imageUrls.size > 1) {
                // Multiple images - use ViewPager carousel
                viewPager.visibility = View.VISIBLE
                singleImageView.visibility = View.GONE
                noImagePlaceholder.visibility = View.GONE

                // Setup ViewPager adapter
                val adapter = PostImagePagerAdapter(imageUrls)
                viewPager.adapter = adapter

                // Set offscreen page limit to avoid reload issues
                viewPager.offscreenPageLimit = 1

                // Setup indicator dots
                setupImageIndicators(imageUrls.size)
                indicatorContainer.visibility = View.VISIBLE

                // Remove any existing callbacks to avoid multiple registrations
                viewPager.unregisterOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {})

                // Update indicator on page change
                viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        super.onPageSelected(position)
                        updateIndicatorSelection(position)
                        if (::pageIndicator.isInitialized) {
                            pageIndicator.text = "${position + 1}/${imageUrls.size}"
                        }
                    }
                })

                // Click on image to open gallery
                viewPager.setOnClickListener {
                    openImageGallery(imageUrls, viewPager.currentItem)
                }

                if (::pageIndicator.isInitialized) {
                    pageIndicator.text = "1/${imageUrls.size}"
                    pageIndicator.visibility = View.VISIBLE
                }
            } else {
                // Single image - use ImageView
                viewPager.visibility = View.GONE
                singleImageView.visibility = View.VISIBLE
                indicatorContainer.visibility = View.GONE
                if (::pageIndicator.isInitialized) {
                    pageIndicator.visibility = View.GONE
                }
                noImagePlaceholder.visibility = View.GONE

                val imageUrl = imageUrls[0]
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
                    openImageGallery(imageUrls, 0)
                }
            }
        } else {
            // No images - show placeholder
            viewPager.visibility = View.GONE
            singleImageView.visibility = View.GONE
            indicatorContainer.visibility = View.GONE
            if (::pageIndicator.isInitialized) {
                pageIndicator.visibility = View.GONE
            }
            noImagePlaceholder.visibility = View.VISIBLE
        }
    }

    private fun setupImageIndicators(count: Int) {
        indicatorContainer.removeAllViews()
        for (i in 0 until count) {
            val indicatorView = layoutInflater.inflate(R.layout.item_image_indicator, indicatorContainer, false)
            val dot = indicatorView.findViewById<View>(R.id.indicator_dot)
            dot.isSelected = (i == 0)
            indicatorContainer.addView(indicatorView)
        }
    }

    private fun updateIndicatorSelection(position: Int) {
        for (i in 0 until indicatorContainer.childCount) {
            val indicatorView = indicatorContainer.getChildAt(i)
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

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnMore.setOnClickListener {
            showPostOptions()
        }

        profileIcon.setOnClickListener {
            openUserProfile(post.firebaseUid, post.userName)
        }

        profileImage.setOnClickListener {
            openUserProfile(post.firebaseUid, post.userName)
        }

        userName.setOnClickListener {
            openUserProfile(post.firebaseUid, post.userName)
        }

        btnMoreDescription.setOnClickListener {
            val isCollapsed = btnMoreDescription.tag as? Boolean ?: false
            if (isCollapsed) {
                postDescription.maxLines = Integer.MAX_VALUE
                postDescription.ellipsize = null
                btnMoreDescription.text = "less"
                btnMoreDescription.tag = false
            } else {
                postDescription.maxLines = 3
                postDescription.ellipsize = TextUtils.TruncateAt.END
                btnMoreDescription.text = "more"
                btnMoreDescription.tag = true
            }
        }

        btnLike.setOnClickListener {
            toggleLike()
        }

        btnShare.setOnClickListener {
            sharePost()
        }

        btnFavorite.setOnClickListener {
            toggleFavorite()
        }

        // CHAT BUTTON CLICK - Opens chat with post owner
        btnChat.setOnClickListener {
            startChat()
        }

        btnPostComment.setOnClickListener {
            postComment()
        }
    }

    private fun loadPostData() {
        // Load user profile image
        viewModel.getUserById(post.firebaseUid) { user ->
            user?.let {
                if (!it.profileImageUrl.isNullOrEmpty()) {
                    val fullImageUrl = if (it.profileImageUrl.startsWith("http")) {
                        it.profileImageUrl
                    } else {
                        "${com.example.pawsociety.api.ApiClient.FULL_BASE_URL}${it.profileImageUrl}"
                    }

                    // Hide text icon and show image
                    profileIcon.visibility = View.GONE
                    profileImage.visibility = View.VISIBLE

                    val requestOptions = RequestOptions()
                        .circleCrop()
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_report_image)

                    Glide.with(this@PostDetailsActivity)
                        .load(fullImageUrl)
                        .apply(requestOptions)
                        .into(profileImage)
                }
            }
        }
    }

    private fun loadComments() {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val result = commentRepository.getComments(post.postId)

                if (result.isSuccess) {
                    val comments = result.getOrNull() ?: emptyList()

                    // Clear and add ALL comments
                    commentsList.clear()
                    commentsList.addAll(comments)

                    // Sort by date (newest first)
                    commentsList.sortByDescending { it.createdAt }

                    commentsAdapter.notifyDataSetChanged()

                    println("✅ Loaded ${commentsList.size} comments for post ${post.postId}")

                    // Scroll to top to show newest comments
                    if (commentsList.isNotEmpty()) {
                        commentsRecyclerView.scrollToPosition(0)
                    }
                } else {
                    println("❌ Failed to load comments: ${result.exceptionOrNull()?.message}")
                    commentsList.clear()
                    commentsAdapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                println("❌ Error loading comments: ${e.message}")
                e.printStackTrace()
                commentsList.clear()
                commentsAdapter.notifyDataSetChanged()
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun checkLikeStatus() {
        val userId = currentUser?.firebaseUid ?: return
        lifecycleScope.launch {
            val result = postRepository.checkLikeStatus(post.postId, userId)
            if (result.isSuccess) {
                isLiked = result.getOrNull() ?: false
                updateLikeButton()
            }
        }
    }

    private fun checkFavoriteStatus() {
        val userId = currentUser?.firebaseUid ?: return
        lifecycleScope.launch {
            val result = favoriteRepository.checkFavorite(post.postId, userId)
            if (result.isSuccess) {
                isFavorited = result.getOrNull() ?: false
                updateFavoriteButton()
            }
        }
    }

    private fun toggleLike() {
        val user = currentUser ?: run {
            Toast.makeText(this, "Please login to like", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val result = postRepository.likePost(post.postId, user.firebaseUid)
            if (result.isSuccess) {
                isLiked = result.getOrNull() ?: !isLiked
                // Update the post object
                val newCount = if (isLiked) post.likesCount + 1 else if (post.likesCount > 0) post.likesCount - 1 else 0
                val updatedPost = post.copy(likesCount = newCount)
                post = updatedPost
                updateLikeButton()
                tvLikeCount.text = post.likesCount.toString()
            }
        }
    }

    private fun toggleFavorite() {
        val user = currentUser ?: run {
            Toast.makeText(this, "Please login to favorite", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val result = if (isFavorited) {
                favoriteRepository.removeFromFavorites(user.firebaseUid, post.postId)
            } else {
                favoriteRepository.addToFavorites(user.firebaseUid, post.postId)
            }

            if (result.isSuccess) {
                isFavorited = !isFavorited
                updateFavoriteButton()
                Toast.makeText(this@PostDetailsActivity,
                    if (isFavorited) "Added to favorites" else "Removed from favorites",
                    Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateLikeButton() {
        if (isLiked) {
            btnLikeIcon.setColorFilter(Color.parseColor("#B88B4A"), PorterDuff.Mode.SRC_ATOP)
        } else {
            btnLikeIcon.setColorFilter(Color.parseColor("#666666"), PorterDuff.Mode.SRC_ATOP)
        }
    }

    private fun updateFavoriteButton() {
        if (isFavorited) {
            btnFavoriteIcon.setColorFilter(Color.parseColor("#B88B4A"), PorterDuff.Mode.SRC_ATOP)
        } else {
            btnFavoriteIcon.setColorFilter(Color.parseColor("#666666"), PorterDuff.Mode.SRC_ATOP)
        }
    }

    private fun postComment() {
        val text = commentInput.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "Please enter a comment", Toast.LENGTH_SHORT).show()
            return
        }

        val user = currentUser ?: run {
            Toast.makeText(this, "Please login to comment", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val result = commentRepository.createComment(
                    postId = post.postId,
                    firebaseUid = user.firebaseUid,
                    userName = user.username,
                    text = text
                )

                if (result.isSuccess) {
                    val newComment = result.getOrNull()
                    commentInput.text.clear()

                    // IMPORTANT: Add the new comment to the list, don't replace
                    if (newComment != null) {
                        commentsList.add(0, newComment) // Add at the beginning (newest first)
                        commentsAdapter.notifyItemInserted(0)
                        commentsRecyclerView.scrollToPosition(0)
                    }

                    Toast.makeText(this@PostDetailsActivity, "Comment posted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@PostDetailsActivity, "Failed to post comment", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@PostDetailsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun likeComment(commentId: String) {
        val user = currentUser ?: return
        lifecycleScope.launch {
            commentRepository.likeComment(commentId, user.firebaseUid)
            loadComments()
        }
    }

    private fun showCommentOptions(comment: ApiComment) {
        val options = mutableListOf("Report")
        if (comment.firebaseUid == currentUser?.firebaseUid) {
            options.add(0, "Delete")
        }

        AlertDialog.Builder(this)
            .setTitle("Comment Options")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Delete" -> deleteComment(comment.commentId)
                    "Report" -> reportComment()
                }
            }
            .show()
    }

    private fun deleteComment(commentId: String) {
        val user = currentUser ?: return
        lifecycleScope.launch {
            val result = commentRepository.deleteComment(commentId, user.firebaseUid)
            if (result.isSuccess) {
                loadComments()
                Toast.makeText(this@PostDetailsActivity, "Comment deleted", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun reportComment() {
        Toast.makeText(this, "Comment reported", Toast.LENGTH_SHORT).show()
    }

    private fun showPostOptions() {
        val options = mutableListOf("Share", "Report")
        if (post.firebaseUid == currentUser?.firebaseUid) {
            options.add(0, "Edit")
            options.add(1, "Delete")
        }

        AlertDialog.Builder(this)
            .setTitle("Post Options")
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "Edit" -> openEditPost()
                    "Delete" -> deletePost()
                    "Share" -> sharePost()
                    "Report" -> reportPost()
                }
            }
            .show()
    }

    private fun openEditPost() {
        val intent = Intent(this, EditPostActivity::class.java)
        intent.putExtra("post", post)
        startActivityForResult(intent, EDIT_POST_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EDIT_POST_REQUEST && resultCode == RESULT_OK) {
            // Refresh post data
            finish()
            startActivity(intent)
        }
    }

    private fun deletePost() {
        AlertDialog.Builder(this)
            .setTitle("Delete Post")
            .setMessage("Are you sure you want to delete this post?")
            .setPositiveButton("Delete") { _, _ ->
                val user = currentUser ?: return@setPositiveButton
                lifecycleScope.launch {
                    val result = postRepository.deletePost(post.postId, user.firebaseUid)
                    if (result.isSuccess) {
                        Toast.makeText(this@PostDetailsActivity, "Post deleted", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sharePost() {
        val shareText = """
            Check out this pet on PawSociety!
            
            ${post.petName} - ${post.status}
            Posted by: ${post.userName}
            Location: ${post.location}
            
            ${post.description}
            
            Contact: ${post.contactInfo}
        """.trimIndent()

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        startActivity(Intent.createChooser(shareIntent, "Share post"))
    }

    private fun reportPost() {
        Toast.makeText(this, "Post reported", Toast.LENGTH_SHORT).show()
    }

    // CHAT FUNCTION - Opens chat with post owner
    private fun startChat() {
        val currentUser = currentUser ?: run {
            Toast.makeText(this, "Please login to chat", Toast.LENGTH_SHORT).show()
            return
        }

        // Don't allow chatting with yourself
        if (currentUser.firebaseUid == post.firebaseUid) {
            Toast.makeText(this, "This is your own post", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("receiverUid", post.firebaseUid)
        intent.putExtra("receiverUsername", post.userName)
        startActivity(intent)
    }

    private fun openUserProfile(userId: String, userName: String) {
        if (userId == currentUser?.firebaseUid) {
            startActivity(Intent(this, ProfileActivity::class.java))
        } else {
            val intent = Intent(this, UserProfileActivity::class.java)
            intent.putExtra("userId", userId)
            intent.putExtra("userName", userName)
            startActivity(intent)
        }
    }

    private fun formatReward(reward: String): String {
        return try {
            val digitsOnly = reward.replace("[^0-9]".toRegex(), "")
            if (digitsOnly.isEmpty()) return reward
            var number = digitsOnly.toLong()
            if (number > 1000000) number = 1000000
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
                } catch (e2: Exception) {}
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

    // Image Pager Adapter (inner class)
    inner class ImagePagerAdapter(private val images: List<String>) :
        RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder>() {

        inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val photoView: com.github.chrisbanes.photoview.PhotoView = itemView.findViewById(R.id.photo_view)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_post_image, parent, false)
            return ImageViewHolder(view)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            val imageUrl = images[position]
            val fullImageUrl = if (imageUrl.startsWith("http")) {
                imageUrl
            } else {
                "${com.example.pawsociety.api.ApiClient.FULL_BASE_URL}$imageUrl"
            }

            Glide.with(this@PostDetailsActivity)
                .load(fullImageUrl)
                .centerCrop()
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .into(holder.photoView)

            holder.itemView.setOnClickListener {
                openImageGallery(images, position)
            }
        }

        override fun getItemCount() = images.size
    }
}