package com.example.pawsociety

import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
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

class PostViewActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var tvCounter: TextView
    private lateinit var btnClose: ImageView
    private lateinit var btnMore: ImageView
    private lateinit var tvProfileIcon: TextView
    private lateinit var tvUsername: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvPetName: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvReward: TextView
    private lateinit var tvDescription: TextView
    private lateinit var tvContact: TextView
    private lateinit var btnLike: LinearLayout
    private lateinit var ivLike: ImageView
    private lateinit var tvLikes: TextView
    private lateinit var btnComment: LinearLayout
    private lateinit var tvComments: TextView
    private lateinit var btnShare: LinearLayout
    private lateinit var btnMessage: LinearLayout
    private lateinit var btnFavorite: LinearLayout
    private lateinit var ivFavorite: ImageView
    private lateinit var layoutProfile: LinearLayout

    private lateinit var sessionManager: SessionManager
    private lateinit var viewModel: HomeViewModel

    private var post: ApiPost? = null
    private var currentUser: com.example.pawsociety.api.ApiUser? = null
    private var imageUrls: List<String> = listOf()
    private var currentPosition: Int = 0

    private val postRepository = PostRepository()
    private val commentRepository = CommentRepository()
    private val favoriteRepository = FavoriteRepository()

    private var isLiked = false
    private var isFavorited = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post_view)

        // Make activity fullscreen
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        sessionManager = SessionManager(this)
        currentUser = sessionManager.getCurrentUser()

        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        viewModel.setSessionManager(sessionManager)

        // Get post from intent
        post = intent.getSerializableExtra("post") as? ApiPost
        if (post == null) {
            Toast.makeText(this, "Post not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        imageUrls = post?.imageUrls ?: listOf()

        initializeViews()
        setupViews()
        setupViewPager()
        setupClickListeners()
        checkLikeStatus()
        checkFavoriteStatus()
    }

    private fun initializeViews() {
        viewPager = findViewById(R.id.view_pager)
        tabLayout = findViewById(R.id.tab_layout)
        tvCounter = findViewById(R.id.tv_counter)
        btnClose = findViewById(R.id.btn_close)
        btnMore = findViewById(R.id.btn_more)
        tvProfileIcon = findViewById(R.id.tv_profile_icon)
        tvUsername = findViewById(R.id.tv_username)
        tvLocation = findViewById(R.id.tv_location)
        tvTime = findViewById(R.id.tv_time)
        tvPetName = findViewById(R.id.tv_pet_name)
        tvStatus = findViewById(R.id.tv_status)
        tvReward = findViewById(R.id.tv_reward)
        tvDescription = findViewById(R.id.tv_description)
        tvContact = findViewById(R.id.tv_contact)
        btnLike = findViewById(R.id.btn_like)
        ivLike = findViewById(R.id.iv_like)
        tvLikes = findViewById(R.id.tv_likes)
        btnComment = findViewById(R.id.btn_comment)
        tvComments = findViewById(R.id.tv_comments)
        btnShare = findViewById(R.id.btn_share)
        btnMessage = findViewById(R.id.btn_message)
        btnFavorite = findViewById(R.id.btn_favorite)
        ivFavorite = findViewById(R.id.iv_favorite)
        layoutProfile = findViewById(R.id.layout_profile)
    }

    private fun setupViews() {
        post?.let { post ->
            tvUsername.text = post.userName
            tvLocation.text = post.location ?: "No location"
            tvTime.text = getTimeAgo(post.createdAt)
            tvPetName.text = post.petName
            tvStatus.text = post.status
            tvDescription.text = post.description
            tvContact.text = "📞 ${post.contactInfo}"
            tvLikes.text = post.likesCount.toString()
            tvComments.text = post.commentsCount.toString()

            // Set profile icon
            val firstLetter = if (post.userName.isNotEmpty()) {
                post.userName.first().toString().uppercase()
            } else {
                "?"
            }
            tvProfileIcon.text = firstLetter

            // Set status color and reward
            when (post.status) {
                "Lost" -> {
                    tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                    if (!post.reward.isNullOrEmpty()) {
                        tvReward.text = "💰 Reward: ${post.reward}"
                        tvReward.visibility = View.VISIBLE
                    } else {
                        tvReward.visibility = View.GONE
                    }
                }
                "Found" -> {
                    tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                    tvReward.visibility = View.GONE
                }
                "Adoption" -> {
                    tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
                    tvReward.visibility = View.GONE
                }
            }

            // Hide message button if it's own post
            if (post.firebaseUid == currentUser?.firebaseUid) {
                btnMessage.visibility = View.GONE
            } else {
                btnMessage.visibility = View.VISIBLE
            }
        }
    }

    private fun setupViewPager() {
        if (imageUrls.isEmpty()) {
            viewPager.visibility = View.GONE
            tabLayout.visibility = View.GONE
            tvCounter.visibility = View.GONE
            return
        }

        val adapter = ImagePagerAdapter(imageUrls)
        viewPager.adapter = adapter
        viewPager.currentItem = currentPosition

        // Update counter
        if (imageUrls.size > 1) {
            tvCounter.visibility = View.VISIBLE
            updateCounter(currentPosition)
        } else {
            tvCounter.visibility = View.GONE
        }

        // Setup page change callback
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateCounter(position)
            }
        })

        // Setup tab indicators if more than 1 image
        if (imageUrls.size > 1) {
            tabLayout.visibility = View.VISIBLE
            TabLayoutMediator(tabLayout, viewPager) { _, _ -> }.attach()
        } else {
            tabLayout.visibility = View.GONE
        }
    }

    private fun updateCounter(position: Int) {
        tvCounter.text = "${position + 1}/${imageUrls.size}"
    }

    private fun setupClickListeners() {
        btnClose.setOnClickListener {
            finish()
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        btnMore.setOnClickListener {
            showOptions()
        }

        layoutProfile.setOnClickListener {
            openUserProfile()
        }

        btnLike.setOnClickListener {
            toggleLike()
        }

        btnComment.setOnClickListener {
            openComments()
        }

        btnShare.setOnClickListener {
            sharePost()
        }

        btnMessage.setOnClickListener {
            startChat()
        }

        btnFavorite.setOnClickListener {
            toggleFavorite()
        }
    }

    private fun openUserProfile() {
        post?.let { post ->
            if (post.firebaseUid == currentUser?.firebaseUid) {
                startActivity(Intent(this, ProfileActivity::class.java))
            } else {
                val intent = Intent(this, UserProfileActivity::class.java)
                intent.putExtra("userId", post.firebaseUid)
                intent.putExtra("userName", post.userName)
                startActivity(intent)
            }
        }
    }

    private fun showOptions() {
        // You can implement options menu (report, delete, etc.)
        Toast.makeText(this, "Options", Toast.LENGTH_SHORT).show()
    }

    private fun toggleLike() {
        val user = currentUser ?: run {
            Toast.makeText(this, "Please login to like", Toast.LENGTH_SHORT).show()
            return
        }

        post?.let { post ->
            lifecycleScope.launch {
                val result = postRepository.likePost(post.postId, user.firebaseUid)
                if (result.isSuccess) {
                    isLiked = !isLiked
                    updateLikeButton()

                    // Calculate new count
                    val newCount = if (isLiked) post.likesCount + 1 else post.likesCount - 1

                    // Create a new copy with updated count
                    val updatedPost = post.copy(likesCount = newCount)

                    // Update the post reference
                    this@PostViewActivity.post = updatedPost

                    // Update the TextView with the new count
                    tvLikes.text = newCount.toString()

                    Toast.makeText(this@PostViewActivity,
                        if (isLiked) "Liked" else "Unliked",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun toggleFavorite() {
        val user = currentUser ?: run {
            Toast.makeText(this, "Please login to favorite", Toast.LENGTH_SHORT).show()
            return
        }

        post?.let { post ->
            lifecycleScope.launch {
                val result = if (isFavorited) {
                    favoriteRepository.removeFromFavorites(user.firebaseUid, post.postId)
                } else {
                    favoriteRepository.addToFavorites(user.firebaseUid, post.postId)
                }

                if (result.isSuccess) {
                    isFavorited = !isFavorited
                    updateFavoriteButton()
                    Toast.makeText(this@PostViewActivity,
                        if (isFavorited) "Added to favorites" else "Removed from favorites",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openComments() {
        post?.let { post ->
            Toast.makeText(this, "Comments for ${post.petName}", Toast.LENGTH_SHORT).show()
            // You can open a comments dialog here
        }
    }

    private fun sharePost() {
        post?.let { post ->
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
    }

    private fun startChat() {
        post?.let { post ->
            val intent = Intent(this, ChatActivity::class.java)
            intent.putExtra("receiverUid", post.firebaseUid)
            intent.putExtra("receiverUsername", post.userName)
            startActivity(intent)
        }
    }

    private fun checkLikeStatus() {
        post?.let { post ->
            val userId = currentUser?.firebaseUid ?: return
            lifecycleScope.launch {
                val result = postRepository.checkLikeStatus(post.postId, userId)
                if (result.isSuccess) {
                    isLiked = result.getOrNull() ?: false
                    updateLikeButton()
                }
            }
        }
    }

    private fun checkFavoriteStatus() {
        post?.let { post ->
            val userId = currentUser?.firebaseUid ?: return
            lifecycleScope.launch {
                val result = favoriteRepository.checkFavorite(post.postId, userId)
                if (result.isSuccess) {
                    isFavorited = result.getOrNull() ?: false
                    updateFavoriteButton()
                }
            }
        }
    }

    private fun updateLikeButton() {
        if (isLiked) {
            ivLike.setColorFilter(Color.parseColor("#B88B4A"), PorterDuff.Mode.SRC_ATOP)
        } else {
            ivLike.setColorFilter(Color.parseColor("#666666"), PorterDuff.Mode.SRC_ATOP)
        }
    }

    private fun updateFavoriteButton() {
        if (isFavorited) {
            ivFavorite.setColorFilter(Color.parseColor("#B88B4A"), PorterDuff.Mode.SRC_ATOP)
        } else {
            ivFavorite.setColorFilter(Color.parseColor("#666666"), PorterDuff.Mode.SRC_ATOP)
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

    // Image Pager Adapter
    // Image Pager Adapter with PhotoView for zoom
    inner class ImagePagerAdapter(private val images: List<String>) :
        androidx.recyclerview.widget.RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder>() {

        inner class ImageViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
            // Change from imageView to photoView
            val photoView: com.github.chrisbanes.photoview.PhotoView = itemView.findViewById(R.id.photo_view)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val view = layoutInflater.inflate(R.layout.item_post_image, parent, false)
            return ImageViewHolder(view)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            val imageUrl = images[position]
            val fullImageUrl = if (imageUrl.startsWith("http")) {
                imageUrl
            } else {
                "${com.example.pawsociety.api.ApiClient.FULL_BASE_URL}$imageUrl"
            }

            Glide.with(this@PostViewActivity)
                .load(fullImageUrl)
                .centerInside()
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .into(holder.photoView)  // Changed from imageView to photoView
        }

        override fun getItemCount() = images.size
    }
}