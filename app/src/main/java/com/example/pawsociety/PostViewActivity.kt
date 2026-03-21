package com.example.pawsociety

import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.example.pawsociety.api.ApiClient
import com.example.pawsociety.api.ApiPost
import com.example.pawsociety.data.repository.*
import com.example.pawsociety.util.SessionManager
import com.example.pawsociety.widget.LikeButton
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import android.net.Uri

class PostViewActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView

    private lateinit var sessionManager: SessionManager
    private lateinit var viewModel: HomeViewModel

    private var allPosts: MutableList<ApiPost> = mutableListOf()
    private var currentPosition: Int = 0
    private var currentUser: com.example.pawsociety.api.ApiUser? = null

    private val postRepository = PostRepository()
    private val favoriteRepository = FavoriteRepository()
    private val blockRepository = BlockRepository()
    private val reportRepository = ReportRepository()
    private val hidePostRepository = HidePostRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post_view)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        sessionManager = SessionManager(this)
        currentUser = sessionManager.getCurrentUser()

        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        viewModel.setSessionManager(sessionManager)

        allPosts = (intent.getSerializableExtra("all_posts") as? List<ApiPost>)?.toMutableList() ?: mutableListOf()
        currentPosition = intent.getIntExtra("position", 0)

        if (allPosts.isEmpty()) {
            Toast.makeText(this, "No posts found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupRecyclerView()

        recyclerView.post {
            recyclerView.scrollToPosition(currentPosition)
        }
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.main_post_pager)
    }

    private fun setupRecyclerView() {
        recyclerView.layoutManager = LinearLayoutManager(this)
        val adapter = PostAdapter(allPosts)
        recyclerView.adapter = adapter
    }

    override fun onBackPressed() {
        super.onBackPressed()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private fun showPostOptions(post: ApiPost) {
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

            val user = currentUser
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
                    Toast.makeText(this, "Edit post", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }

                optionDelete?.visibility = View.VISIBLE
                optionDelete?.setOnClickListener {
                    showDeleteConfirmation(post)
                    dialog.dismiss()
                }

                optionFollow?.visibility = View.GONE
                optionBlock?.visibility = View.GONE
                optionReport?.visibility = View.GONE
            } else {
                optionEdit?.visibility = View.GONE
                optionDelete?.visibility = View.GONE

                optionFollow?.visibility = View.VISIBLE
                tvFollowText?.text = "Follow"
                ivFollowIcon?.setImageResource(R.drawable.add)
                ivFollowIcon?.setColorFilter(Color.parseColor("#000000"), PorterDuff.Mode.SRC_ATOP)

                optionFollow?.setOnClickListener {
                    Toast.makeText(this, "Followed ${post.userName}", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }

                optionBlock?.visibility = View.VISIBLE
                optionBlock?.setOnClickListener {
                    showBlockConfirmation(post)
                    dialog.dismiss()
                }

                optionReport?.visibility = View.VISIBLE
            }

            optionAddFavorites?.setOnClickListener {
                toggleFavoriteFromMenu(post)
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
            e.printStackTrace()
            Toast.makeText(this, "Menu not available", Toast.LENGTH_SHORT).show()
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
            e.printStackTrace()
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
            e.printStackTrace()
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
        val currentUser = currentUser ?: return
        lifecycleScope.launch {
            val result = hidePostRepository.hidePost(currentUser.firebaseUid, post.postId)
            if (result.isSuccess) {
                Toast.makeText(this@PostViewActivity, "Post hidden", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showBlockConfirmation(post: ApiPost) {
        AlertDialog.Builder(this)
            .setTitle("Block User")
            .setMessage("Are you sure you want to block ${post.userName}?")
            .setPositiveButton("Block") { _, _ ->
                blockUser(post)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun blockUser(post: ApiPost) {
        val currentUser = currentUser ?: return
        lifecycleScope.launch {
            val result = blockRepository.blockUser(currentUser.firebaseUid, post.firebaseUid)
            if (result.isSuccess) {
                Toast.makeText(this@PostViewActivity, "Blocked ${post.userName}", Toast.LENGTH_SHORT).show()
            }
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
                reason = reason.lowercase(Locale.getDefault()).replace(" ", "_"),
                postId = post.postId,
                description = "Reported for: $reason"
            )
            if (result.isSuccess) {
                Toast.makeText(this@PostViewActivity, "Report submitted", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showDeleteConfirmation(post: ApiPost) {
        AlertDialog.Builder(this)
            .setTitle("Delete Post")
            .setMessage("Are you sure you want to delete this post?")
            .setPositiveButton("Delete") { _, _ ->
                deletePost(post)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deletePost(post: ApiPost) {
        val user = currentUser ?: return
        lifecycleScope.launch {
            val result = postRepository.deletePost(post.postId, user.firebaseUid)
            if (result.isSuccess) {
                Toast.makeText(this@PostViewActivity, "Post deleted", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun toggleFavoriteFromMenu(post: ApiPost) {
        val user = currentUser ?: run {
            Toast.makeText(this, "Please login to favorite", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val result = favoriteRepository.addToFavorites(user.firebaseUid, post.postId)
            if (result.isSuccess) {
                Toast.makeText(this@PostViewActivity, "Added to favorites", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ========== POST ADAPTER WITH NEW UI ELEMENTS ==========
    inner class PostAdapter(private val posts: List<ApiPost>) :
        RecyclerView.Adapter<PostAdapter.PostViewHolder>() {

        // Cache for image adapters to prevent recreation
        private val imageAdapters = mutableMapOf<String, ImagePagerAdapter>()

        init {
            setHasStableIds(true)
        }

        override fun getItemId(position: Int): Long {
            return posts[position].postId.hashCode().toLong()
        }

        override fun getItemViewType(position: Int): Int {
            return position // Prevent any recycling
        }

        // ViewHolder with all new UI elements
        inner class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            // Image views
            val imageViewPager: ViewPager2 = itemView.findViewById(R.id.image_view_pager)
            val imageCounter: TextView = itemView.findViewById(R.id.image_counter)
            val indicatorContainer: LinearLayout = itemView.findViewById(R.id.indicator_container)

            // Profile section
            val layoutProfile: RelativeLayout = itemView.findViewById(R.id.layout_profile)
            val tvProfileIcon: TextView = itemView.findViewById(R.id.tv_profile_icon)
            val ivProfileImage: ImageView = itemView.findViewById(R.id.iv_profile_image)
            val tvUsername: TextView = itemView.findViewById(R.id.tv_username)
            val tvTime: TextView = itemView.findViewById(R.id.tv_time)
            val btnMoreDetails: ImageView = itemView.findViewById(R.id.btn_more_details)

            // Pet info with badge
            val tvPetType: TextView = itemView.findViewById(R.id.tv_pet_type)
            val tvCategoryBadge: TextView = itemView.findViewById(R.id.tv_category_badge)
            val tvPetName: TextView = itemView.findViewById(R.id.tv_pet_name)

            // Gender, Age, Weight
            val tvGenderIcon: ImageView = itemView.findViewById(R.id.tv_gender_icon)
            val tvGender: TextView = itemView.findViewById(R.id.tv_gender)
            val tvAge: TextView = itemView.findViewById(R.id.tv_age)
            val tvWeight: TextView = itemView.findViewById(R.id.tv_weight)

            // Reward
            val rewardContainer: LinearLayout = itemView.findViewById(R.id.reward_container)
            val tvReward: TextView = itemView.findViewById(R.id.tv_reward)

            // Call button
            val btnCallContainer: LinearLayout = itemView.findViewById(R.id.btn_call_container)
            val ivCallIcon: ImageView = itemView.findViewById(R.id.iv_call_icon)
            val tvContact: TextView = itemView.findViewById(R.id.tv_contact)

            // Location
            val tvLocation: TextView = itemView.findViewById(R.id.tv_location)

            // Description
            val tvDescription: TextView = itemView.findViewById(R.id.tv_description)
            val btnMoreDescription: TextView = itemView.findViewById(R.id.btn_more_description)

            // Status badge
            val tvStatusBadge: TextView = itemView.findViewById(R.id.tv_status_badge)

            // Action buttons
            val btnLike: LinearLayout = itemView.findViewById(R.id.btn_like)
            val btnLikeLottie: LikeButton = itemView.findViewById(R.id.btn_like_lottie_view)
            val tvLikes: TextView = itemView.findViewById(R.id.tv_likes)
            val btnMessage: LinearLayout = itemView.findViewById(R.id.btn_message)
            val btnMessageIcon: ImageView = itemView.findViewById(R.id.btn_message_icon)
            val btnShare: LinearLayout = itemView.findViewById(R.id.btn_share)
            val btnShareIcon: ImageView = itemView.findViewById(R.id.btn_share_icon)
            val btnFavorite: LinearLayout = itemView.findViewById(R.id.btn_favorite)
            val ivFavorite: ImageView = itemView.findViewById(R.id.iv_favorite)

            // User role
            val tvUserRole: TextView = itemView.findViewById(R.id.tv_user_role)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_post_view_container, parent, false)
            return PostViewHolder(view)
        }

        override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
            val post = posts[position]
            bindPost(holder, post)
        }

        override fun getItemCount() = posts.size

        private fun bindPost(holder: PostViewHolder, post: ApiPost) {
            holder.tvUsername.text = post.userName
            holder.tvLocation.text = post.location ?: "No location"
            holder.tvTime.text = getTimeAgo(post.createdAt)
            holder.tvPetName.text = post.petName
            holder.tvPetType.text = post.petType ?: "Unknown breed"
            holder.tvDescription.text = post.description
            holder.tvLikes.text = post.likesCount.toString()

            // Set user role with pet name
            val petOwnerText = if (!post.petName.isNullOrEmpty()) {
                "${post.petName}'s Owner"
            } else {
                "Pet Owner"
            }
            holder.tvUserRole.text = petOwnerText

            // ===== SET CONTACT WITH CALL BUTTON =====
            val rawContact = post.contactInfo ?: ""
            val phoneDigits = rawContact.replace("[^0-9]".toRegex(), "")

            if (phoneDigits.isNotEmpty()) {
                holder.tvContact.text = phoneDigits
                holder.btnCallContainer.visibility = View.VISIBLE
                holder.btnCallContainer.setOnClickListener {
                    try {
                        val intent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:$phoneDigits")
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this@PostViewActivity, "Cannot make call", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                holder.btnCallContainer.visibility = View.GONE
            }

            // ===== SET GENDER =====
            when (post.gender?.lowercase(Locale.getDefault())) {
                "male" -> {
                    holder.tvGender.text = "Male"
                    holder.tvGender.setTextColor(Color.parseColor("#2196F3"))
                    holder.tvGenderIcon.visibility = View.GONE
                }
                "female" -> {
                    holder.tvGender.text = "Female"
                    holder.tvGender.setTextColor(Color.parseColor("#E91E63"))
                    holder.tvGenderIcon.visibility = View.GONE
                }
                else -> {
                    holder.tvGender.text = "Unknown"
                    holder.tvGender.setTextColor(Color.parseColor("#666666"))
                    holder.tvGenderIcon.visibility = View.GONE
                }
            }

            // ===== SET AGE AND WEIGHT =====
            if (post.age.isNullOrEmpty()) {
                holder.tvAge.visibility = View.GONE
            } else {
                holder.tvAge.visibility = View.VISIBLE
                holder.tvAge.text = post.age
            }

            if (post.weight.isNullOrEmpty()) {
                holder.tvWeight.visibility = View.GONE
            } else {
                holder.tvWeight.visibility = View.VISIBLE
                holder.tvWeight.text = post.weight
            }

            // ===== SET CATEGORY BADGE =====
            try {
                var category = when (post.category) {
                    "Dogs" -> "DOG"
                    "Cats" -> "CAT"
                    "Fish" -> "FISH"
                    "Birds" -> "BIRD"
                    else -> null
                }

                if (category == null) {
                    val petTypeLower = post.petType.lowercase()
                    category = when {
                        // DOG detection
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
                                petTypeLower.contains("unknown dog") ||
                                petTypeLower.contains("other dog") -> "DOG"

                        // CAT detection
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
                                petTypeLower.contains("unknown cat") ||
                                petTypeLower.contains("other cat") -> "CAT"

                        // FISH detection
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
                                petTypeLower.contains("unknown fish") ||
                                petTypeLower.contains("other fish") -> "FISH"

                        // BIRD detection
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
                                petTypeLower.contains("unknown bird") ||
                                petTypeLower.contains("other bird") -> "BIRD"

                        else -> null
                    }
                }

                if (category != null) {
                    holder.tvCategoryBadge.text = category
                    holder.tvCategoryBadge.visibility = View.VISIBLE

                    when (category) {
                        "DOG" -> {
                            holder.tvCategoryBadge.setBackgroundResource(R.drawable.category_badge_rounded)
                            holder.tvCategoryBadge.background.setTint(Color.parseColor("#B88B4A"))
                        }
                        "CAT" -> {
                            holder.tvCategoryBadge.setBackgroundResource(R.drawable.category_badge_rounded)
                            holder.tvCategoryBadge.background.setTint(Color.parseColor("#4CAF50"))
                        }
                        "FISH" -> {
                            holder.tvCategoryBadge.setBackgroundResource(R.drawable.category_badge_rounded)
                            holder.tvCategoryBadge.background.setTint(Color.parseColor("#2196F3"))
                        }
                        "BIRD" -> {
                            holder.tvCategoryBadge.setBackgroundResource(R.drawable.category_badge_rounded)
                            holder.tvCategoryBadge.background.setTint(Color.parseColor("#FF9800"))
                        }
                    }
                    holder.tvCategoryBadge.setTextColor(Color.WHITE)
                    holder.tvCategoryBadge.setPadding(12.dp, 4.dp, 12.dp, 4.dp)
                } else {
                    holder.tvCategoryBadge.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("PostViewActivity", "Error setting badge", e)
            }

            // ===== SET STATUS BADGE AND REWARD =====
            when (post.status) {
                "Lost" -> {
                    holder.tvStatusBadge.apply {
                        text = "LOST"
                        setBackgroundResource(R.drawable.status_badge_lost)
                        visibility = View.VISIBLE
                    }

                    if (!post.reward.isNullOrEmpty()) {
                        val formattedReward = formatReward(post.reward)
                        holder.tvReward.text = "₱$formattedReward"
                        holder.rewardContainer.visibility = View.VISIBLE
                    } else {
                        holder.rewardContainer.visibility = View.GONE
                    }
                }
                "Found" -> {
                    holder.tvStatusBadge.apply {
                        text = "FOUND"
                        setBackgroundResource(R.drawable.status_badge_found)
                        visibility = View.VISIBLE
                    }
                    holder.rewardContainer.visibility = View.GONE
                }
                "Adoption" -> {
                    holder.tvStatusBadge.apply {
                        text = "ADOPTION"
                        setBackgroundResource(R.drawable.status_badge_adoption)
                        visibility = View.VISIBLE
                    }
                    holder.rewardContainer.visibility = View.GONE
                }
            }

            // Set 3 dots menu click listener
            holder.btnMoreDetails.setOnClickListener {
                showPostOptions(post)
            }

            // Set profile icon
            val firstLetter = if (post.userName.isNotEmpty()) {
                post.userName.first().toString().uppercase(Locale.getDefault())
            } else {
                "?"
            }
            holder.tvProfileIcon.text = firstLetter

            // Load profile image if available
            if (!post.userImageUrl.isNullOrEmpty()) {
                val fullImageUrl = if (post.userImageUrl.startsWith("http")) {
                    post.userImageUrl
                } else {
                    "${ApiClient.FULL_BASE_URL}${post.userImageUrl}"
                }

                holder.tvProfileIcon.visibility = View.GONE
                holder.ivProfileImage.visibility = View.VISIBLE

                Glide.with(this@PostViewActivity)
                    .load(fullImageUrl)
                    .circleCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .error(android.R.drawable.ic_menu_report_image)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(holder.ivProfileImage)
            } else {
                holder.tvProfileIcon.visibility = View.VISIBLE
                holder.ivProfileImage.visibility = View.GONE
            }

            // Show/hide message button
            if (post.firebaseUid == currentUser?.firebaseUid) {
                holder.btnMessage.visibility = View.GONE
            } else {
                holder.btnMessage.visibility = View.VISIBLE
            }

            // Setup image carousel
            setupImageCarousel(holder, post)

            // Handle more description button
            holder.tvDescription.post {
                val lineCount = holder.tvDescription.lineCount
                if (lineCount > 3) {
                    holder.tvDescription.maxLines = 3
                    holder.tvDescription.ellipsize = TextUtils.TruncateAt.END
                    holder.btnMoreDescription.visibility = View.VISIBLE
                    holder.btnMoreDescription.tag = false

                    holder.btnMoreDescription.setOnClickListener {
                        val isCollapsed = holder.btnMoreDescription.tag as Boolean
                        if (isCollapsed) {
                            holder.tvDescription.maxLines = Integer.MAX_VALUE
                            holder.tvDescription.ellipsize = null
                            holder.btnMoreDescription.text = "less"
                            holder.btnMoreDescription.tag = false
                        } else {
                            holder.tvDescription.maxLines = 3
                            holder.tvDescription.ellipsize = TextUtils.TruncateAt.END
                            holder.btnMoreDescription.text = "more"
                            holder.btnMoreDescription.tag = true
                        }
                    }
                } else {
                    holder.btnMoreDescription.visibility = View.GONE
                }
            }

            // Check like and favorite status
            checkLikeStatus(holder, post)
            checkFavoriteStatus(holder, post)

            // Set click listeners
            holder.layoutProfile.setOnClickListener {
                openUserProfile(post)
            }

            holder.btnLike.setOnClickListener {
                toggleLike(holder, post)
            }

            holder.btnShare.setOnClickListener {
                sharePost(post)
            }

            holder.btnMessage.setOnClickListener {
                startChat(post)
            }

            holder.btnFavorite.setOnClickListener {
                toggleFavorite(holder, post)
            }
        }

        private fun setupImageCarousel(holder: PostViewHolder, post: ApiPost) {
            val imageUrls = post.imageUrls ?: emptyList()

            if (imageUrls.isEmpty()) {
                holder.imageViewPager.visibility = View.GONE
                holder.indicatorContainer.visibility = View.GONE
                holder.imageCounter.visibility = View.GONE
                return
            }

            // REUSE the same adapter for the same post
            var adapter = imageAdapters[post.postId]
            if (adapter == null) {
                adapter = ImagePagerAdapter(imageUrls)
                imageAdapters[post.postId] = adapter
            }

            holder.imageViewPager.adapter = adapter
            holder.imageViewPager.offscreenPageLimit = imageUrls.size

            if (imageUrls.size > 1) {
                holder.imageCounter.visibility = View.VISIBLE
                holder.imageCounter.text = "1/${imageUrls.size}"
                holder.indicatorContainer.visibility = View.VISIBLE

                setupImageIndicators(holder.indicatorContainer, imageUrls.size)

                // Remove existing callbacks to prevent duplicates
                holder.imageViewPager.unregisterOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {})

                holder.imageViewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageSelected(position: Int) {
                        super.onPageSelected(position)
                        holder.imageCounter.text = "${position + 1}/${imageUrls.size}"
                        updateIndicatorSelection(holder.indicatorContainer, position)
                    }
                })
            } else {
                holder.imageCounter.visibility = View.GONE
                holder.indicatorContainer.visibility = View.GONE
            }
        }

        private fun checkLikeStatus(holder: PostViewHolder, post: ApiPost) {
            val userId = currentUser?.firebaseUid ?: return
            lifecycleScope.launch {
                val result = postRepository.checkLikeStatus(post.postId, userId)
                if (result.isSuccess) {
                    val response = result.getOrNull()!!
                    val isLiked = response.isLiked ?: false
                    holder.btnLikeLottie.setLiked(isLiked, animate = false)
                    holder.tvLikes.text = response.likesCount.toString()
                }
            }
        }

        private fun checkFavoriteStatus(holder: PostViewHolder, post: ApiPost) {
            val userId = currentUser?.firebaseUid ?: return
            lifecycleScope.launch {
                val result = favoriteRepository.checkFavorite(post.postId, userId)
                if (result.isSuccess) {
                    val isFavorited = result.getOrNull() ?: false
                    if (isFavorited) {
                        holder.ivFavorite.setColorFilter(Color.parseColor("#B88B4A"), PorterDuff.Mode.SRC_ATOP)
                    } else {
                        holder.ivFavorite.setColorFilter(Color.parseColor("#666666"), PorterDuff.Mode.SRC_ATOP)
                    }
                }
            }
        }

        private fun toggleLike(holder: PostViewHolder, post: ApiPost) {
            val user = currentUser ?: run {
                Toast.makeText(this@PostViewActivity, "Please login to like", Toast.LENGTH_SHORT).show()
                return
            }

            if (!holder.btnLike.isEnabled) return
            holder.btnLike.isEnabled = false

            val wasAlreadyLiked = holder.btnLikeLottie.isLiked
            holder.btnLikeLottie.setLiked(!wasAlreadyLiked, animate = true)

            val optimisticCount = if (!wasAlreadyLiked) {
                post.likesCount + 1
            } else {
                max(0, post.likesCount - 1)
            }
            holder.tvLikes.text = optimisticCount.toString()

            lifecycleScope.launch {
                val result = postRepository.likePost(post.postId, user.firebaseUid)

                if (result.isSuccess) {
                    val response = result.getOrNull()!!
                    val serverLiked = response.liked ?: response.isLiked ?: !wasAlreadyLiked
                    val serverCount = response.likesCount

                    if (holder.btnLikeLottie.isLiked != serverLiked) {
                        holder.btnLikeLottie.setLiked(serverLiked, animate = false)
                    }
                    holder.tvLikes.text = serverCount.toString()

                    val index = allPosts.indexOfFirst { it.postId == post.postId }
                    if (index >= 0) {
                        allPosts[index] = post.copy(likesCount = serverCount)
                    }
                } else {
                    holder.btnLikeLottie.setLiked(wasAlreadyLiked, animate = false)
                    holder.tvLikes.text = post.likesCount.toString()
                    Toast.makeText(this@PostViewActivity, "Failed to update like", Toast.LENGTH_SHORT).show()
                }

                Handler(Looper.getMainLooper()).postDelayed({
                    holder.btnLike.isEnabled = true
                }, 1000)
            }
        }

        private fun toggleFavorite(holder: PostViewHolder, post: ApiPost) {
            val user = currentUser ?: run {
                Toast.makeText(this@PostViewActivity, "Please login to favorite", Toast.LENGTH_SHORT).show()
                return
            }

            lifecycleScope.launch {
                val isFavorited = holder.ivFavorite.colorFilter != null
                val result = if (isFavorited) {
                    favoriteRepository.removeFromFavorites(user.firebaseUid, post.postId)
                } else {
                    favoriteRepository.addToFavorites(user.firebaseUid, post.postId)
                }

                if (result.isSuccess) {
                    if (isFavorited) {
                        holder.ivFavorite.setColorFilter(Color.parseColor("#666666"), PorterDuff.Mode.SRC_ATOP)
                    } else {
                        holder.ivFavorite.setColorFilter(Color.parseColor("#B88B4A"), PorterDuff.Mode.SRC_ATOP)
                    }
                    Toast.makeText(this@PostViewActivity,
                        if (!isFavorited) "Added to favorites" else "Removed from favorites",
                        Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Helper functions
    private fun setupImageIndicators(container: LinearLayout, count: Int) {
        container.removeAllViews()
        for (i in 0 until count) {
            val dot = View(this)
            val params = LinearLayout.LayoutParams(8.dp, 8.dp)
            params.setMargins(4.dp, 0, 4.dp, 0)
            dot.layoutParams = params

            if (i == 0) {
                dot.setBackgroundResource(R.drawable.dot_indicator_selected)
            } else {
                dot.setBackgroundResource(R.drawable.dot_indicator)
            }

            container.addView(dot)
        }
    }

    private fun updateIndicatorSelection(container: LinearLayout, position: Int) {
        for (i in 0 until container.childCount) {
            val dot = container.getChildAt(i)
            if (i == position) {
                dot.setBackgroundResource(R.drawable.dot_indicator_selected)
            } else {
                dot.setBackgroundResource(R.drawable.dot_indicator)
            }
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    inner class ImagePagerAdapter(private val images: List<String>) :
        RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder>() {

        // PERMANENT CACHE - images load once and never disappear
        private val imageCache = mutableMapOf<Int, Drawable>()
        private val requestManager = Glide.with(this@PostViewActivity)

        inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val photoView: com.github.chrisbanes.photoview.PhotoView = itemView.findViewById(R.id.photo_view)

            fun bind(position: Int) {
                // Check cache first - INSTANT LOAD
                if (imageCache.containsKey(position)) {
                    photoView.setImageDrawable(imageCache[position])
                    return
                }

                val imageUrl = images[position]
                val fullImageUrl = if (imageUrl.startsWith("http")) {
                    imageUrl
                } else {
                    "${ApiClient.FULL_BASE_URL}$imageUrl"
                }

                // Load and cache permanently
                requestManager
                    .load(fullImageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(object : CustomTarget<Drawable>() {
                        override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                            imageCache[position] = resource
                            photoView.setImageDrawable(resource)
                        }

                        override fun onLoadCleared(placeholder: Drawable?) {
                            // Do nothing
                        }
                    })
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val view = layoutInflater.inflate(R.layout.item_post_image, parent, false)
            return ImageViewHolder(view)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            holder.bind(position)
        }

        override fun getItemCount() = images.size

        // Prevent recycling
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getItemViewType(position: Int): Int = position
    }

    private fun openUserProfile(post: ApiPost) {
        if (post.firebaseUid == currentUser?.firebaseUid) {
            startActivity(Intent(this, ProfileActivity::class.java))
        } else {
            val intent = Intent(this, UserProfileActivity::class.java)
            intent.putExtra("userId", post.firebaseUid)
            intent.putExtra("userName", post.userName)
            startActivity(intent)
        }
    }

    private fun sharePost(post: ApiPost) {
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

    private fun startChat(post: ApiPost) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra("receiverUid", post.firebaseUid)
        intent.putExtra("receiverUsername", post.userName)
        startActivity(intent)
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
}