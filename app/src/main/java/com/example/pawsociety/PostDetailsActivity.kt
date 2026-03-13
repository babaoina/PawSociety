package com.example.pawsociety

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
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

import com.example.pawsociety.api.ApiPost
import com.example.pawsociety.data.repository.*
import com.example.pawsociety.util.SessionManager
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import com.example.pawsociety.widget.LikeButton
import kotlin.math.max

class PostDetailsActivity : AppCompatActivity() {

    private lateinit var viewModel: HomeViewModel
    private lateinit var sessionManager: SessionManager
    private lateinit var post: ApiPost

    // Repositories
    private val postRepository = PostRepository()

    private val favoriteRepository = FavoriteRepository()
    private val reportRepository = ReportRepository()
    private val blockRepository = BlockRepository()
    private val hidePostRepository = HidePostRepository()

    // Views
    private lateinit var viewPager: ViewPager2

    private lateinit var pageIndicator: TextView
    private lateinit var btnBack: ImageView
    private lateinit var btnMore: ImageView
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
    private lateinit var tvLikeCount: TextView
    private lateinit var btnShare: LinearLayout
    private lateinit var btnFavorite: LinearLayout
    private lateinit var btnFavoriteIcon: ImageView
    private lateinit var btnMessage: LinearLayout
    private lateinit var genderIcon: ImageView
    private lateinit var genderText: TextView
    private lateinit var btnLikeLottieDetails: LikeButton




    private lateinit var progressBar: ProgressBar
    private lateinit var indicatorContainer: LinearLayout
    private lateinit var imageCarouselContainer: FrameLayout
    private lateinit var singleImageView: ImageView
    private lateinit var noImagePlaceholder: LinearLayout

    private var currentUser: com.example.pawsociety.api.ApiUser? = null
    private var isLiked = false
    private var isFavorited = false


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
            val postId = intent.getStringExtra("postId")
            if (postId != null) {
                Toast.makeText(this, "Loading post...", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            Toast.makeText(this, "Post not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }


        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        viewModel.setSessionManager(sessionManager)

        initializeViews()
        setupClickListeners()
        loadPostData()
        checkLikeStatus()
        checkFavoriteStatus()
    }

    private fun initializeViews() {
        viewPager = findViewById(R.id.post_view_pager)
        pageIndicator = findViewById(R.id.page_indicator)
        btnBack = findViewById(R.id.btn_back)
        btnMore = findViewById(R.id.btn_more_details)
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
        tvLikeCount = findViewById(R.id.tv_like_count)
        btnShare = findViewById(R.id.btn_share)
        btnFavorite = findViewById(R.id.btn_favorite)
        btnFavoriteIcon = findViewById(R.id.btn_favorite_icon)
        btnMessage = findViewById(R.id.btn_message)
        genderIcon = findViewById(R.id.post_gender_icon)
        genderText = findViewById(R.id.post_gender_text)
        btnLikeLottieDetails = findViewById(R.id.btn_like_lottie_view)
        progressBar = findViewById(R.id.progress_bar)
        indicatorContainer = findViewById(R.id.indicator_container)
        imageCarouselContainer = findViewById(R.id.image_carousel_container)
        singleImageView = findViewById(R.id.post_image)
        noImagePlaceholder = findViewById(R.id.no_image_placeholder)

        // Set click listener for the 3 dots menu
        btnMore.setOnClickListener {
            showPostOptions()
        }



        // Set initial data
        userName.text = post.userName
        postLocation.text = post.location ?: "No location"
        postTime.text = getTimeAgo(post.createdAt)
        petName.text = post.petName
        postStatus.text = post.status
        postDescription.text = post.description
        postContact.text = "📞 ${post.contactInfo}"
        tvLikeCount.text = post.likesCount.toString()

        // Set gender display
        when (post.gender?.lowercase()) {
            "male" -> {
                try {
                    genderIcon.setImageResource(R.drawable.ic_male)
                    genderIcon.visibility = View.VISIBLE
                    genderText.visibility = View.GONE
                    genderIcon.setColorFilter(Color.parseColor("#2196F3"))
                } catch (e: Exception) {
                    genderIcon.visibility = View.GONE
                    genderText.visibility = View.VISIBLE
                    genderText.text = "• Male"
                    genderText.setTextColor(Color.parseColor("#2196F3"))
                }
            }
            "female" -> {
                try {
                    genderIcon.setImageResource(R.drawable.ic_female)
                    genderIcon.visibility = View.VISIBLE
                    genderText.visibility = View.GONE
                    genderIcon.setColorFilter(Color.parseColor("#E91E63"))
                } catch (e: Exception) {
                    genderIcon.visibility = View.GONE
                    genderText.visibility = View.VISIBLE
                    genderText.text = "• Female"
                    genderText.setTextColor(Color.parseColor("#E91E63"))
                }
            }
            else -> {
                genderIcon.visibility = View.GONE
                genderText.visibility = View.VISIBLE
                genderText.text = "• Unknown"
                genderText.setTextColor(Color.parseColor("#999999"))
            }
        }

        // Set profile icon
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
            "Lost" -> {
                postStatus.setBackgroundResource(R.drawable.status_badge_lost)
                postStatus.setTextColor(Color.WHITE)
                if (!post.reward.isNullOrEmpty()) {
                    val formattedReward = formatReward(post.reward ?: "")
                    postReward.text = "Reward = ₱ $formattedReward"
                    postReward.visibility = View.VISIBLE
                } else {
                    postReward.visibility = View.GONE
                }
            }
            "Found" -> {
                postStatus.setBackgroundResource(R.drawable.status_badge_found)
                postStatus.setTextColor(Color.WHITE)
                postReward.visibility = View.GONE
            }
            "Adoption" -> {
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

        // Show/hide chat button
        if (post.firebaseUid == currentUser?.firebaseUid) {
            btnMessage.visibility = View.GONE
        } else {
            btnMessage.visibility = View.VISIBLE
            btnMessage.setOnClickListener {
                startChat()
            }
        }
    }

    private fun setupImageCarousel() {
        val imageUrls = post.imageUrls
        if (!imageUrls.isNullOrEmpty() && imageUrls.isNotEmpty()) {
            if (imageUrls.size > 1) {
                viewPager.visibility = View.VISIBLE
                singleImageView.visibility = View.GONE
                noImagePlaceholder.visibility = View.GONE

                val adapter = PostImagePagerAdapter(imageUrls)
                viewPager.adapter = adapter
                viewPager.offscreenPageLimit = 1

                setupImageIndicators(imageUrls.size)
                indicatorContainer.visibility = View.VISIBLE

                viewPager.unregisterOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {})

                viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        super.onPageSelected(position)
                        updateIndicatorSelection(position)
                        if (::pageIndicator.isInitialized) {
                            pageIndicator.text = "${position + 1}/${imageUrls.size}"
                        }
                    }
                })

                viewPager.setOnClickListener {
                    openImageGallery(imageUrls, viewPager.currentItem)
                }

                if (::pageIndicator.isInitialized) {
                    pageIndicator.text = "1/${imageUrls.size}"
                    pageIndicator.visibility = View.VISIBLE
                }
            } else {
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

        btnMessage.setOnClickListener {
            startChat()
        }

    }

    private fun loadPostData() {
        viewModel.getUserById(post.firebaseUid) { user ->
            user?.let {
                if (!it.profileImageUrl.isNullOrEmpty()) {
                    val fullImageUrl = if (it.profileImageUrl.startsWith("http")) {
                        it.profileImageUrl
                    } else {
                        "${com.example.pawsociety.api.ApiClient.FULL_BASE_URL}${it.profileImageUrl}"
                    }

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



    private fun checkLikeStatus() {
        val userId = currentUser?.firebaseUid ?: return
        lifecycleScope.launch {
            val result = postRepository.checkLikeStatus(post.postId, userId)
            if (result.isSuccess) {
                val response = result.getOrNull()!!
                isLiked = response.isLiked ?: false
                btnLikeLottieDetails.setLiked(isLiked, animate = false)
                tvLikeCount.text = response.likesCount.toString()
                post = post.copy(likesCount = response.likesCount)
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

        // Get current state from the button
        val wasAlreadyLiked = btnLikeLottieDetails.isLiked
        val willBeLiked = !wasAlreadyLiked

        // Update UI immediately (optimistic update)
        btnLikeLottieDetails.setLiked(willBeLiked, animate = willBeLiked)

        // Calculate optimistic count
        val optimisticCount = if (willBeLiked) {
            post.likesCount + 1
        } else {
            max(0, post.likesCount - 1)
        }
        tvLikeCount.text = optimisticCount.toString()

        // Disable button to prevent double-clicks
        btnLike.isEnabled = false

        // Call repository - now returns Result<LikeResponse>
        lifecycleScope.launch {
            val result = postRepository.likePost(post.postId, user.firebaseUid)

            if (result.isSuccess) {
                val response = result.getOrNull()!!

                // Get the ACTUAL values from server
                val serverLiked = response.liked ?: response.isLiked ?: willBeLiked
                val serverCount = response.likesCount

                // Update with server values
                btnLikeLottieDetails.setLiked(serverLiked, animate = false)
                tvLikeCount.text = serverCount.toString()

                // Update post object
                post = post.copy(likesCount = serverCount)
                isLiked = serverLiked

                // Optional log to debug
                Log.d("LikeDebug", "Server returned: liked=$serverLiked, count=$serverCount")
            } else {
                // Revert on failure
                btnLikeLottieDetails.setLiked(wasAlreadyLiked, animate = false)
                tvLikeCount.text = post.likesCount.toString()
                Toast.makeText(this@PostDetailsActivity,
                    "Failed to update like", Toast.LENGTH_SHORT).show()
            }

            // Re-enable button
            btnLike.isEnabled = true
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

    private fun updateFavoriteButton() {
        if (isFavorited) {
            btnFavoriteIcon.setColorFilter(Color.parseColor("#B88B4A"), PorterDuff.Mode.SRC_ATOP)
        } else {
            btnFavoriteIcon.setColorFilter(Color.parseColor("#666666"), PorterDuff.Mode.SRC_ATOP)
        }
    }


    // ========== POST MENU WITH INSTAGRAM STYLE ==========
    private fun showPostOptions() {
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

            // Set data
            menuUsername?.text = post.userName
            menuPostInfo?.text = "${post.petName} • ${post.status}"

            // Check favorite status
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

            // Configure options based on post ownership
            if (isOwnPost) {
                // OWN POST - Show Edit and Delete options
                optionEdit?.visibility = View.VISIBLE
                optionEdit?.setOnClickListener {
                    openEditPost()
                    dialog.dismiss()
                }

                optionDelete?.visibility = View.VISIBLE
                optionDelete?.setOnClickListener {
                    showDeleteConfirmation()
                    dialog.dismiss()
                }

                // Hide other options
                optionFollow?.visibility = View.GONE
                optionBlock?.visibility = View.GONE
                optionReport?.visibility = View.GONE
                optionWhySeeing?.visibility = View.GONE
                optionHide?.visibility = View.GONE
                optionAboutAccount?.visibility = View.GONE
            } else {
                // OTHER USER'S POST - Show Follow, Block, Report, etc.
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
                    showBlockConfirmation()
                    dialog.dismiss()
                }

                optionReport?.visibility = View.VISIBLE
                optionWhySeeing?.visibility = View.VISIBLE
                optionHide?.visibility = View.VISIBLE
                optionAboutAccount?.visibility = View.VISIBLE
            }

            optionAddFavorites?.setOnClickListener {
                val isFav = viewModel.favoriteStatus.value?.get(post.postId) ?: false
                viewModel.toggleFavorite(post, isFav)
                dialog.dismiss()
            }

            optionWhySeeing?.setOnClickListener {
                showWhySeeingDialog()
                dialog.dismiss()
            }

            optionHide?.setOnClickListener {
                showHideConfirmation()
                dialog.dismiss()
            }

            optionAboutAccount?.setOnClickListener {
                showAboutAccountDialog()
                dialog.dismiss()
            }

            optionReport?.setOnClickListener {
                showReportDialog()
                dialog.dismiss()
            }

            dialog.show()
        } catch (e: Exception) {
            println("❌ Error showing post options: ${e.message}")
            e.printStackTrace()
            Toast.makeText(this, "Menu not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showWhySeeingDialog() {
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

    private fun showAboutAccountDialog() {
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

    private fun showHideConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Hide Post")
            .setMessage("This post will be hidden from your feed. You can unhide it later in Settings.")
            .setPositiveButton("Hide") { _, _ ->
                hidePost()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun hidePost() {
        val currentUser = currentUser ?: return
        lifecycleScope.launch {
            val result = hidePostRepository.hidePost(currentUser.firebaseUid, post.postId)
            if (result.isSuccess) {
                Toast.makeText(this@PostDetailsActivity, "Post hidden", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun showBlockConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Block User")
            .setMessage("Are you sure you want to block ${post.userName}?")
            .setPositiveButton("Block") { _, _ ->
                blockUser()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun blockUser() {
        val currentUser = currentUser ?: return
        lifecycleScope.launch {
            val result = blockRepository.blockUser(currentUser.firebaseUid, post.firebaseUid)
            if (result.isSuccess) {
                Toast.makeText(this@PostDetailsActivity, "Blocked ${post.userName}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun showReportDialog() {
        val options = arrayOf("Spam", "Inappropriate", "False information", "Scam", "Harassment", "Other")
        AlertDialog.Builder(this)
            .setTitle("Report Post")
            .setItems(options) { _, which ->
                submitReport(options[which])
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun submitReport(reason: String) {
        val currentUser = currentUser ?: return
        lifecycleScope.launch {
            val result = reportRepository.createReport(
                reporterUid = currentUser.firebaseUid,
                reason = reason.lowercase().replace(" ", "_"),
                postId = post.postId,
                description = "Reported for: $reason"
            )
            if (result.isSuccess) {
                Toast.makeText(this@PostDetailsActivity, "Report submitted", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openEditPost() {
        val intent = Intent(this, EditPostActivity::class.java)
        intent.putExtra("post", post)
        startActivityForResult(intent, EDIT_POST_REQUEST)
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Delete Post")
            .setMessage("Are you sure you want to delete this post?")
            .setPositiveButton("Delete") { _, _ ->
                deletePost()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePost() {
        val user = currentUser ?: return
        lifecycleScope.launch {
            val result = postRepository.deletePost(post.postId, user.firebaseUid)
            if (result.isSuccess) {
                Toast.makeText(this@PostDetailsActivity, "Post deleted", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == EDIT_POST_REQUEST && resultCode == RESULT_OK) {
            finish()
            startActivity(intent)
        }
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

    private fun startChat() {
        val currentUser = currentUser ?: run {
            Toast.makeText(this, "Please login to chat", Toast.LENGTH_SHORT).show()
            return
        }

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